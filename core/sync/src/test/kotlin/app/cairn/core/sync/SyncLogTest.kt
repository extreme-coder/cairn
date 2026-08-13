package app.cairn.core.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class SyncLogTest {

    @get:Rule
    val folder: TemporaryFolder = TemporaryFolder()

    private val at = Instant.parse("2026-08-12T09:02:00Z")

    private fun store(scope: CoroutineScope) =
        PreferenceDataStoreFactory.create(scope = scope) {
            File(folder.root, "sync_cursors.preferences_pb")
        }

    private suspend fun <T> withStore(block: suspend (DataStore<Preferences>) -> T): T {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return try {
            block(store(scope))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `a device that has never synced has no last synced time`() = runTest {
        withStore { assertNull(DataStoreSyncLog(it).lastSyncedAt.first()) }
    }

    @Test
    fun `a recorded time reads back`() = runTest {
        withStore {
            val log = DataStoreSyncLog(it)
            log.record(at)

            assertEquals(at, log.lastSyncedAt.first())
        }
    }

    @Test
    fun `recording again replaces rather than accumulates`() = runTest {
        withStore {
            val log = DataStoreSyncLog(it)
            log.record(at)
            log.record(at + 1.hours)

            assertEquals(at + 1.hours, log.lastSyncedAt.first())
        }
    }

    /**
     * The last-synced time survives a restart, which is the whole reason it is
     * not [SyncStatus]. Two DataStores over one file throw, so the first scope is
     * cancelled before the second is opened — see the wiki's gotchas.
     */
    @Test
    fun `it survives the process it was written in`() = runTest {
        withStore { DataStoreSyncLog(it).record(at) }

        withStore { assertEquals(at, DataStoreSyncLog(it).lastSyncedAt.first()) }
    }

    /**
     * The coupling that makes the shared file correct rather than merely
     * convenient. A last-synced time inherited by the next collector is the same
     * bug as an inherited cursor: it says this device is up to date on rows it
     * has never seen.
     */
    @Test
    fun `a sign-out clears the last synced time along with the cursors`() = runTest {
        withStore { store ->
            val log = DataStoreSyncLog(store)
            val cursors = DataStoreSyncCursors(store)
            log.record(at)
            cursors.write("studies", scope = "all", cursor = ts(1))

            cursors.clear()

            assertNull(log.lastSyncedAt.first())
        }
    }
}
