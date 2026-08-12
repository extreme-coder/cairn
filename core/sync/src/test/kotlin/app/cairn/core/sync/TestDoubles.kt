package app.cairn.core.sync

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.cairn.core.database.CairnDatabase
import app.cairn.core.network.FormDto
import app.cairn.core.network.FormTranslationDto
import app.cairn.core.network.FormVersionDto
import app.cairn.core.network.ParticipantDto
import app.cairn.core.network.RemoteDataSource
import app.cairn.core.network.StudyDto
import app.cairn.core.network.StudyMemberDto
import app.cairn.core.network.SubmissionDto
import kotlinx.coroutines.Dispatchers
import java.io.IOException

/**
 * The query context is [Dispatchers.IO], not `Unconfined` as in `:core:database`.
 *
 * `withTransaction` refuses to run on the main thread, and Robolectric runs
 * tests *on* the main looper — so an unconfined context puts every transaction
 * exactly where Room will not allow one. Relaxing the check with
 * `allowMainThreadQueries()` would hide the difference; a real background
 * dispatcher reproduces the threading the worker actually uses.
 */
internal fun testDatabase(): CairnDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        CairnDatabase::class.java,
    )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

internal class InMemoryCursors : SyncCursors {

    private val cursors = mutableMapOf<String, Pair<String, String>>()

    /** Every read, in order, so a test can prove a cursor was consulted and not just written. */
    val reads: MutableList<String> = mutableListOf()

    override suspend fun read(key: String, scope: String): String? {
        reads += key
        val held = cursors[key] ?: return null
        return if (held.first == scope) held.second else null
    }

    override suspend fun write(key: String, scope: String, cursor: String) {
        cursors[key] = scope to cursor
    }

    override suspend fun clear() {
        cursors.clear()
    }

    fun peek(key: String): String? = cursors[key]?.second
}

/**
 * A server, not a script.
 *
 * It holds rows and answers the same question PostgREST does — `updated_at >
 * since`, ascending, capped at `limit` — and upserts on
 * `(collected_by, client_id)`. Scripting canned pages instead would make the
 * paging tests assert that the fake returns what the fake was told to return.
 * This way a page boundary is a real page boundary, and replaying a push is
 * idempotent because the server makes it so, not because the test says it is.
 */
internal class FakeRemote : RemoteDataSource {

    var currentUser: String? = Ids.ADAKU

    val studies: MutableList<StudyDto> = mutableListOf()
    val members: MutableList<StudyMemberDto> = mutableListOf()
    val forms: MutableList<FormDto> = mutableListOf()
    val versions: MutableList<FormVersionDto> = mutableListOf()
    val participants: MutableList<ParticipantDto> = mutableListOf()
    val translations: MutableList<FormTranslationDto> = mutableListOf()
    val submissions: MutableList<SubmissionDto> = mutableListOf()

    /** Set to fail every request, the way a dead network does. */
    var offline: Boolean = false

    /** Writes blocked, reads working — a proxy, a captive portal, a revoked insert grant. */
    var pushOffline: Boolean = false

    /** Rows the server rejects outright — a locked submission, an RLS denial. */
    var rejects: (SubmissionDto) -> Boolean = { false }

    val pushBatches: MutableList<List<SubmissionDto>> = mutableListOf()
    val fetchLog: MutableList<Pair<String, String?>> = mutableListOf()

    /** Every call in the order it arrived, so ordering between push and pull is assertable. */
    val events: MutableList<String> = mutableListOf()

    private var assigned = 0
    private var stamped = 0

    override suspend fun signIn(email: String, password: String) {
        currentUser = Ids.ADAKU
    }

    override suspend fun signOut() {
        currentUser = null
    }

    override suspend fun currentUserId(): String? = currentUser

    override suspend fun studies(since: String?, limit: Int): List<StudyDto> =
        page("studies", studies, since, limit) { it.updatedAt }

    override suspend fun members(studyId: String, since: String?, limit: Int): List<StudyMemberDto> =
        page("study_members", members.filter { it.studyId == studyId }, since, limit) { it.updatedAt }

    override suspend fun forms(studyId: String, since: String?, limit: Int): List<FormDto> =
        page("forms", forms.filter { it.studyId == studyId }, since, limit) { it.updatedAt }

    override suspend fun formVersions(
        formIds: List<String>,
        since: String?,
        limit: Int,
    ): List<FormVersionDto> {
        if (formIds.isEmpty()) return emptyList()
        return page("form_versions", versions.filter { it.formId in formIds }, since, limit) { it.updatedAt }
    }

    override suspend fun participants(studyId: String, since: String?, limit: Int): List<ParticipantDto> =
        page("participants", participants.filter { it.studyId == studyId }, since, limit) { it.updatedAt }

    override suspend fun translations(
        formVersionIds: List<String>,
        since: String?,
        limit: Int,
    ): List<FormTranslationDto> {
        if (formVersionIds.isEmpty()) return emptyList()
        return page(
            "form_translations",
            translations.filter { it.formVersionId in formVersionIds },
            since,
            limit,
        ) { it.updatedAt }
    }

    override suspend fun submissions(studyId: String, since: String?, limit: Int): List<SubmissionDto> =
        page("submissions", submissions.filter { it.studyId == studyId }, since, limit) {
            it.updatedAt ?: error("a stored submission always has updated_at")
        }

    override suspend fun push(submissions: List<SubmissionDto>): List<SubmissionDto> {
        pushBatches += submissions
        events += "push"
        if (offline || pushOffline) throw IOException("no route to host")
        if (submissions.any(rejects)) throw IllegalStateException("the server rejected a row")

        return submissions.map { incoming ->
            val existing = this.submissions.firstOrNull {
                it.collectedBy == incoming.collectedBy && it.clientId == incoming.clientId
            }
            val stored = incoming.copy(
                id = existing?.id ?: "server-${assigned++}",
                updatedAt = serverStamp(),
            )
            this.submissions.remove(existing)
            this.submissions += stored
            stored
        }
    }

    /** The server's clock, always later than any fixture and unrelated to the device's. */
    fun serverStamp(): String = ts(minute = 30, micros = stamped++)

    private fun <T> page(
        table: String,
        rows: List<T>,
        since: String?,
        limit: Int,
        updatedAt: (T) -> String,
    ): List<T> {
        if (offline) throw IOException("no route to host")
        fetchLog += table to since
        events += "fetch:$table"
        return rows.filter { since == null || updatedAt(it) > since }
            .sortedBy(updatedAt)
            .take(limit)
    }
}
