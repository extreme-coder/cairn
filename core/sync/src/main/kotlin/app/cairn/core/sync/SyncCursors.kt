package app.cairn.core.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.first
import java.security.MessageDigest

/**
 * Where the pull left off, per table.
 *
 * A cursor is the server's own `updated_at` string, stored verbatim. It is never
 * parsed into an [kotlin.time.Instant] and re-rendered: Postgres keeps
 * microseconds, the device keeps milliseconds, and a round trip through the
 * device's precision would move the boundary and either skip a row or fetch it
 * twice forever.
 *
 * Cursors live here rather than in Room because they are not data — they are
 * where this device got to, and wiping the database on sign-out should take them
 * with it rather than leave a cursor pointing into someone else's rows.
 */
public interface SyncCursors {

    /**
     * The cursor for [key], or null to start from the beginning.
     *
     * [scope] is what the query was scoped to when the cursor was taken. A cursor
     * is only meaningful for the exact question that produced it, so a changed
     * scope reads as no cursor — see [scopeOf].
     */
    public suspend fun read(key: String, scope: String): String?

    public suspend fun write(key: String, scope: String, cursor: String)

    /** Sign-out. A cursor outliving its user would hide that user's rows from the next one. */
    public suspend fun clear()

    public companion object {
        public const val STUDIES: String = "studies"
        public fun members(studyId: String): String = "members:$studyId"
        public fun forms(studyId: String): String = "forms:$studyId"
        public fun formVersions(studyId: String): String = "form_versions:$studyId"
        public fun participants(studyId: String): String = "participants:$studyId"
        public fun translations(studyId: String): String = "translations:$studyId"
        public fun submissions(studyId: String): String = "submissions:$studyId"
    }
}

/**
 * A fingerprint of the ids a query was scoped to.
 *
 * `form_versions` and `form_translations` are fetched by an explicit id list
 * rather than by study, and that list grows as forms are added. Reusing the old
 * cursor after it grows would ask for "versions of these six forms, changed
 * since Tuesday" — and the new form's versions were all created before Tuesday,
 * so they would never arrive. Not once: **never**, because the cursor only moves
 * forward. Changing the scope voids the cursor instead, at the cost of one
 * re-pull of data the device mostly already has.
 *
 * Sorted before hashing so the fingerprint does not depend on the order the
 * server happened to return the parents in.
 */
internal fun scopeOf(ids: Collection<String>): String {
    val joined = ids.sorted().joinToString(",")
    val digest = MessageDigest.getInstance("SHA-256").digest(joined.toByteArray())
    return digest.take(SCOPE_BYTES).joinToString("") { "%02x".format(it) }
}

private const val SCOPE_BYTES = 8

/**
 * The two things this module keeps outside Room, over the one file it writes.
 *
 * Handed out together because they must be built over the same [DataStore]:
 * cursors and the last-synced time are the same kind of fact — where this
 * device got to, for whoever is signed in — they are cleared together on
 * sign-out, and two DataStores over one path throw outright. Returning them as
 * a pair is what stops a caller from opening a second one.
 */
public class SyncStores internal constructor(
    public val cursors: SyncCursors,
    public val log: SyncLog,
)

/**
 * The app's store. Keeps DataStore an implementation detail of this module
 * rather than something `:app` has to depend on and name a file for.
 */
public fun cairnSyncStores(context: Context): SyncStores {
    val store: DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("sync_cursors") }
    return SyncStores(DataStoreSyncCursors(store), DataStoreSyncLog(store))
}

public class DataStoreSyncCursors(
    private val store: DataStore<Preferences>,
) : SyncCursors {

    override suspend fun read(key: String, scope: String): String? {
        val preferences = store.data.first()
        if (preferences[scopeKey(key)] != scope) return null
        return preferences[cursorKey(key)]
    }

    override suspend fun write(key: String, scope: String, cursor: String) {
        store.edit {
            it[scopeKey(key)] = scope
            it[cursorKey(key)] = cursor
        }
    }

    override suspend fun clear() {
        store.edit { it.clear() }
    }

    private fun scopeKey(key: String) = stringPreferencesKey("scope:$key")

    private fun cursorKey(key: String) = stringPreferencesKey("cursor:$key")
}
