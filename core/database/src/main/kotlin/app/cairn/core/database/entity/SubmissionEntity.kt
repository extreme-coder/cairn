package app.cairn.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import app.cairn.core.model.SyncState
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

/**
 * The only table that flows upward.
 *
 * **The primary key is `(collected_by, client_id)`, not the server's `id`.** A
 * submission exists on device before it exists on the server, so `id` is null
 * until a push comes back. Keying on `id` would mean rewriting the primary key
 * after every first sync, and every local reference to the row with it.
 * `(collected_by, client_id)` is exactly the unique constraint the server
 * upserts on, so the same row is the same row on both sides from the moment it
 * is created — which is what makes replaying a failed queue a no-op rather than
 * a set of duplicates.
 *
 * [syncState] and [pendingSince] have no server counterpart. They are queue
 * state and never travel.
 */
@Entity(
    tableName = "submissions",
    primaryKeys = ["collected_by", "client_id"],
    foreignKeys = [
        ForeignKey(
            entity = StudyEntity::class,
            parentColumns = ["id"],
            childColumns = ["study_id"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
        ForeignKey(
            entity = FormVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["form_version_id"],
            deferred = true,
        ),
        ForeignKey(
            entity = ParticipantEntity::class,
            parentColumns = ["id"],
            childColumns = ["participant_id"],
            deferred = true,
        ),
    ],
    indices = [
        Index(value = ["study_id", "updated_at"]),
        Index(value = ["study_id", "collected_by", "updated_at"]),
        Index(value = ["form_version_id"]),
        Index(value = ["participant_id"]),
        Index(value = ["sync_state"]),
        Index(value = ["id"]),
    ],
)
public data class SubmissionEntity(
    @ColumnInfo(name = "collected_by") public val collectedBy: String,
    @ColumnInfo(name = "client_id") public val clientId: String,
    @ColumnInfo(name = "id") public val id: String? = null,
    @ColumnInfo(name = "study_id") public val studyId: String,
    @ColumnInfo(name = "form_version_id") public val formVersionId: String,
    @ColumnInfo(name = "participant_id") public val participantId: String? = null,
    @ColumnInfo(name = "collected_at") public val collectedAt: Instant,
    @ColumnInfo(name = "data") public val data: JsonObject,
    @ColumnInfo(name = "locked_at") public val lockedAt: Instant? = null,
    @ColumnInfo(name = "updated_at") public val updatedAt: Instant,
    @ColumnInfo(name = "deleted_at") public val deletedAt: Instant? = null,
    @ColumnInfo(name = "sync_state") public val syncState: SyncState = SyncState.QUEUED,
    @ColumnInfo(name = "pending_since") public val pendingSince: Instant? = null,
) {
    /** Non-null once verified on the server, after which no client may edit it. */
    public val isLocked: Boolean get() = lockedAt != null

    /** Voided, never hard-deleted. The server revokes `delete` outright. */
    public val isVoided: Boolean get() = deletedAt != null
}
