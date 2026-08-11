package app.cairn.core.database

import androidx.room.TypeConverter
import app.cairn.core.model.StudyRole
import app.cairn.core.model.SyncState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.time.Instant

internal val cairnJson: Json = Json { ignoreUnknownKeys = true }

public object CairnConverters {

    /**
     * Timestamps are stored as epoch milliseconds so ordering is numeric.
     *
     * ISO-8601 text would sort wrongly: kotlinx-datetime omits trailing zeros in
     * the fractional second, so `…:00Z` compares greater than `…:00.5Z`. The sync
     * cursor keeps the server's exact string in DataStore, so the microsecond
     * precision Postgres holds is not lost by this column.
     */
    @TypeConverter
    public fun instantToMillis(value: Instant?): Long? = value?.toEpochMilliseconds()

    @TypeConverter
    public fun millisToInstant(value: Long?): Instant? =
        value?.let(Instant::fromEpochMilliseconds)

    @TypeConverter
    public fun jsonToText(value: JsonObject?): String? = value?.toString()

    @TypeConverter
    public fun textToJson(value: String?): JsonObject? =
        value?.let { cairnJson.parseToJsonElement(it).jsonObject }

    /**
     * Roles round-trip through their wire spelling (`pi`, not `PI`), read off the
     * serializer descriptor so the `@SerialName` annotations in `:core:model`
     * stay the only place the spelling is written down.
     */
    @TypeConverter
    public fun roleToText(value: StudyRole?): String? =
        value?.let { StudyRole.serializer().descriptor.getElementName(it.ordinal) }

    @TypeConverter
    public fun textToRole(value: String?): StudyRole? =
        value?.let { StudyRole.entries[StudyRole.serializer().descriptor.getElementIndex(it)] }

    /** Device-local queue state. It has no wire spelling because the server has no such column. */
    @TypeConverter
    public fun syncStateToText(value: SyncState?): String? = value?.name

    @TypeConverter
    public fun textToSyncState(value: String?): SyncState? = value?.let(SyncState::valueOf)
}
