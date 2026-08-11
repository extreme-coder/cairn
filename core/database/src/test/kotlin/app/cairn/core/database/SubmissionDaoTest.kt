package app.cairn.core.database

import androidx.sqlite.SQLiteException
import app.cairn.core.model.SyncState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class SubmissionDaoTest {

    private lateinit var db: CairnDatabase

    @Before
    fun setUp() = runTest {
        db = testDatabase()
        db.seedReferenceData()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `replaying a queue upserts rather than duplicating`() = runTest {
        val queued = submission()
        db.submissions().upsert(queued)
        db.submissions().upsert(queued)

        val rows = db.submissions().observeForStudy(Ids.STUDY).first()
        assertEquals(1, rows.size)
    }

    @Test
    fun `the same client id from a different collector is a different submission`() = runTest {
        val clientId = "aaaaaaaa-0000-0000-0000-000000000009"
        db.submissions().upsert(submission(collectedBy = Ids.ADAKU, clientId = clientId))
        db.submissions().upsert(submission(collectedBy = Ids.TOMAS, clientId = clientId))

        assertEquals(2, db.submissions().observeForStudy(Ids.STUDY).first().size)
    }

    @Test
    fun `a submission is queued before the server has given it an id`() = runTest {
        db.submissions().upsert(submission())

        val row = db.submissions().observe(Ids.ADAKU, "aaaaaaaa-0000-0000-0000-000000000001").first()
        assertNull(row?.id)
        assertEquals(SyncState.QUEUED, row?.syncState)
    }

    @Test
    fun `marking uploaded takes the server id and the server timestamp`() = runTest {
        db.submissions().upsert(submission())
        val serverId = "99999999-9999-9999-9999-999999999999"
        val serverTime = Instant.parse("2026-07-01T10:15:30Z")

        db.submissions().markUploaded(
            collectedBy = Ids.ADAKU,
            clientId = "aaaaaaaa-0000-0000-0000-000000000001",
            serverId = serverId,
            updatedAt = serverTime,
        )

        val row = db.submissions().observe(Ids.ADAKU, "aaaaaaaa-0000-0000-0000-000000000001").first()
        assertEquals(serverId, row?.id)
        assertEquals(serverTime, row?.updatedAt)
        assertEquals(SyncState.UPLOADED, row?.syncState)
        assertNull(row?.pendingSince)
    }

    @Test
    fun `the unsynced count follows the queue down to zero`() = runTest {
        db.submissions().upsert(submission(clientId = "aaaaaaaa-0000-0000-0000-00000000000a"))
        db.submissions().upsert(submission(clientId = "aaaaaaaa-0000-0000-0000-00000000000b"))
        assertEquals(2, db.submissions().observeUnsyncedCount().first())

        db.submissions().markUploaded(
            Ids.ADAKU,
            "aaaaaaaa-0000-0000-0000-00000000000a",
            "99999999-9999-9999-9999-99999999990a",
            at(40),
        )
        assertEquals(1, db.submissions().observeUnsyncedCount().first())
    }

    @Test
    fun `the worker drains the queue oldest first`() = runTest {
        db.submissions().upsert(
            submission(clientId = "aaaaaaaa-0000-0000-0000-00000000001a", pendingSince = at(90)),
        )
        db.submissions().upsert(
            submission(clientId = "aaaaaaaa-0000-0000-0000-00000000001b", pendingSince = at(10)),
        )

        val queue = db.submissions().awaiting()
        assertEquals(
            listOf("aaaaaaaa-0000-0000-0000-00000000001b", "aaaaaaaa-0000-0000-0000-00000000001a"),
            queue.map { it.clientId },
        )
    }

    @Test
    fun `a failed push is retried on the next run`() = runTest {
        db.submissions().upsert(submission())
        db.submissions().markFailed(Ids.ADAKU, "aaaaaaaa-0000-0000-0000-000000000001")
        assertTrue(db.submissions().awaiting().isEmpty())

        db.submissions().requeueFailed()
        assertEquals(1, db.submissions().awaiting().size)
    }

    @Test
    fun `a voided submission leaves the feed but stays on disk`() = runTest {
        db.submissions().upsert(submission(deletedAt = at(60)))

        assertTrue(db.submissions().observeForStudy(Ids.STUDY).first().isEmpty())
        val row = db.submissions().observe(Ids.ADAKU, "aaaaaaaa-0000-0000-0000-000000000001").first()
        assertTrue(row!!.isVoided)
    }

    @Test
    fun `a collector's feed holds only their own rows`() = runTest {
        db.submissions().upsert(submission(collectedBy = Ids.ADAKU, clientId = "c-1"))
        db.submissions().upsert(submission(collectedBy = Ids.TOMAS, clientId = "c-2"))

        val adaku = db.submissions().observeForCollector(Ids.STUDY, Ids.ADAKU).first()
        assertEquals(listOf(Ids.ADAKU), adaku.map { it.collectedBy })
    }

    @Test
    fun `a submission cannot reference a form version the device has not pulled`() = runTest {
        val thrown = runCatching {
            db.submissions().upsert(
                submission(formVersionId = "77777777-7777-7777-7777-777777777777"),
            )
        }.exceptionOrNull()

        assertTrue(
            "expected a foreign key violation, got: $thrown",
            thrown is SQLiteException &&
                thrown.message.orEmpty().contains("FOREIGN KEY", ignoreCase = true),
        )
    }

    @Test
    fun `another study's submissions stay out of the feed`() = runTest {
        db.seedSecondStudy()
        db.submissions().upsert(submission(clientId = "here"))
        db.submissions().upsert(
            submission(
                clientId = "elsewhere",
                studyId = Ids.OTHER_STUDY,
                formVersionId = Ids.OTHER_VERSION,
                participantId = Ids.OTHER_PARTICIPANT,
            ),
        )

        assertEquals(
            listOf("here"),
            db.submissions().observeForStudy(Ids.STUDY).first().map { it.clientId },
        )
        assertEquals(
            listOf("elsewhere"),
            db.submissions().observeForStudy(Ids.OTHER_STUDY).first().map { it.clientId },
        )
    }

    /**
     * The queue is deliberately not study-scoped. A collector working two studies
     * in one trip has one queue, and it drains in the order things were collected.
     */
    @Test
    fun `the queue spans every study on the device`() = runTest {
        db.seedSecondStudy()
        db.submissions().upsert(submission(clientId = "here", pendingSince = at(20)))
        db.submissions().upsert(
            submission(
                clientId = "elsewhere",
                studyId = Ids.OTHER_STUDY,
                formVersionId = Ids.OTHER_VERSION,
                participantId = Ids.OTHER_PARTICIPANT,
                pendingSince = at(10),
            ),
        )

        assertEquals(listOf("elsewhere", "here"), db.submissions().awaiting().map { it.clientId })
    }

    @Test
    fun `a submission without a participant is still valid`() = runTest {
        db.submissions().upsert(submission(participantId = null))

        val row = db.submissions().observe(Ids.ADAKU, "aaaaaaaa-0000-0000-0000-000000000001").first()
        assertNull(row?.participantId)
    }

    @Test
    fun `the feed is newest first`() = runTest {
        db.submissions().upsert(submission(clientId = "older", collectedAt = at(10)))
        db.submissions().upsert(submission(clientId = "newer", collectedAt = at(90)))

        assertEquals(
            listOf("newer", "older"),
            db.submissions().observeForStudy(Ids.STUDY).first().map { it.clientId },
        )
    }

    @Test
    fun `an amendment pulled from the server overwrites the local row`() = runTest {
        db.submissions().upsert(submission(mass = 268.0))
        db.submissions().upsert(
            submission(
                id = "99999999-9999-9999-9999-999999999999",
                mass = 271.5,
                updatedAt = at(120),
                syncState = SyncState.UPLOADED,
                pendingSince = null,
            ),
        )

        val rows = db.submissions().observeForStudy(Ids.STUDY).first()
        assertEquals(1, rows.size)
        assertEquals(at(120), rows.single().updatedAt)
        assertEquals(SyncState.UPLOADED, rows.single().syncState)
    }

    @Test
    fun `a locked submission is readable and flagged`() = runTest {
        db.submissions().upsert(submission(lockedAt = at(120)))

        val row = db.submissions().observe(Ids.ADAKU, "aaaaaaaa-0000-0000-0000-000000000001").first()
        assertTrue(row!!.isLocked)
    }
}
