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
