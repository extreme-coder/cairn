package app.cairn.feature.review

import app.cairn.core.database.CairnDatabase
import app.cairn.core.model.ReviewState
import app.cairn.core.model.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * One submission, its actions, and what the server's three answers do to the
 * screen.
 *
 * Against a real database and a fake server, because the two halves that can be
 * wrong are the query — which schema is read for a row that pins one — and the
 * write-back, and neither is provable against a fake DAO.
 */
@RunWith(RobolectricTestRunner::class)
class SubmissionDetailViewModelTest {

    private lateinit var db: CairnDatabase
    private lateinit var remote: FakeRemote

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = testDatabase()
        remote = FakeRemote()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun model(userId: String = Ids.TOMAS, clientId: String = "c-148") =
        SubmissionDetailViewModel(
            submissions = db.submissions(),
            members = db.members(),
            studyId = Ids.KLUANE,
            collectedBy = Ids.ADAKU,
            clientId = clientId,
            userId = userId,
            repository = ReviewRepository(db.submissions(), remote),
            now = { at(90) },
            zone = UTC,
        )

    private suspend fun TestScope.ready(model: SubmissionDetailViewModel): DetailUiState.Ready =
        watching(model.uiState).awaiting { it is DetailUiState.Ready } as DetailUiState.Ready

    private suspend fun TestScope.readyAfterSeeding(
        userId: String = Ids.TOMAS,
    ): DetailUiState.Ready = ready(model(userId))

    /**
     * **The versioning ADR arriving at a screen.** The row pins v2; v3 renamed
     * `body_mass` to "Mass at capture" and changed its unit to kg. Reading the
     * current version here would relabel an old observation silently and hand a
     * coordinator a figure that is wrong by three orders of magnitude.
     */
    @Test
    fun `the answers are read against the version the row pins, not the current one`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-148", formVersionId = Ids.BASELINE_V2))

        val state = readyAfterSeeding()

        assertEquals("v2", state.header.versionLabel)
        assertEquals("268.0 g", state.fields.single { it.label == "Body mass" }.value)
        assertTrue(state.fields.none { it.label == "Mass at capture" })
    }

    @Test
    fun `the header says what this is, which study it belongs to and when it was collected`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-148"))

        val header = readyAfterSeeding().header

        assertEquals("KL-0148", header.label)
        assertEquals("Kluane ground squirrel survey", header.studyName)
        assertEquals("Baseline intake", header.formTitle)
        assertEquals("13 Aug 2026 · 09:14", header.collected)
        assertEquals(ReviewState.OPEN, header.state)
    }

    // ---- What each role is offered ----

    @Test
    fun `a coordinator is offered lock and void`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-148"))

        assertEquals(listOf(ReviewAction.LOCK, ReviewAction.VOID), readyAfterSeeding(Ids.TOMAS).actions)
    }

    @Test
    fun `a viewer reads the same submission and is offered nothing`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-148"))

        val state = readyAfterSeeding(Ids.NOOR)

        assertTrue(state.actions.isEmpty())
        assertNull(state.note)
        assertEquals(5, state.fields.size)
    }

    @Test
    fun `a collector is offered nothing, even on their own row`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-148", collectedBy = Ids.ADAKU))

        assertTrue(readyAfterSeeding(Ids.ADAKU).actions.isEmpty())
    }

    @Test
    fun `a locked row offers nothing and says that unlocking is not possible`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-148", lockedAt = at(40)))

        val state = readyAfterSeeding()

        assertTrue(state.actions.isEmpty())
        assertTrue(state.header.locked)
        assertTrue(state.note!!.contains("Unlocking is not possible"))
    }

    @Test
    fun `a voided row offers restore`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-148", deletedAt = at(40)))

        assertEquals(listOf(ReviewAction.RESTORE), readyAfterSeeding().actions)
    }

    @Test
    fun `a row that has not been pushed offers nothing and says why`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-148", id = null, syncState = SyncState.QUEUED))

        val state = readyAfterSeeding()

        assertTrue(state.actions.isEmpty())
        assertTrue(state.note!!.contains("has not uploaded yet"))
    }

    // ---- Confirming ----

    @Test
    fun `nothing is written until the dialog is confirmed`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-148"))
        val model = model()
        val state = watching(model.uiState)
        state.awaiting { it is DetailUiState.Ready }

        model.ask(ReviewAction.LOCK)
        assertEquals(
            ReviewAction.LOCK,
            (state.awaiting { (it as? DetailUiState.Ready)?.confirming != null } as DetailUiState.Ready).confirming,
        )
        assertTrue(remote.calls.isEmpty())

        model.dismiss()
        assertNull(
            (state.awaiting { (it as? DetailUiState.Ready)?.confirming == null } as DetailUiState.Ready).confirming,
        )
        assertTrue(remote.calls.isEmpty())
    }

    /**
     * The screen never renders the answer it hoped for. It redraws because Room
     * re-emitted, which is what stops a refused write from leaving a "Locked"
     * chip on a submission the server did not lock.
     */
    @Test
    fun `confirming a lock redraws the row as locked, from the database`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-148"))
        val model = model()
        val state = watching(model.uiState)
        state.awaiting { it is DetailUiState.Ready }

        model.ask(ReviewAction.LOCK)
        model.confirm()

        val locked = state.awaiting {
            (it as? DetailUiState.Ready)?.header?.locked == true
        } as DetailUiState.Ready

        assertEquals(ReviewState.LOCKED, locked.header.state)
        assertTrue(locked.actions.isEmpty())
        assertNull(locked.problem)
    }

    @Test
    fun `a refusal shows the server's own words and leaves the row open`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-148"))
        remote.refusal = "The server did not change this submission."
        val model = model()
        val state = watching(model.uiState)
        state.awaiting { it is DetailUiState.Ready }

        model.ask(ReviewAction.LOCK)
        model.confirm()

        val refused = state.awaiting {
            (it as? DetailUiState.Ready)?.problem != null
        } as DetailUiState.Ready

        assertEquals("The server did not change this submission.", refused.problem)
        assertEquals(ReviewState.OPEN, refused.header.state)
        assertEquals(listOf(ReviewAction.LOCK, ReviewAction.VOID), refused.actions)
    }

    /**
     * A refusal and a dead network are different sentences, and the offline one
     * names what was *not* done rather than naming the network. The consequence
     * is the part a coordinator has to act on.
     */
    @Test
    fun `an unreachable server says what was not done, not that the network is down`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-148"))
        remote.offline = true
        val model = model()
        val state = watching(model.uiState)
        state.awaiting { it is DetailUiState.Ready }

        model.ask(ReviewAction.VOID)
        model.confirm()

        val offline = state.awaiting {
            (it as? DetailUiState.Ready)?.problem != null
        } as DetailUiState.Ready

        assertEquals(
            "Not voided — the server could not be reached. Try again when you reconnect.",
            offline.problem,
        )
    }

    /**
     * A sync can land while the dialog is open — that is exactly when it is
     * likely to. The id is read at confirm time so the action lands on the row
     * as it is, and a row that still has none refuses rather than writing
     * nowhere.
     */
    @Test
    fun `confirming a row with no server id refuses instead of writing`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-148", id = null, syncState = SyncState.QUEUED))
        val model = model()
        val state = watching(model.uiState)
        state.awaiting { it is DetailUiState.Ready }

        model.ask(ReviewAction.LOCK)
        model.confirm()

        val refused = state.awaiting {
            (it as? DetailUiState.Ready)?.problem != null
        } as DetailUiState.Ready

        assertTrue(refused.problem!!.contains("has not uploaded yet"))
        assertTrue(remote.calls.isEmpty())
    }

    // ---- The edges ----

    /**
     * The raw-`JsonObject` decision reaching the last screen that reads a
     * schema. The row is safely on disk, its provenance still renders, and one
     * submission cannot be laid out rather than the query behind every screen
     * failing.
     */
    @Test
    fun `a schema this build cannot decode still shows what the submission is`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-148", formVersionId = Ids.FUTURE_V1))

        val state = watching(model().uiState)
            .awaiting { it is DetailUiState.Unreadable } as DetailUiState.Unreadable

        assertEquals("KL-0148", state.header.label)
        assertEquals("Call survey", state.header.formTitle)
        assertEquals("v1", state.header.versionLabel)
    }

    @Test
    fun `a submission removed from the device reads as gone`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-148"))
        val state = watching(model().uiState)
        state.awaiting { it is DetailUiState.Ready }

        withContext(Dispatchers.IO) { db.clearAllTables() }

        assertEquals(DetailUiState.Gone, state.awaiting { it is DetailUiState.Gone })
    }

    @Test
    fun `a submission this device does not hold reads as gone rather than loading forever`() = runTest {
        db.seedKluane()

        assertEquals(
            DetailUiState.Gone,
            watching(model(clientId = "c-nothing").uiState).awaiting { it is DetailUiState.Gone },
        )
    }
}
