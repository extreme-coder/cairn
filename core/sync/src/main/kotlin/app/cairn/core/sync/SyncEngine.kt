package app.cairn.core.sync

import app.cairn.core.database.CairnDatabase
import app.cairn.core.database.inTransaction
import app.cairn.core.database.dao.SubmissionDao
import app.cairn.core.database.entity.SubmissionEntity
import app.cairn.core.model.SyncState
import app.cairn.core.network.FormDto
import app.cairn.core.network.FormTranslationDto
import app.cairn.core.network.FormVersionDto
import app.cairn.core.network.ParticipantDto
import app.cairn.core.network.RemoteDataSource
import app.cairn.core.network.SubmissionDto

/**
 * What one run moved.
 *
 * [pulled] is keyed by cursor name, so a run that fetched nothing is visibly
 * different from a run that never asked.
 */
public data class SyncOutcome(
    public val pushed: Int,
    public val failed: Int,
    public val pulled: Map<String, Int>,
) {
    public val pulledTotal: Int get() = pulled.values.sum()
}

/**
 * One sync run, top to bottom. No Android in here — a `SyncEngine` holds a Room
 * database, a [RemoteDataSource] and a [SyncCursors], all three of which can be
 * fakes, so the algorithm is testable without a device or a server.
 * [SyncWorker] is the part that knows about WorkManager, and it is thin on
 * purpose.
 *
 * **Push runs before pull, always.** A pull writes server rows over local ones,
 * and a row that has not been pushed yet holds the only copy of an observation
 * that exists anywhere. Pushing first shrinks that window to nothing for every
 * row that sends successfully; [SubmissionDao.pendingKeys] covers the rows that
 * did not.
 */
public class SyncEngine(
    private val database: CairnDatabase,
    private val remote: RemoteDataSource,
    private val cursors: SyncCursors,
    private val pageSize: Int = RemoteDataSource.PAGE,
) {

    /**
     * A push that could not send does not cancel the pull.
     *
     * They fail for the same reason most of the time, but not always: a captive
     * portal, a proxy refusing writes, or a server rejecting this device's token
     * for `insert` can leave reads working. A collector standing in that gap
     * should still receive the form a coordinator published this morning. The
     * failure is re-thrown afterwards so the worker still backs off and retries.
     */
    public suspend fun sync(): SyncOutcome {
        if (remote.currentUserId() == null) throw SyncException.NotSignedIn()

        var report = PushReport(uploaded = 0, failed = 0)
        var unsent: SyncException.PushUnavailable? = null
        try {
            report = push()
        } catch (unavailable: SyncException.PushUnavailable) {
            unsent = unavailable
        }

        val pulled = pull()
        unsent?.let { throw it }

        return SyncOutcome(pushed = report.uploaded, failed = report.failed, pulled = pulled)
    }

    private data class PushReport(val uploaded: Int, val failed: Int)

    /**
     * Drains the queue oldest first.
     *
     * Sent as one batch, because that is one request and one upsert. If the batch
     * fails there is no way to tell from the failure whether the network dropped
     * or one row was rejected, so it retries them one at a time: a row that fails
     * alone while others succeed is the row's fault and is marked `FAILED`; if
     * every row fails alone it is the network's fault and they all stay `QUEUED`
     * for the next backoff window.
     *
     * The one case this cannot separate is a single queued row that fails — with
     * nothing to compare it against, it is treated as transient and retried
     * forever. That is deliberate. A collector who walked a transect to record it
     * would rather see it stuck in the queue than watch it be discarded as
     * unsendable.
     */
    private suspend fun push(): PushReport {
        val queued = database.submissions().awaiting(SyncState.QUEUED, pageSize)
        if (queued.isEmpty()) return PushReport(uploaded = 0, failed = 0)

        return try {
            val echoed = remote.push(queued.map { it.toDto() })
            record(echoed)
            PushReport(uploaded = echoed.size, failed = 0)
        } catch (batchFailure: Exception) {
            isolate(queued, batchFailure)
        }
    }

    private suspend fun isolate(queued: List<SubmissionEntity>, batchFailure: Exception): PushReport {
        val rejected = mutableListOf<SubmissionEntity>()
        var uploaded = 0

        for (row in queued) {
            try {
                val echoed = remote.push(listOf(row.toDto()))
                record(echoed)
                uploaded += echoed.size
            } catch (_: Exception) {
                rejected += row
            }
        }

        if (uploaded == 0) throw SyncException.PushUnavailable(queued.size, batchFailure)

        rejected.forEach { database.submissions().markFailed(it.collectedBy, it.clientId) }
        return PushReport(uploaded = uploaded, failed = rejected.size)
    }

    /**
     * Writes back what the server said it stored.
     *
     * The `updated_at` recorded here is the server's, never the device's. It is
     * what last-write-wins compares, so a phone with a skewed clock must not be
     * able to put its own value in the column it would later be judged by.
     */
    private suspend fun record(echoed: List<SubmissionDto>) {
        echoed.forEach { dto ->
            val serverId = dto.id
                ?: throw SyncException.Malformed("push echoed ${dto.clientId} back with no id")
            val updatedAt = dto.updatedAt
                ?: throw SyncException.Malformed("push echoed ${dto.clientId} back with no updated_at")
            database.submissions().markUploaded(
                collectedBy = dto.collectedBy,
                clientId = dto.clientId,
                serverId = serverId,
                updatedAt = updatedAt.toStoredInstant(),
            )
        }
    }

    /**
     * Reference data first, in dependency order, then observations.
     *
     * Foreign keys are deferred, so ordering does not matter *within* a
     * transaction — but each table's delta is its own transaction, so it matters
     * between them. Forms cannot land before their study.
     */
    private suspend fun pull(): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()

        counts[SyncCursors.STUDIES] = drain(
            table = "studies",
            key = SyncCursors.STUDIES,
            scope = ALL,
            updatedAt = { it.updatedAt },
            apply = { rows -> database.studies().upsert(rows.map { it.toEntity() }) },
            fetch = { since, limit -> remote.studies(since, limit) },
        )

        for (studyId in database.studies().ids()) {
            counts[SyncCursors.members(studyId)] = drain(
                table = "study_members",
                key = SyncCursors.members(studyId),
                scope = studyId,
                updatedAt = { it.updatedAt },
                apply = { rows -> database.members().upsert(rows.map { it.toEntity() }) },
                fetch = { since, limit -> remote.members(studyId, since, limit) },
            )

            counts[SyncCursors.forms(studyId)] = drain(
                table = "forms",
                key = SyncCursors.forms(studyId),
                scope = studyId,
                updatedAt = { it.updatedAt },
                apply = ::applyForms,
                fetch = { since, limit -> remote.forms(studyId, since, limit) },
            )

            val formIds = database.forms().formIds(studyId)
            counts[SyncCursors.formVersions(studyId)] = drain(
                table = "form_versions",
                key = SyncCursors.formVersions(studyId),
                scope = scopeOf(formIds),
                updatedAt = { it.updatedAt },
                apply = ::applyVersions,
                fetch = { since, limit -> remote.formVersions(formIds, since, limit) },
            )

            counts[SyncCursors.participants(studyId)] = drain(
                table = "participants",
                key = SyncCursors.participants(studyId),
                scope = studyId,
                updatedAt = { it.updatedAt },
                apply = ::applyParticipants,
                fetch = { since, limit -> remote.participants(studyId, since, limit) },
            )

            val versionIds = database.forms().versionIds(formIds)
            counts[SyncCursors.translations(studyId)] = drain(
                table = "form_translations",
                key = SyncCursors.translations(studyId),
                scope = scopeOf(versionIds),
                updatedAt = { it.updatedAt },
                apply = ::applyTranslations,
                fetch = { since, limit -> remote.translations(versionIds, since, limit) },
            )

            counts[SyncCursors.submissions(studyId)] = drain(
                table = "submissions",
                key = SyncCursors.submissions(studyId),
                scope = studyId,
                updatedAt = { it.updatedAt ?: throw SyncException.Malformed("pulled submission with no updated_at") },
                apply = ::applySubmissions,
                fetch = { since, limit -> remote.submissions(studyId, since, limit) },
            )
        }

        return counts
    }

    /**
     * Fetch, cut, apply, advance — and in that order, so the cursor only ever
     * moves past rows that are on disk. A crash between two pages costs one page
     * of re-fetching and loses nothing.
     */
    private suspend fun <D> drain(
        table: String,
        key: String,
        scope: String,
        updatedAt: (D) -> String,
        apply: suspend (List<D>) -> Unit,
        fetch: suspend (since: String?, limit: Int) -> List<D>,
    ): Int {
        var since = cursors.read(key, scope)
        var applied = 0

        while (true) {
            val cut = cutPage(fetch(since, pageSize), pageSize, table, updatedAt)

            if (cut.keep.isNotEmpty()) {
                apply(cut.keep)
                applied += cut.keep.size
            }

            val next = cut.cursor
            if (next != null) {
                if (next == since) throw SyncException.CursorStalled(table, next, pageSize)
                cursors.write(key, scope, next)
                since = next
            }

            if (!cut.more) return applied
        }
    }

    private suspend fun applyForms(rows: List<FormDto>) =
        database.inTransaction {
            database.forms().upsertForms(rows.map { it.toEntity() })
            assertLanded("forms", rows.map { it.id }, database.forms()::existingFormIds)
        }

    private suspend fun applyVersions(rows: List<FormVersionDto>) =
        database.inTransaction {
            database.forms().upsertVersions(rows.map { it.toEntity() })
            assertLanded("form_versions", rows.map { it.id }, database.forms()::existingVersionIds)
        }

    private suspend fun applyParticipants(rows: List<ParticipantDto>) =
        database.inTransaction {
            database.participants().upsert(rows.map { it.toEntity() })
            assertLanded("participants", rows.map { it.id }, database.participants()::existingIds)
        }

    private suspend fun applyTranslations(rows: List<FormTranslationDto>) =
        database.inTransaction {
            database.translations().upsert(rows.map { it.toEntity() })
            assertLanded("form_translations", rows.map { it.id }, database.translations()::existingIds)
        }

    /**
     * The one pull that has to look before it writes.
     *
     * Every other table is server-authoritative and read-only on the device, so
     * overwriting is always right. Submissions are the exception: a row still in
     * the queue holds a local change the server has not seen, and the incoming
     * row is the server's older idea of it. Push has already run, so anything
     * still pending here failed to send — writing over it would turn a retryable
     * network error into a lost observation.
     */
    private suspend fun applySubmissions(rows: List<SubmissionDto>) =
        database.inTransaction {
            val pending = database.submissions().pendingKeys()
                .mapTo(mutableSetOf()) { it.collectedBy to it.clientId }
            val safe = rows.filterNot { (it.collectedBy to it.clientId) in pending }
            database.submissions().upsert(safe.map { it.toEntity() })
        }

    /**
     * Room's `@Upsert` drops a row whose primary key is new but whose unique
     * index is taken, and says nothing. Reading the ids back turns that into a
     * failed transaction and a cursor that stays put, so the rows are offered
     * again, instead of a hole nobody notices.
     */
    private suspend fun assertLanded(
        table: String,
        ids: List<String>,
        present: suspend (List<String>) -> List<String>,
    ) {
        val landed = present(ids).toSet()
        val missing = ids.filterNot(landed::contains)
        if (missing.isNotEmpty()) throw SyncException.DeltaIncomplete(table, missing)
    }

    private companion object {
        /** `studies` is not scoped by anything — the server returns what this user can see. */
        const val ALL = "all"
    }
}
