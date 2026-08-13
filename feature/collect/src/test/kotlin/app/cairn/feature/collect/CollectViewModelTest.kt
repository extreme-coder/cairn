package app.cairn.feature.collect

import app.cairn.core.database.CairnDatabase
import app.cairn.core.model.StudyRole
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
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The two Collect-tab ViewModels, against a real in-memory database.
 *
 * Fakes would prove the mapping and nothing else; the queries these read are
 * the part most likely to be subtly wrong, so they run against SQLite. Every
 * assertion is on finished text, because finished text is what a collector
 * reads and what a screen is not allowed to reassemble.
 */
@RunWith(RobolectricTestRunner::class)
class CollectViewModelTest {

    private lateinit var db: CairnDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = testDatabase()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    // ---- Studies ----

    private fun studies(synced: Boolean = true) = StudiesViewModel(
        studies = db.studies(),
        userId = Ids.ADAKU,
        syncedOnce = MutableStateFlow(synced),
    )

    private suspend fun TestScope.studiesReady(synced: Boolean = true): StudiesUiState.Ready =
        watching(studies(synced).uiState).awaiting { it is StudiesUiState.Ready } as StudiesUiState.Ready

    @Test
    fun `a study reads as its name, its contents and whether anything is waiting`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-1"))
        db.submissions().upsert(submission("c-2", syncState = SyncState.UPLOADED))

        val row = studiesReady().studies.single()

        assertEquals("Kluane ground squirrel survey", row.name)
        assertEquals("3 forms · 2 submissions", row.detail)
        assertEquals("1 queued", row.status)
        assertEquals(StudyRole.COLLECTOR, row.role)
    }

    @Test
    fun `a study with nothing waiting says so rather than saying nothing`() = runTest {
        db.seedKluane()

        assertEquals("All uploaded", studiesReady().studies.single().status)
    }

    /**
     * A viewer has nothing to collect, so "All uploaded" would be answering a
     * question they did not ask. The role decides the sentence.
     */
    @Test
    fun `a viewer's study reads as read only`() = runTest {
        db.seedKluane()
        db.seedPeel()

        val peel = studiesReady().studies.single { it.id == Ids.PEEL }

        assertEquals("Read only", peel.status)
        assertFalse(peel.collectable)
    }

    /**
     * Both are zero rows, and only whether a sync has finished can tell them
     * apart. Getting this backwards sends a collector to phone their coordinator
     * about a download that had not finished.
     */
    @Test
    fun `an empty device distinguishes downloading from being in no study`() = runTest {
        assertEquals(
            StudiesUiState.Empty(synced = false),
            watching(studies(synced = false).uiState).awaiting { it is StudiesUiState.Empty },
        )
        assertEquals(
            StudiesUiState.Empty(synced = true),
            watching(studies(synced = true).uiState).awaiting { it is StudiesUiState.Empty },
        )
    }

    @Test
    fun `studies are ordered by name, not by arrival`() = runTest {
        db.seedKluane()
        db.seedPeel()

        assertEquals(
            listOf("Kluane ground squirrel survey", "Peel watershed water quality"),
            studiesReady().studies.map { it.name },
        )
    }

    // ---- One study ----

    private fun collect(studyId: String = Ids.KLUANE) = CollectViewModel(
        studies = db.studies(),
        members = db.members(),
        forms = db.forms(),
        submissions = db.submissions(),
        studyId = studyId,
        userId = Ids.ADAKU,
        now = { at(20) },
        zone = UTC,
    )

    private suspend fun TestScope.collectReady(studyId: String = Ids.KLUANE): CollectUiState.Ready =
        watching(collect(studyId).uiState).awaiting { it is CollectUiState.Ready } as CollectUiState.Ready

    @Test
    fun `a form offers its published version and its field count`() = runTest {
        db.seedKluane()

        val form = collectReady().forms.single { it.id == Ids.BASELINE }

        assertEquals("Baseline intake", form.title)
        assertEquals("2 fields", form.detail)
        assertEquals("v3", form.versionLabel)
        assertTrue(form.openable)
    }

    /**
     * Forms and versions arrive in separate pulls. The row appears with the
     * reason it cannot be opened rather than vanishing, and it is not tappable —
     * tapping through to a capture screen that immediately fails is a worse
     * answer than not offering the tap.
     */
    @Test
    fun `a form whose version has not arrived says so and cannot be opened`() = runTest {
        db.seedKluane()

        val form = collectReady().forms.single { it.id == Ids.TRAP }

        assertEquals("No published version yet", form.detail)
        assertNull(form.versionLabel)
        assertFalse(form.openable)
    }

    /**
     * The raw-JSON decision surfacing: the row is safely on disk, and one form
     * cannot be rendered rather than the study failing to sync.
     */
    @Test
    fun `a schema this build cannot decode is one closed form, not a broken study`() = runTest {
        db.seedKluane()

        val state = collectReady()

        val future = state.forms.single { it.id == Ids.FUTURE }
        assertEquals("Not supported by this version of Cairn", future.detail)
        assertFalse(future.openable)
        assertTrue(state.forms.single { it.id == Ids.BASELINE }.openable)
    }

    @Test
    fun `recent submissions read as form, version and time`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-1", collectedAt = at(14)))

        val row = collectReady().recent.single()

        assertEquals("KL-0148", row.label)
        assertEquals("Baseline intake v3 · 09:14", row.detail)
        assertEquals(SyncState.QUEUED, row.state)
    }

    @Test
    fun `recent is this study's, not the device's`() = runTest {
        db.seedKluane()
        db.seedPeel()
        db.submissions().upsert(submission("c-1"))
        db.submissions().upsert(
            submission("c-2", studyId = Ids.PEEL, formVersionId = Ids.PEEL_V1, participantId = null),
        )

        assertEquals(listOf("c-1"), collectReady().recent.map { it.clientId })
        assertEquals(listOf("c-2"), collectReady(Ids.PEEL).recent.map { it.clientId })
    }

    /**
     * The banner counts the device, not the study: it says how much work is
     * unsent in total, which is the number that decides whether it is safe to
     * hand the phone over.
     */
    @Test
    fun `the banner counts everything unsent, across studies`() = runTest {
        db.seedKluane()
        db.seedPeel()
        db.submissions().upsert(submission("c-1"))
        db.submissions().upsert(
            submission("c-2", studyId = Ids.PEEL, formVersionId = Ids.PEEL_V1, participantId = null),
        )

        assertEquals(2, collectReady().pendingCount)
    }

    /**
     * A sign-out wipes the database while this screen may be on top of the
     * stack. Rendering an empty form list under the old study's name would leave
     * someone tapping at a study that no longer exists.
     */
    @Test
    fun `a study removed from the device reads as gone, not as empty`() = runTest {
        db.seedKluane()
        val state = watching(collect().uiState)
        assertTrue((state.awaiting { it is CollectUiState.Ready } as CollectUiState.Ready).forms.isNotEmpty())

        // Off the main thread: Room refuses a clear on it, and Robolectric runs
        // tests *on* the main looper.
        withContext(Dispatchers.IO) { db.clearAllTables() }

        assertEquals(CollectUiState.Gone, state.awaiting { it is CollectUiState.Gone })
    }

    @Test
    fun `the role comes from this collector's membership`() = runTest {
        db.seedKluane()

        assertEquals(StudyRole.COLLECTOR, collectReady().role)
    }

    @Test
    fun `forms are listed in a stable order`() = runTest {
        db.seedKluane()

        assertEquals(
            listOf("Baseline intake", "Call survey", "Trap check"),
            collectReady().forms.map { it.title },
        )
    }
}
