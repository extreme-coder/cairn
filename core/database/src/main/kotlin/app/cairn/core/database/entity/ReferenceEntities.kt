package app.cairn.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.cairn.core.model.StudyRole
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

/**
 * Reference data: studies, membership, forms, participants, translations.
 *
 * All of it flows down from the server and is read-only on device. Only
 * [SubmissionEntity] flows up. That asymmetry is why none of these carry sync
 * state — there is never a local edit to reconcile.
 */

@Entity(tableName = "studies")
public data class StudyEntity(
    @PrimaryKey @ColumnInfo(name = "id") public val id: String,
    @ColumnInfo(name = "name") public val name: String,
    @ColumnInfo(name = "created_by") public val createdBy: String,
    @ColumnInfo(name = "created_at") public val createdAt: Instant,
)

@Entity(
    tableName = "study_members",
    primaryKeys = ["study_id", "user_id"],
    foreignKeys = [
        ForeignKey(
            entity = StudyEntity::class,
            parentColumns = ["id"],
            childColumns = ["study_id"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
    indices = [Index(value = ["user_id"])],
)
public data class StudyMemberEntity(
    @ColumnInfo(name = "study_id") public val studyId: String,
    @ColumnInfo(name = "user_id") public val userId: String,
    @ColumnInfo(name = "role") public val role: StudyRole,
    @ColumnInfo(name = "added_at") public val addedAt: Instant,
)

@Entity(
    tableName = "forms",
    foreignKeys = [
        ForeignKey(
            entity = StudyEntity::class,
            parentColumns = ["id"],
            childColumns = ["study_id"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
    indices = [Index(value = ["study_id", "code"], unique = true)],
)
public data class FormEntity(
    @PrimaryKey @ColumnInfo(name = "id") public val id: String,
    @ColumnInfo(name = "study_id") public val studyId: String,
    @ColumnInfo(name = "code") public val code: String,
    @ColumnInfo(name = "created_at") public val createdAt: Instant,
)

/**
 * `schema` is held as raw JSON rather than a decoded [app.cairn.core.model.FormSchema].
 *
 * A pull must not fail because the server published a field type this build of
 * the app has never heard of. Storing the payload verbatim moves that failure
 * from sync — where it would stall every later row — to the moment a screen
 * actually tries to render the form, where it is one form that cannot be opened.
 * Decode with [app.cairn.core.database.toFormSchema].
 */
@Entity(
    tableName = "form_versions",
    foreignKeys = [
        ForeignKey(
            entity = FormEntity::class,
            parentColumns = ["id"],
            childColumns = ["form_id"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
    indices = [Index(value = ["form_id", "version"], unique = true)],
)
public data class FormVersionEntity(
    @PrimaryKey @ColumnInfo(name = "id") public val id: String,
    @ColumnInfo(name = "form_id") public val formId: String,
    @ColumnInfo(name = "version") public val version: Int,
    @ColumnInfo(name = "schema") public val schema: JsonObject,
    @ColumnInfo(name = "published_at") public val publishedAt: Instant?,
    @ColumnInfo(name = "created_at") public val createdAt: Instant,
)

@Entity(
    tableName = "participants",
    foreignKeys = [
        ForeignKey(
            entity = StudyEntity::class,
            parentColumns = ["id"],
            childColumns = ["study_id"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
    indices = [Index(value = ["study_id", "code"], unique = true)],
)
public data class ParticipantEntity(
    @PrimaryKey @ColumnInfo(name = "id") public val id: String,
    @ColumnInfo(name = "study_id") public val studyId: String,
    @ColumnInfo(name = "code") public val code: String,
    @ColumnInfo(name = "created_at") public val createdAt: Instant,
)

/**
 * `reviewed_at` null means a machine draft. The server already hides those from
 * collectors and viewers, so for most devices the column is never null; the
 * queries still filter on it so a coordinator's device does not show an
 * unreviewed label to a participant.
 */
@Entity(
    tableName = "form_translations",
    foreignKeys = [
        ForeignKey(
            entity = FormVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["form_version_id"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
    indices = [Index(value = ["form_version_id", "lang"], unique = true)],
)
public data class FormTranslationEntity(
    @PrimaryKey @ColumnInfo(name = "id") public val id: String,
    @ColumnInfo(name = "form_version_id") public val formVersionId: String,
    @ColumnInfo(name = "lang") public val lang: String,
    @ColumnInfo(name = "labels") public val labels: JsonObject,
    @ColumnInfo(name = "engine") public val engine: String?,
    @ColumnInfo(name = "reviewed_by") public val reviewedBy: String?,
    @ColumnInfo(name = "reviewed_at") public val reviewedAt: Instant?,
    @ColumnInfo(name = "created_at") public val createdAt: Instant,
    @ColumnInfo(name = "updated_at") public val updatedAt: Instant,
)
