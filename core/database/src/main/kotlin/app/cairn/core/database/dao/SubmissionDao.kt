package app.cairn.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.cairn.core.database.entity.SubmissionEntity
import app.cairn.core.model.SyncState
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * Identifies a submission the way both sides of the wire do.
 *
 * Not the server's `id`: a row exists on the device before the server has ever
 * seen it, so `id` is null for exactly the rows a pull most needs to avoid.
 */
public data class SubmissionKey(
    @ColumnInfo(name = "collected_by") public val collectedBy: String,
    @ColumnInfo(name = "client_id") public val clientId: String,
)

@Dao
public interface SubmissionDao {

    @Query(
        """
        select * from submissions
         where study_id = :studyId and deleted_at is null
         order by collected_at desc
        """,
    )
    public fun observeForStudy(studyId: String): Flow<List<SubmissionEntity>>

    /** A collector's own queue. Their pull returns only these rows anyway. */
    @Query(
        """
        select * from submissions
         where study_id = :studyId and collected_by = :userId and deleted_at is null
         order by collected_at desc
        """,
    )
    public fun observeForCollector(studyId: String, userId: String): Flow<List<SubmissionEntity>>

    @Query("select * from submissions where collected_by = :collectedBy and client_id = :clientId")
    public fun observe(collectedBy: String, clientId: String): Flow<SubmissionEntity?>

    @Query("select count(*) from submissions where sync_state != 'UPLOADED'")
    public fun observeUnsyncedCount(): Flow<Int>

    /**
     * Everything this collector has recorded that the server has not
     * acknowledged, across every study.
     *
     * The Queue is not scoped to a study on purpose. A collector walking two
     * transects in a morning has one bag of unsent observations, and asking them
     * to check each study separately to find out whether anything is stuck is
     * how a submission gets left behind.
     *
     * `collected_at desc` matches the capture screen and the Studies list: the
     * most recent thing you did is at the top, whatever state it is in.
     */
    @Query(
        """
        select s.client_id as client_id,
               s.study_id as study_id,
               st.name as study_name,
               f.code as form_code,
               fv.version as version,
               p.code as participant_code,
               s.collected_at as collected_at,
               s.sync_state as sync_state
          from submissions s
          join studies st on st.id = s.study_id
          join form_versions fv on fv.id = s.form_version_id
          join forms f on f.id = fv.form_id
          left join participants p on p.id = s.participant_id
         where s.collected_by = :userId
           and s.deleted_at is null
           and s.sync_state != 'UPLOADED'
         order by s.collected_at desc
        """,
    )
    public fun observePending(userId: String): Flow<List<QueuedSubmission>>

    /**
     * The uploaded half of the Queue, behind "Show all uploaded".
     *
     * Limited, because this list only grows: a device three months into a season
     * holds thousands of these and none of them need anything done to them.
     */
    @Query(
        """
        select s.client_id as client_id,
               s.study_id as study_id,
               st.name as study_name,
               f.code as form_code,
               fv.version as version,
               p.code as participant_code,
               s.collected_at as collected_at,
               s.sync_state as sync_state
          from submissions s
          join studies st on st.id = s.study_id
          join form_versions fv on fv.id = s.form_version_id
          join forms f on f.id = fv.form_id
          left join participants p on p.id = s.participant_id
         where s.collected_by = :userId
           and s.deleted_at is null
           and s.sync_state = 'UPLOADED'
         order by s.collected_at desc
         limit :limit
        """,
    )
    public fun observeUploaded(userId: String, limit: Int = 100): Flow<List<QueuedSubmission>>

    /** The most recent submissions in one study, whatever their state. Drives the Collect screen's Recent section. */
    @Query(
        """
        select s.client_id as client_id,
               s.study_id as study_id,
               st.name as study_name,
               f.code as form_code,
               fv.version as version,
               p.code as participant_code,
               s.collected_at as collected_at,
               s.sync_state as sync_state
          from submissions s
          join studies st on st.id = s.study_id
          join form_versions fv on fv.id = s.form_version_id
          join forms f on f.id = fv.form_id
          left join participants p on p.id = s.participant_id
         where s.collected_by = :userId
           and s.study_id = :studyId
           and s.deleted_at is null
         order by s.collected_at desc
         limit :limit
        """,
    )
    public fun observeRecent(studyId: String, userId: String, limit: Int = 5): Flow<List<QueuedSubmission>>

    /**
     * `coalesce` because an aggregate over no rows is null, not zero, and a
     * fresh device has no rows.
     *
     * It is belt-and-braces rather than the thing that saves this: Room reads a
     * NULL column into a non-null `Int` as 0, so removing the `coalesce` does
     * not currently fail `an empty device counts three zeroes` — checked, not
     * assumed. It stays because the SQL should mean what it says, and because a
     * future `Int?` or a different driver would make the difference real.
     */
    @Query(
        """
        select coalesce(sum(case when sync_state = 'QUEUED' then 1 else 0 end), 0) as queued,
               coalesce(sum(case when sync_state = 'FAILED' then 1 else 0 end), 0) as failed,
               coalesce(sum(case when sync_state = 'UPLOADED' then 1 else 0 end), 0) as uploaded
          from submissions
         where collected_by = :userId and deleted_at is null
        """,
    )
    public fun observeCounts(userId: String): Flow<QueueCounts>

    /**
     * The coordinator's Submissions screen: everything in one study, whoever
     * collected it.
     *
     * **Voided rows are not filtered out**, which is the one thing that makes
     * this query different from every other list in the app. Voiding excludes a
     * submission from analysis and keeps the row; hiding it here would make a
     * void look exactly like a delete to the person who performed it, and the
     * promise that nothing is ever hard-deleted would be invisible on the only
     * screen where it matters.
     *
     * Limited for the same reason the uploaded half of the Queue is: a study
     * three seasons in holds thousands and a review list is read from the top.
     */
    @Query(
        """
        select s.collected_by as collected_by,
               s.client_id as client_id,
               s.id as id,
               f.code as form_code,
               fv.version as version,
               p.code as participant_code,
               s.collected_at as collected_at,
               s.locked_at as locked_at,
               s.deleted_at as deleted_at,
               s.sync_state as sync_state
          from submissions s
          join form_versions fv on fv.id = s.form_version_id
          join forms f on f.id = fv.form_id
          left join participants p on p.id = s.participant_id
         where s.study_id = :studyId
         order by s.collected_at desc
         limit :limit
        """,
    )
    public fun observeForReview(studyId: String, limit: Int = 200): Flow<List<ReviewSubmission>>

    /**
     * One submission and the schema it was collected under.
     *
     * Keyed the way every local reference to a submission is keyed, because the
     * server's `id` is null until a push comes back and a coordinator may be
     * looking at a row they collected themselves this morning.
     */
    @Query(
        """
        select s.collected_by as collected_by,
               s.client_id as client_id,
               s.id as id,
               s.study_id as study_id,
               st.name as study_name,
               f.code as form_code,
               fv.version as version,
               fv.schema as schema,
               p.code as participant_code,
               s.collected_at as collected_at,
               s.updated_at as updated_at,
               s.locked_at as locked_at,
               s.deleted_at as deleted_at,
               s.sync_state as sync_state,
               s.data as data
          from submissions s
          join studies st on st.id = s.study_id
          join form_versions fv on fv.id = s.form_version_id
          join forms f on f.id = fv.form_id
          left join participants p on p.id = s.participant_id
         where s.collected_by = :collectedBy and s.client_id = :clientId
        """,
    )
    public fun observeDetail(collectedBy: String, clientId: String): Flow<SubmissionDetail?>

    /**
     * Submissions per calendar day, mirroring the server's `v_study_progress`.
     *
     * [zoneOffsetMillis] shifts the instant before the day is taken, so a
     * transect walked at 18:00 in Whitehorse lands on the day it was walked
     * rather than on the next one in UTC. Comparing before applying the zone is
     * the same mistake `collectedLabel` guards against, and it is the kind a
     * reader cannot catch from the chart.
     *
     * The offset is a parameter rather than SQLite's `localtime` modifier so the
     * query is deterministic under test, and because `localtime` would read the
     * process's zone from inside the database, which is not somewhere a timezone
     * should be coming from. One offset for the whole range means a daylight
     * saving change inside the window shifts an hour of observations onto the
     * neighbouring day; over a fourteen-day chart that is at most one bar off by
     * a fraction, and the fix if it ever matters is grouping in Kotlin.
     */
    @Query(
        """
        select date((s.collected_at + :zoneOffsetMillis) / 1000, 'unixepoch') as day,
               count(*) as n_submissions,
               count(distinct s.participant_id) as n_participants
          from submissions s
         where s.study_id = :studyId and s.deleted_at is null
         group by day
         order by day
        """,
    )
    public fun observeProgress(studyId: String, zoneOffsetMillis: Long): Flow<List<ProgressDay>>

    /** The three numbers above the chart. Counted by the database, not by `.size`. */
    @Query(
        """
        select coalesce(sum(case when deleted_at is null then 1 else 0 end), 0) as collected,
               coalesce(sum(case when locked_at is not null and deleted_at is null then 1 else 0 end), 0) as locked,
               coalesce(sum(case when deleted_at is not null then 1 else 0 end), 0) as voided,
               count(distinct case when deleted_at is null then participant_id end) as participants
          from submissions
         where study_id = :studyId
        """,
    )
    public fun observeReviewCounts(studyId: String): Flow<ReviewCounts>

    /**
     * Writes back what the server echoed after a lock, a void or a restore.
     *
     * **`sync_state` is deliberately untouched.** The server has already applied
     * the change — that is where these values came from — so the row is not
     * pending anything. Marking it `QUEUED` would put the coordinator's copy of
     * the payload into the next push under the *collector's*
     * `(collected_by, client_id)` key, which is exactly the clobber that writing
     * one column over HTTP exists to avoid.
     *
     * [updatedAt] is the server's, for the same reason [markUploaded] takes it:
     * it is what last-write-wins compares and the device never gets to write it.
     */
    @Query(
        """
        update submissions
           set locked_at = :lockedAt,
               deleted_at = :deletedAt,
               updated_at = :updatedAt
         where collected_by = :collectedBy and client_id = :clientId
        """,
    )
    public suspend fun applyReview(
        collectedBy: String,
        clientId: String,
        lockedAt: Instant?,
        deletedAt: Instant?,
        updatedAt: Instant,
    )

    /** What the sync worker drains. Oldest first, so a long queue leaves in order. */
    @Query(
        """
        select * from submissions
         where sync_state = :state
         order by pending_since asc
         limit :limit
        """,
    )
    public suspend fun awaiting(state: SyncState = SyncState.QUEUED, limit: Int = 500): List<SubmissionEntity>

    /**
     * Every row that has a local change the server has not acknowledged.
     *
     * A pull must not write over these. The device's copy is the only copy, and
     * the incoming row is the server's older idea of it — applying it would
     * discard an observation that was collected offline and never sent.
     */
    @Query("select collected_by, client_id from submissions where sync_state != 'UPLOADED'")
    public suspend fun pendingKeys(): List<SubmissionKey>

    @Upsert
    public suspend fun upsert(submissions: List<SubmissionEntity>)

    @Upsert
    public suspend fun upsert(submission: SubmissionEntity)

    /**
     * Called with what the server echoed back after a push: its `id` and its
     * authoritative `updated_at`. The local clock never wins here — `updated_at`
     * is what last-write-wins compares, so it has to be the server's value.
     */
    @Query(
        """
        update submissions
           set id = :serverId,
               updated_at = :updatedAt,
               sync_state = 'UPLOADED',
               pending_since = null
         where collected_by = :collectedBy and client_id = :clientId
        """,
    )
    public suspend fun markUploaded(
        collectedBy: String,
        clientId: String,
        serverId: String,
        updatedAt: Instant,
    )

    @Query(
        """
        update submissions
           set sync_state = 'FAILED'
         where collected_by = :collectedBy and client_id = :clientId
        """,
    )
    public suspend fun markFailed(collectedBy: String, clientId: String)

    /** Re-queues everything a previous run gave up on, for the next backoff window. */
    @Query("update submissions set sync_state = 'QUEUED' where sync_state = 'FAILED'")
    public suspend fun requeueFailed()
}
