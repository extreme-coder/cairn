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
