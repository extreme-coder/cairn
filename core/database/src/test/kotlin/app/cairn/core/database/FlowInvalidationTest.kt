package app.cairn.core.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The architectural claim, tested: a screen observes Room and Room alone.
 *
 * If a `Flow` returned by a DAO did not re-emit when the sync layer wrote to the
 * table underneath it, every screen would need to be told when to refresh — and
 * "Room is the single source of truth" would be a diagram rather than a fact.
 *
 * These use `runBlocking`, not `runTest`. Invalidation is delivered on Room's own
 * threads, and `runTest`'s virtual clock would let `withTimeout` expire before
 * any real work happened.
 */
@RunWith(RobolectricTestRunner::class)
class FlowInvalidationTest {

    private lateinit var db: CairnDatabase

    @Before
    fun setUp() = runBlocking {
        db = testDatabase()
        db.seedReferenceData()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `a study flow re-emits when the row is rewritten underneath it`() = runBlocking {
        val seen = Channel<String?>(Channel.UNLIMITED)
        val collector = launch(Dispatchers.IO) {
            db.studies().observe(Ids.STUDY).collect { seen.send(it?.name) }
        }

        try {
            assertEquals("Kestrel breeding survey", withTimeout(TIMEOUT) { seen.receive() })

            db.studies().upsert(listOf(study(name = "Kestrel breeding survey (2027)")))

            assertEquals("Kestrel breeding survey (2027)", withTimeout(TIMEOUT) { seen.receive() })
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun `a submission feed re-emits when the queue drains`() = runBlocking {
        val seen = Channel<Int>(Channel.UNLIMITED)
        val collector = launch(Dispatchers.IO) {
            db.submissions().observeUnsyncedCount().collect { seen.send(it) }
        }

        try {
            assertEquals(0, withTimeout(TIMEOUT) { seen.receive() })

            db.submissions().upsert(submission())
            assertEquals(1, withTimeout(TIMEOUT) { seen.receive() })

            db.submissions().markUploaded(
                collectedBy = Ids.ADAKU,
                clientId = "aaaaaaaa-0000-0000-0000-000000000001",
                serverId = "99999999-9999-9999-9999-999999999999",
                updatedAt = at(45),
            )
            assertEquals(0, withTimeout(TIMEOUT) { seen.receive() })
        } finally {
            collector.cancel()
        }
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
