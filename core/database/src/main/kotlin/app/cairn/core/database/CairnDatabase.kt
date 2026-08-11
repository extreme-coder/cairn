package app.cairn.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.cairn.core.database.dao.FormDao
import app.cairn.core.database.dao.FormTranslationDao
import app.cairn.core.database.dao.ParticipantDao
import app.cairn.core.database.dao.StudyDao
import app.cairn.core.database.dao.StudyMemberDao
import app.cairn.core.database.dao.SubmissionDao
import app.cairn.core.database.entity.FormEntity
import app.cairn.core.database.entity.FormTranslationEntity
import app.cairn.core.database.entity.FormVersionEntity
import app.cairn.core.database.entity.ParticipantEntity
import app.cairn.core.database.entity.StudyEntity
import app.cairn.core.database.entity.StudyMemberEntity
import app.cairn.core.database.entity.SubmissionEntity
import app.cairn.core.model.FormSchema
import kotlinx.serialization.json.JsonObject

/**
 * The single source of truth. The UI reads from here and never from the network.
 *
 * Schemas are exported to `core/database/schemas` and checked in, which is what
 * makes a migration test possible at all.
 */
@Database(
    entities = [
        StudyEntity::class,
        StudyMemberEntity::class,
        FormEntity::class,
        FormVersionEntity::class,
        ParticipantEntity::class,
        SubmissionEntity::class,
        FormTranslationEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(CairnConverters::class)
public abstract class CairnDatabase : RoomDatabase() {
    public abstract fun studies(): StudyDao
    public abstract fun members(): StudyMemberDao
    public abstract fun forms(): FormDao
    public abstract fun participants(): ParticipantDao
    public abstract fun submissions(): SubmissionDao
    public abstract fun translations(): FormTranslationDao

    public companion object {
        public const val NAME: String = "cairn.db"
    }
}

/**
 * Decodes a stored form schema.
 *
 * Throws if this build does not understand the payload — a field type added
 * after this version of the app shipped, say. That is deliberate and local: the
 * row is already safely on disk, so only the screen that opens this one form
 * fails, and the rest of the study keeps working.
 */
public fun JsonObject.toFormSchema(): FormSchema = cairnJson.decodeFromJsonElement(FormSchema.serializer(), this)
