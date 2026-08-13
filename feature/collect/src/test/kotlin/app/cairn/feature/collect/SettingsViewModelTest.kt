package app.cairn.feature.collect

import app.cairn.core.database.CairnDatabase
import app.cairn.core.model.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    private lateinit var db: CairnDatabase

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = testDatabase()
        db.seedKluane()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun settings(
        email: String? = "adaku.obi@cairn.test",
        stale: Boolean = false,
        lastSyncedAt: Instant? = at(6),
    ) = SettingsViewModel(
        submissions = db.submissions(),
        userId = Ids.ADAKU,
        server = "cairn.psych.ubc.ca",
        version = "0.1.0 (1)",
        email = MutableStateFlow(email),
        stale = MutableStateFlow(stale),
        lastSyncedAt = MutableStateFlow(lastSyncedAt),
        now = { at(20) },
    )

    @Test
    fun `it says who is signed in, where, and when it last synced`() = runTest {
        val state = watching(settings().uiState).awaiting { it.lastSynced.isNotEmpty() }

        assertEquals("adaku.obi@cairn.test", state.email)
        assertEquals("adaku.obi@cairn.test", state.name)
        assertEquals("cairn.psych.ubc.ca", state.server)
        assertEquals("14 minutes ago", state.lastSynced)
        assertEquals("0.1.0 (1)", state.version)
    }

    @Test
    fun `a device that has never synced says so`() = runTest {
        val state = watching(settings(lastSyncedAt = null).uiState).awaiting { it.lastSynced.isNotEmpty() }

        assertEquals("Not synced on this device yet", state.lastSynced)
    }

    /**
     * The sign-out guard's number, shown before the tap rather than after it.
     * `FAILED` counts: the last attempt did not work, not that the row is
     * disposable.
     */
    @Test
    fun `it counts what a sign-out would refuse over`() = runTest {
        db.submissions().upsert(submission("c-1", syncState = SyncState.QUEUED))
        db.submissions().upsert(submission("c-2", syncState = SyncState.FAILED))
        db.submissions().upsert(submission("c-3", syncState = SyncState.UPLOADED))

        assertEquals(2, watching(settings().uiState).awaiting { it.pendingCount > 0 }.pendingCount)
    }

    @Test
    fun `a stale session is reported as signed in, not as an error`() = runTest {
        assertTrue(watching(settings(stale = true).uiState).awaiting { it.stale }.stale)
    }

    /** Two initials in a tonal circle: the only representation of a person in this app. */
    @Test
    fun `initials come off the address`() {
        assertEquals("AO", SettingsUiState(email = "adaku.obi@cairn.test").initials)
        assertEquals("AO", SettingsUiState(email = "adaku_obi@cairn.test").initials)
        assertEquals("A", SettingsUiState(email = "adaku@cairn.test").initials)
    }

    /**
     * A session stored by an earlier build carries no email. The screen falls
     * back to something true rather than rendering a blank where a name goes.
     */
    @Test
    fun `with no email it falls back to the id rather than to nothing`() {
        val state = SettingsUiState(email = null, userId = Ids.ADAKU)

        assertEquals(Ids.ADAKU, state.name)
        assertEquals("?", state.initials)
    }

    @Test
    fun `the last synced label moves as time passes`() = runTest {
        val state = watching(
            SettingsViewModel(
                submissions = db.submissions(),
                userId = Ids.ADAKU,
                server = "s",
                version = "v",
                email = MutableStateFlow(null),
                stale = MutableStateFlow(false),
                lastSyncedAt = MutableStateFlow(T0),
                now = { T0 + 3.minutes },
            ).uiState,
        ).awaiting { it.lastSynced.isNotEmpty() }

        assertEquals("3 minutes ago", state.lastSynced)
    }
}
