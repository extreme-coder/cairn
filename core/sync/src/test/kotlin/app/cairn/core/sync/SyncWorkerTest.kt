package app.cairn.core.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import app.cairn.core.database.CairnDatabase
import app.cairn.core.model.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Instant

/**
 * The worker's own job is small: run the engine and turn what happens into a
 * [ListenableWorker.Result]. These tests are about that translation, because
 * getting it wrong is silent — a failure reported as success is a sync that
 * never retries.
 */
@RunWith(RobolectricTestRunner::class)
class SyncWorkerTest {

    private lateinit var db: CairnDatabase
    private lateinit var remote: FakeRemote
    private lateinit var log: RecordingLog

    @Before
    fun setUp() {
        db = testDatabase()
        remote = FakeRemote()
        log = RecordingLog()
        SyncDependencies.install(SyncEngine(db, remote, InMemoryCursors()), log)
    }

    @After
    fun tearDown() {
        SyncDependencies.engine = null
        SyncDependencies.log = null
        db.close()
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private suspend fun run(): ListenableWorker.Result =
        TestListenableWorkerBuilder<SyncWorker>(context()).build().doWork()

    /**
     * A submission cannot be queued before the study it belongs to is on the
     * device — the foreign key is deferred, not absent, and it is checked at
     * commit. So the first run is the one that makes capture possible at all.
     */
    @Test
    fun `the first run pulls reference data and the next one pushes what was captured`() = runTest {
        remote.seedStudy()

        val first = run()
        assertTrue(first is ListenableWorker.Result.Success)
        assertEquals(
            5,
            (first as ListenableWorker.Result.Success).outputData.getInt(SyncWorker.KEY_PULLED, -1),
        )

        db.submissions().upsert(queued())
        val second = run()

        assertTrue(second is ListenableWorker.Result.Success)
        val data = (second as ListenableWorker.Result.Success).outputData
        assertEquals(1, data.getInt(SyncWorker.KEY_PUSHED, -1))
        assertEquals(0, data.getInt(SyncWorker.KEY_FAILED, -1))
    }

    @Test
    fun `the submission really is on the server afterwards`() = runTest {
        remote.seedStudy()
        run()
        db.submissions().upsert(queued())

        run()

        assertEquals(1, remote.submissions.size)
        assertEquals(
            SyncState.UPLOADED,
            db.submissions().observe(Ids.ADAKU, "cccccccc-0000-0000-0000-000000000001").first()!!.syncState,
        )
    }

    /** No network is the normal state of this app, not an error worth burning a failure on. */
    @Test
    fun `an offline run asks to be retried`() = runTest {
        remote.seedStudy()
        remote.offline = true

        assertTrue(run() is ListenableWorker.Result.Retry)
    }

    /**
     * Signed out is not a failure and not worth retrying — there is nothing to
     * sync until someone signs in, and a retry loop would just burn battery.
     */
    @Test
    fun `a signed-out run succeeds quietly`() = runTest {
        remote.currentUser = null

        assertTrue(run() is ListenableWorker.Result.Success)
    }

    /**
     * Retrying a stalled cursor cannot help: the next run asks the same question
     * and gets the same page back. Backoff would turn a permanent problem into a
     * quiet one.
     */
    @Test
    fun `a stalled cursor fails rather than retrying forever`() = runTest {
        remote.studies += studyDto()
        repeat(4) { i ->
            remote.participants += participantDto(
                id = "44444444-0000-0000-0000-00000000000$i",
                code = "K-30$i",
                updatedAt = ts(12),
            )
        }
        SyncDependencies.install(SyncEngine(db, remote, InMemoryCursors(), pageSize = 3))

        val result = run()

        assertTrue(result is ListenableWorker.Result.Failure)
        val reason = (result as ListenableWorker.Result.Failure).outputData
            .getString(SyncWorker.KEY_REASON)
        assertTrue(reason!!.contains("participants"))
    }

    @Test
    fun `a worker with nothing wired up fails instead of throwing`() = runTest {
        SyncDependencies.engine = null

        assertTrue(run() is ListenableWorker.Result.Failure)
    }

    // ---- What Settings reads: when this device last synced ----

    @Test
    fun `a clean run records when it happened`() = runTest {
        remote.seedStudy()

        run()

        assertNotNull(log.lastSyncedAt.first())
    }

    /**
     * "Last synced" is read as "everything is up to date as of", so a run that
     * could not send the queue must not set it. Otherwise Settings reassures a
     * collector at the exact moment their observations are stuck on the device.
     */
    @Test
    fun `a run that could not upload does not claim to have synced`() = runTest {
        remote.seedStudy()
        run()
        db.submissions().upsert(queued())
        log.forget()
        remote.offline = true

        assertTrue(run() is ListenableWorker.Result.Retry)
        assertNull(log.lastSyncedAt.first())
    }

    @Test
    fun `a signed-out run records nothing`() = runTest {
        remote.currentUser = null

        assertTrue(run() is ListenableWorker.Result.Success)
        assertNull(log.lastSyncedAt.first())
    }

    private class RecordingLog : SyncLog {
        private val state = MutableStateFlow<Instant?>(null)
        override val lastSyncedAt: Flow<Instant?> = state
        override suspend fun record(at: Instant) {
            state.value = at
        }

        fun forget() {
            state.value = null
        }
    }
}
