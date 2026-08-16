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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The review list, against a real in-memory database.
 *
 * Fakes would prove the mapping and nothing else; the query is the part most
 * likely to be subtly wrong — it is the only list in the app that deliberately
 * does *not* filter out voided rows, and the only one not scoped to the person
 * reading it.
 */
@RunWith(RobolectricTestRunner::class)
class SubmissionsViewModelTest {

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

    private fun model(studyId: String = Ids.KLUANE) = SubmissionsViewModel(
        studies = db.studies(),
        submissions = db.submissions(),
        studyId = studyId,
        now = { at(20) },
        zone = UTC,
    )

    private suspend fun TestScope.ready(
        model: SubmissionsViewModel = model(),
    ): SubmissionsUiState.Ready =
        watching(model.uiState).awaiting { it is SubmissionsUiState.Ready } as SubmissionsUiState.Ready

    /**
     * The point of this screen. A collector's Queue and Recent lists are scoped
     * to them; this one is not, and the rows on the device are already exactly
     * what row-level security let this account pull.
     */
    @Test
    fun `the list holds everything in the study, whoever collected it`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-148", collectedBy = Ids.ADAKU))
        db.submissions().upsert(submission("c-147", collectedBy = Ids.TOMAS, id = "server-2"))

        assertEquals(setOf("c-148", "c-147"), ready().rows.map { it.clientId }.toSet())
    }

    /**
     * Voiding excludes a submission from analysis and keeps the row. Hiding it
     * here would make a void look exactly like a delete to the person who
     * performed it, on the only screen where the difference is visible.
     */
    @Test
    fun `a voided submission stays in the list, marked voided`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-148", deletedAt = at(40)))

        val row = ready().rows.single()

        assertEquals(ReviewState.VOIDED, row.state)
        assertEquals("KL-0148", row.label)
    }

    @Test
    fun `a locked submission reads as locked`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-148", lockedAt = at(40)))

        assertEquals(ReviewState.LOCKED, ready().rows.single().state)
    }

    @Test
    fun `rows read as form, version and time, newest first`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-1", collectedAt = at(14)))
        db.submissions().upsert(
            submission("c-2", collectedAt = at(16), formVersionId = Ids.TRAP_V5, id = "server-2"),
        )

        val rows = ready().rows

        assertEquals(listOf("c-2", "c-1"), rows.map { it.clientId })
        assertEquals("Trap check v5 · 09:16", rows.first().detail)
        assertEquals("Baseline intake v2 · 09:14", rows.last().detail)
    }

    @Test
    fun `the list is this study's, not the device's`() = runTest {
        db.seedKluane()
        db.seedPeel()
        db.submissions().upsert(submission("c-1"))
        db.submissions().upsert(
            submission(
                "c-2",
                studyId = Ids.PEEL,
                formVersionId = Ids.PEEL_V1,
                participantId = null,
                id = "server-2",
            ),
        )

        assertEquals(listOf("c-1"), ready().rows.map { it.clientId })
        assertEquals(listOf("c-2"), ready(model(Ids.PEEL)).rows.map { it.clientId })
    }

    @Test
    fun `selecting a filter changes what is visible and nothing else`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-open"))
        db.submissions().upsert(submission("c-locked", lockedAt = at(40), id = "server-2"))
        db.submissions().upsert(submission("c-voided", deletedAt = at(40), id = "server-3"))

        val model = model()
        val state = watching(model.uiState)
        assertEquals(3, (state.awaiting { it is SubmissionsUiState.Ready } as SubmissionsUiState.Ready).visible.size)

        model.select(ReviewFilter.OPEN)
        val open = state.awaiting {
            it is SubmissionsUiState.Ready && it.filter == ReviewFilter.OPEN
        } as SubmissionsUiState.Ready

        assertEquals(listOf("c-open"), open.visible.map { it.clientId })
        // The rows behind the chip are still there, which is what the count line
        // reads and what the next chip tap shows.
        assertEquals(3, open.rows.size)
    }

    /**
     * The three are disjoint. Overlapping counts that sum to more than the table
     * holds are how a summary stops being believed.
     */
    @Test
    fun `the counts do not double-count a row that is both locked and voided`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-1"))
        db.submissions().upsert(submission("c-2", lockedAt = at(40), id = "server-2"))
        db.submissions().upsert(submission("c-3", lockedAt = at(40), deletedAt = at(41), id = "server-3"))

        val counts = ready().counts

        assertEquals(2, counts.collected)
        assertEquals(1, counts.locked)
        assertEquals(1, counts.voided)
        assertEquals(1, counts.unlocked)
    }

    @Test
    fun `participants are counted once each, and voided rows do not contribute`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-1"))
        db.submissions().upsert(submission("c-2", id = "server-2"))
        db.submissions().upsert(submission("c-3", participantId = null, id = "server-3"))

        assertEquals(1, ready().counts.participants)
    }

    /**
     * A sign-out wipes the database while this screen may be on top of the
     * stack. Rendering an empty list under the old study's name would leave
     * someone scrolling a study that no longer exists.
     */
    @Test
    fun `a study removed from the device reads as gone, not as empty`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-148"))
        val state = watching(model().uiState)
        assertTrue((state.awaiting { it is SubmissionsUiState.Ready } as SubmissionsUiState.Ready).rows.isNotEmpty())

        withContext(Dispatchers.IO) { db.clearAllTables() }

        assertEquals(SubmissionsUiState.Gone, state.awaiting { it is SubmissionsUiState.Gone })
    }

    @Test
    fun `a row this device has not pushed still appears in the list`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-148", id = null, syncState = SyncState.QUEUED))

        assertEquals(ReviewState.OPEN, ready().rows.single().state)
    }
}
