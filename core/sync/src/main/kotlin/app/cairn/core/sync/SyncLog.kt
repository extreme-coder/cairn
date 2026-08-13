package app.cairn.core.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/**
 * When this device last completed a sync.
 *
 * [SyncStatus] answers "has a pull ever finished, this run" and dies with the
 * process, which is what an empty screen needs to know and nothing else does. It
 * cannot answer the question the Settings screen actually asks — *when* — because
 * a cold start resets it to false and the honest answer at that moment is
 * usually "eleven minutes ago", not "never".
 *
 * Only a clean run is recorded. A run whose push failed and whose pull succeeded
 * moved data, but "last synced" is read as "everything is up to date as of", and
 * saying that while submissions are still sitting in the queue would be the one
 * lie this screen must not tell.
 */
public interface SyncLog {

    /** Null until a run has finished cleanly on this device, for this user. */
    public val lastSyncedAt: Flow<Instant?>

    public suspend fun record(at: Instant)
}

/**
 * Kept in the same DataStore file as the cursors, deliberately.
 *
 * Both answer "where this device got to", both belong to whoever is signed in,
 * and both have to be gone before the next collector signs in — a last-synced
 * time inherited from the previous user is the same class of bug as an inherited
 * cursor, just a more visible one. [SyncCursors.clear] empties the file, so this
 * goes with them; `a sign-out clears the last synced time` in `SyncLogTest`
 * pins that rather than leaving it to be rediscovered.
 *
 * Sharing the file is also required, not merely tidy: two DataStores over one
 * path throw.
 */
public class DataStoreSyncLog(
    private val store: DataStore<Preferences>,
) : SyncLog {

    override val lastSyncedAt: Flow<Instant?> =
        store.data.map { preferences ->
            preferences[LAST_SYNCED]?.let(Instant::fromEpochMilliseconds)
        }

    override suspend fun record(at: Instant) {
        store.edit { it[LAST_SYNCED] = at.toEpochMilliseconds() }
    }

    private companion object {
        val LAST_SYNCED = longPreferencesKey("last_synced_at")
    }
}
