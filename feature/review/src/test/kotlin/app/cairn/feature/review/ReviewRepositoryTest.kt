package app.cairn.feature.review

import app.cairn.core.database.CairnDatabase
import app.cairn.core.model.SyncState
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
 * The one place in the app that writes to the server directly, against a real
 * in-memory database and a fake server.
 *
 * What is being proved is that the local copy is caught up from what the server
 * *echoed*, and only then — a refusal or a dead network must leave the row
 * exactly as it was, because the alternative is a screen that says "Locked"
 * about a submission nobody locked.
 */
@RunWith(RobolectricTestRunner::class)
class ReviewRepositoryTest {

    private lateinit var db: CairnDatabase
    private lateinit var remote: FakeRemote
    private lateinit var repository: ReviewRepository

    @Before
    fun setUp() {
        db = testDatabase()
        remote = FakeRemote()
        repository = ReviewRepository(db.submissions(), remote)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seed(
        lockedAt: Instant? = null,
        deletedAt: Instant? = null,
        syncState: SyncState = SyncState.UPLOADED,
    ) {
        db.seedKluane()
        db.submissions().upsert(
            submission("c-148", lockedAt = lockedAt, deletedAt = deletedAt, syncState = syncState),
        )
    }

    private suspend fun stored() = db.submissions().observeDetail(Ids.ADAKU, "c-148").first()!!

    @Test
    fun `a lock writes the server's own values into the local row`() = runTest {
        seed()
        remote.lockedAt = "2026-08-13T10:31:00+00:00"

        assertEquals(ReviewOutcome.Applied, repository.lock(Ids.ADAKU, "c-148", Ids.SERVER_ID, at(90)))

        val row = stored()
        assertEquals(Instant.parse("2026-08-13T10:31:00Z"), row.lockedAt)
        // The server's `updated_at`, not the device's and not the one sent.
        assertEquals(Instant.parse("2026-08-13T10:31:02.456Z"), row.updatedAt)
    }

    @Test
    fun `the request carries the server id and a timestamp, and nothing else`() = runTest {
        seed()

        repository.lock(Ids.ADAKU, "c-148", Ids.SERVER_ID, at(90))

        assertEquals(listOf("lock(${Ids.SERVER_ID}, 2026-08-13T10:30:00Z)"), remote.calls)
    }

    @Test
    fun `a void sets deleted_at locally and a restore clears it`() = runTest {
        seed()

        repository.void(Ids.ADAKU, "c-148", Ids.SERVER_ID, at(90))
        assertNotNull(stored().deletedAt)

        repository.restore(Ids.ADAKU, "c-148", Ids.SERVER_ID)
        assertNull(stored().deletedAt)
    }

    /**
     * The silent-UPDATE trap arriving at the device. The server changed nothing,
     * so the device must change nothing — a local row that says "Locked" while
     * the server's says otherwise is a divergence nothing would ever repair,
     * because a pull would find no newer `updated_at` to bring down.
     */
    @Test
    fun `a refusal leaves the local row exactly as it was`() = runTest {
        seed()
        remote.refusal = "no row was changed"

        val outcome = repository.lock(Ids.ADAKU, "c-148", Ids.SERVER_ID, at(90))

        assertEquals(ReviewOutcome.Refused("no row was changed"), outcome)
        assertNull(stored().lockedAt)
        assertEquals(at(14), stored().updatedAt)
    }

    @Test
    fun `an unreachable server is a different answer from a refusal, and also changes nothing`() = runTest {
        seed()
        remote.offline = true

        assertEquals(ReviewOutcome.Offline, repository.void(Ids.ADAKU, "c-148", Ids.SERVER_ID, at(90)))
        assertNull(stored().deletedAt)
    }

    /**
     * **The clobber guard.** `sync_state` must not become `QUEUED`, or the next
     * push would send this device's copy of the whole payload back up under the
     * collector's `(collected_by, client_id)` key — discarding any amendment
     * made between the pull and the lock, and calling it last-write-wins.
     */
    @Test
    fun `applying a review does not put the row back into the push queue`() = runTest {
        seed()

        repository.lock(Ids.ADAKU, "c-148", Ids.SERVER_ID, at(90))

        assertEquals(SyncState.UPLOADED, stored().syncState)
        assertTrue(db.submissions().awaiting().isEmpty())
        assertTrue(db.submissions().pendingKeys().isEmpty())
    }

    /**
     * A build with no server configured. Unreachable in practice — the Sign in
     * screen refuses first — but it is what lets `:app` build its graph without
     * branching, and answering `Applied` here would be a lie.
     */
    @Test
    fun `with no server configured every action is offline`() = runTest {
        seed()
        val repository = ReviewRepository(db.submissions(), remote = null)

        assertEquals(ReviewOutcome.Offline, repository.lock(Ids.ADAKU, "c-148", Ids.SERVER_ID, at(90)))
        assertEquals(ReviewOutcome.Offline, repository.restore(Ids.ADAKU, "c-148", Ids.SERVER_ID))
        assertNull(stored().lockedAt)
    }

    @Test
    fun `a restore sends a null rather than omitting the column`() = runTest {
        seed(deletedAt = at(40))

        repository.restore(Ids.ADAKU, "c-148", Ids.SERVER_ID)

        assertEquals(listOf("setVoided(${Ids.SERVER_ID}, null)"), remote.calls)
    }
}
