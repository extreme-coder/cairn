package app.cairn.feature.collect

import app.cairn.core.database.CairnDatabase
import app.cairn.core.model.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QueueViewModelTest {

    private lateinit var db: CairnDatabase
    private var syncRequests = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = testDatabase()
        syncRequests = 0
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun queue(userId: String = Ids.ADAKU) = QueueViewModel(
        submissions = db.submissions(),
        userId = userId,
        requestSync = { syncRequests++ },
        now = { at(20) },
        zone = UTC,
    )

    private suspend fun TestScope.state(userId: String = Ids.ADAKU): QueueUiState =
        watching(queue(userId).uiState).awaiting { it.loaded }

    @Test
    fun `queued and failed are separate sections`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-1", syncState = SyncState.QUEUED))
        db.submissions().upsert(submission("c-2", syncState = SyncState.FAILED))
        db.submissions().upsert(submission("c-3", syncState = SyncState.UPLOADED))

        val queue = state()

        assertEquals(listOf("c-1"), queue.queued.map { it.clientId })
        assertEquals(listOf("c-2"), queue.failed.map { it.clientId })
        assertEquals(1, queue.counts.queued)
        assertEquals(1, queue.counts.failed)
        assertEquals(1, queue.counts.uploaded)
        assertEquals(2, queue.counts.pending)
    }

    @Test
    fun `a row reads as its participant code, form, version and time`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-1", collectedAt = at(14)))

        val row = state().queued.single()

        assertEquals("KL-0148", row.label)
        assertEquals("Baseline intake v3 · 09:14", row.detail)
    }

    /**
     * Uploaded rows are not fetched until asked for. A device three months into
     * a season holds thousands of them and none need anything done to them.
     */
    @Test
    fun `uploaded rows are absent until they are asked for`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-1", syncState = SyncState.UPLOADED))
        val viewModel = queue()
        val state = watching(viewModel.uiState)

        assertTrue(state.awaiting { it.counts.uploaded == 1 }.uploaded.isEmpty())

        viewModel.toggleUploaded()

        val showing = state.awaiting { it.showingUploaded && it.uploaded.isNotEmpty() }
        assertEquals(listOf("c-1"), showing.uploaded.map { it.clientId })
    }

    @Test
    fun `hiding uploaded rows drops them again`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-1", syncState = SyncState.UPLOADED))
        val viewModel = queue()
        val state = watching(viewModel.uiState)
        state.awaiting { it.counts.uploaded == 1 }

        viewModel.toggleUploaded()
        state.awaiting { it.showingUploaded && it.uploaded.isNotEmpty() }
        viewModel.toggleUploaded()

        val hidden = state.awaiting { !it.showingUploaded }
        assertTrue(hidden.uploaded.isEmpty())
    }

    /**
     * The invariant that makes "Upload now" honest. A `FAILED` row is not in
     * `awaiting()`, so a sync alone would walk straight past exactly the rows
     * the collector pressed the button for and then report success.
     */
    @Test
    fun `upload now re-queues the failed rows before asking for a sync`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-1", syncState = SyncState.FAILED))
        val viewModel = queue()
        watching(viewModel.uiState).awaiting { it.counts.failed == 1 }

        viewModel.uploadNow()

        assertEquals(
            SyncState.QUEUED,
            db.submissions().observe(Ids.ADAKU, "c-1").first()!!.syncState,
        )
        assertEquals(1, syncRequests)
    }

    @Test
    fun `there is nothing to upload when nothing is waiting`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-1", syncState = SyncState.UPLOADED))

        assertFalse(state().canUpload)
    }

    @Test
    fun `there is something to upload when a row has failed`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-1", syncState = SyncState.FAILED))

        assertTrue(state().canUpload)
    }

    @Test
    fun `the queue is this collector's own`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-1", collectedBy = Ids.ADAKU))
        db.submissions().upsert(submission("c-2", collectedBy = Ids.TOMAS))

        assertEquals(listOf("c-1"), state(Ids.ADAKU).queued.map { it.clientId })
        assertEquals(listOf("c-2"), state(Ids.TOMAS).queued.map { it.clientId })
    }

    @Test
    fun `a device with nothing on it is empty rather than a list of zeroes`() = runTest {
        db.seedKluane()

        assertTrue(state().isEmpty)
    }

    @Test
    fun `the queue spans studies`() = runTest {
        db.seedKluane()
        db.seedPeel()
        db.submissions().upsert(submission("c-1"))
        db.submissions().upsert(
            submission("c-2", studyId = Ids.PEEL, formVersionId = Ids.PEEL_V1, participantId = null),
        )

        assertEquals(2, state().queued.size)
    }

    /** A voided submission is not waiting for anything and must not be counted as if it were. */
    @Test
    fun `a voided submission is not in the queue`() = runTest {
        db.seedKluane()
        db.submissions().upsert(submission("c-1", deletedAt = at(30)))

        val queue = state()

        assertTrue(queue.queued.isEmpty())
        assertEquals(0, queue.counts.pending)
    }
}
