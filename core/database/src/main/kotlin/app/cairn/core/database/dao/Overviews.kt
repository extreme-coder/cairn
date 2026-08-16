package app.cairn.core.database.dao

import androidx.room.ColumnInfo
import app.cairn.core.model.StudyRole
import app.cairn.core.model.SyncState
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

/**
 * What list screens read.
 *
 * Each of these is a projection, not an entity: a row assembled by the query
 * from several tables so the screen above it does no joining of its own. A
 * screen that fetched studies, then forms per study, then a count per form would
 * issue a query per row and re-run all of them whenever any one table changed.
 * Room recomputes one [kotlinx.coroutines.flow.Flow] instead, and only when a
 * table the query actually names is written.
 */

/**
 * One row of the Studies screen.
 *
 * [role] is nullable because membership arrives in its own pull. A study whose
 * `study_members` row has not landed yet is still a study this device holds, and
 * dropping it from the list until the second pull completes would make studies
 * appear one at a time for no reason the collector can see.
 */
public data class StudySummary(
    @ColumnInfo(name = "id") public val id: String,
    @ColumnInfo(name = "name") public val name: String,
    @ColumnInfo(name = "role") public val role: StudyRole?,
    @ColumnInfo(name = "form_count") public val formCount: Int,
    @ColumnInfo(name = "submission_count") public val submissionCount: Int,
    @ColumnInfo(name = "pending_count") public val pendingCount: Int,
)

/**
 * One row of the Collect screen: a form and the version a new submission would
 * be collected under.
 *
 * [version] and [schema] are null when the form has no published version. That
 * is a real state on a device syncing for the first time — forms and their
 * versions arrive in separate pulls — and the row says so rather than being
 * hidden, because a form that exists and cannot yet be filled in is different
 * from a form that does not exist.
 *
 * [schema] is raw JSON here for the same reason it is raw in the table: a field
 * count is worth nothing if computing it can fail the whole list.
 */
public data class FormSummary(
    @ColumnInfo(name = "id") public val id: String,
    @ColumnInfo(name = "study_id") public val studyId: String,
    @ColumnInfo(name = "code") public val code: String,
    @ColumnInfo(name = "version_id") public val versionId: String?,
    @ColumnInfo(name = "version") public val version: Int?,
    @ColumnInfo(name = "schema") public val schema: JsonObject?,
)

/**
 * One row of the Queue screen.
 *
 * Carries the study name and the form code because the Queue is not scoped to a
 * study: a collector working two studies in one morning has one queue, and a
 * row that only said `KL-0148` would not say which survey it belongs to.
 *
 * [participantCode] is null for a submission with no participant. The screen
 * falls back to the client id rather than showing an empty line — every row has
 * to be identifiable, since the whole point of this screen is to account for
 * observations that only exist here.
 */
public data class QueuedSubmission(
    @ColumnInfo(name = "client_id") public val clientId: String,
    @ColumnInfo(name = "study_id") public val studyId: String,
    @ColumnInfo(name = "study_name") public val studyName: String,
    @ColumnInfo(name = "form_code") public val formCode: String,
    @ColumnInfo(name = "version") public val version: Int,
    @ColumnInfo(name = "participant_code") public val participantCode: String?,
    @ColumnInfo(name = "collected_at") public val collectedAt: Instant,
    @ColumnInfo(name = "sync_state") public val syncState: SyncState,
)

/**
 * The three numbers at the top of the Queue screen.
 *
 * Counted by the database rather than by taking `.size` of a list, so showing
 * "148 uploaded" does not mean loading 148 rows nobody asked to see.
 */
public data class QueueCounts(
    @ColumnInfo(name = "queued") public val queued: Int,
    @ColumnInfo(name = "failed") public val failed: Int,
    @ColumnInfo(name = "uploaded") public val uploaded: Int,
) {
    /** What the sign-out guard, the app bar badge and the banner all mean by "waiting". */
    public val pending: Int get() = queued + failed

    public val total: Int get() = queued + failed + uploaded
}

/**
 * One row of the coordinator's Submissions screen.
 *
 * Distinct from [QueuedSubmission] because the two screens ask different
 * questions of the same table. A collector's queue asks "has this left the
 * phone", so it carries a [SyncState] and hides nothing else. A review list asks
 * "what has been done to this", so it carries [lockedAt] and [deletedAt] — and
 * the server [id], because that is the only handle a lock or a void has.
 *
 * [id] is null for a row this device collected and has not yet pushed. That is
 * what makes "not uploaded yet" a state the detail screen can state plainly
 * rather than a button that fails when pressed.
 */
public data class ReviewSubmission(
    @ColumnInfo(name = "collected_by") public val collectedBy: String,
    @ColumnInfo(name = "client_id") public val clientId: String,
    @ColumnInfo(name = "id") public val id: String?,
    @ColumnInfo(name = "form_code") public val formCode: String,
    @ColumnInfo(name = "version") public val version: Int,
    @ColumnInfo(name = "participant_code") public val participantCode: String?,
    @ColumnInfo(name = "collected_at") public val collectedAt: Instant,
    @ColumnInfo(name = "locked_at") public val lockedAt: Instant?,
    @ColumnInfo(name = "deleted_at") public val deletedAt: Instant?,
    @ColumnInfo(name = "sync_state") public val syncState: SyncState,
)

/**
 * One submission, with the schema it was collected under.
 *
 * [schema] comes from the version the row **pins**, not the form's current one.
 * That is the versioning ADR arriving at the screen it was written for: a
 * payload collected on v2 is read back against v2's labels, units and options,
 * whatever v4 says today. Reading the current version here would relabel old
 * observations silently, which is the failure mode versioning exists to prevent.
 *
 * Raw [JsonObject] for both [schema] and [data] for the same reason the table
 * stores them that way: a field type this build has never heard of must fail one
 * screen, not the query behind every screen.
 */
public data class SubmissionDetail(
    @ColumnInfo(name = "collected_by") public val collectedBy: String,
    @ColumnInfo(name = "client_id") public val clientId: String,
    @ColumnInfo(name = "id") public val id: String?,
    @ColumnInfo(name = "study_id") public val studyId: String,
    @ColumnInfo(name = "study_name") public val studyName: String,
    @ColumnInfo(name = "form_code") public val formCode: String,
    @ColumnInfo(name = "version") public val version: Int,
    @ColumnInfo(name = "schema") public val schema: JsonObject,
    @ColumnInfo(name = "participant_code") public val participantCode: String?,
    @ColumnInfo(name = "collected_at") public val collectedAt: Instant,
    @ColumnInfo(name = "updated_at") public val updatedAt: Instant,
    @ColumnInfo(name = "locked_at") public val lockedAt: Instant?,
    @ColumnInfo(name = "deleted_at") public val deletedAt: Instant?,
    @ColumnInfo(name = "sync_state") public val syncState: SyncState,
    @ColumnInfo(name = "data") public val data: JsonObject,
)

/**
 * One bar of the progress chart: a calendar day and what was collected on it.
 *
 * The device-side twin of the server's `v_study_progress` view, which a
 * coordinator's pull cannot deliver — a view is not a table and there is no
 * cursor over it. It does not need to be delivered: a coordinator's pull already
 * brings every submission in the study down, so the same aggregate is available
 * locally, and computing it here keeps Room the single source of truth rather
 * than putting one screen on the network.
 *
 * [day] is `YYYY-MM-DD` because that is what SQLite's `date()` returns and what
 * sorts correctly as text.
 */
public data class ProgressDay(
    @ColumnInfo(name = "day") public val day: String,
    @ColumnInfo(name = "n_submissions") public val submissions: Int,
    @ColumnInfo(name = "n_participants") public val participants: Int,
)

/**
 * The numbers above the progress chart.
 *
 * The three are deliberately disjoint: a voided row is not counted as collected,
 * and a locked row is only counted as locked while it is not voided. Overlapping
 * counts that sum to more than the table holds are how a summary stops being
 * believed.
 */
public data class ReviewCounts(
    @ColumnInfo(name = "collected") public val collected: Int,
    @ColumnInfo(name = "locked") public val locked: Int,
    @ColumnInfo(name = "voided") public val voided: Int,
    @ColumnInfo(name = "participants") public val participants: Int,
) {
    /** What is still open to amendment — the number a coordinator is working through. */
    public val unlocked: Int get() = collected - locked
}
