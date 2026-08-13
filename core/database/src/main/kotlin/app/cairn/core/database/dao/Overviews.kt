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
