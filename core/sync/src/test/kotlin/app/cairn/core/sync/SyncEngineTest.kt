package app.cairn.core.sync

import app.cairn.core.database.CairnDatabase
import app.cairn.core.model.SyncState
import app.cairn.core.network.RemoteDataSource
import app.cairn.core.network.SubmissionDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Instant

/**
 * One sync run against a fake server and a real SQLite database.
 *
 * The database is the one that ships — bundled native SQLite through Room — so
 * deferred foreign keys, unique indexes and `@Upsert`'s conflict behaviour are
 * the real thing rather than a model of it. Only the network is faked.
 */
@RunWith(RobolectricTestRunner::class)
class SyncEngineTest {

    private lateinit var db: CairnDatabase
    private lateinit var remote: FakeRemote
    private lateinit var cursors: InMemoryCursors

    @Before
    fun setUp() {
        db = testDatabase()
        remote = FakeRemote()
        cursors = InMemoryCursors()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun engine(pageSize: Int = 500) = SyncEngine(db, remote, cursors, pageSize)

    private suspend fun rows() = db.submissions().observeForStudy(Ids.STUDY).first()

    private suspend fun row(clientId: String = "cccccccc-0000-0000-0000-000000000001") =
        db.submissions().observe(Ids.ADAKU, clientId).first()

    // ---- pull ------------------------------------------------------------

    @Test
    fun `a first sync brings a study and its form down`() = runTest {
        remote.seedStudy()

        val outcome = engine().sync()

        assertEquals("Kestrel breeding survey", db.studies().observe(Ids.STUDY).first()?.name)
        assertEquals(listOf(Ids.FORM), db.forms().observeForms(Ids.STUDY).first().map { it.id })
        assertEquals(1, db.forms().observeCurrentVersion(Ids.FORM).first()?.version)
        assertEquals(5, outcome.pulledTotal)
    }

    @Test
    fun `a second sync with nothing changed fetches nothing`() = runTest {
        remote.seedStudy()
        engine().sync()

        val outcome = engine().sync()

        assertEquals(0, outcome.pulledTotal)
    }

    @Test
    fun `only rows newer than the cursor come down on the next run`() = runTest {
        remote.seedStudy()
        engine().sync()

        remote.participants += participantDto(id = "44444444-0000-0000-0000-000000000002", code = "K-015", updatedAt = ts(20))
        val outcome = engine().sync()

        assertEquals(1, outcome.pulledTotal)
        assertEquals(2, db.participants().observeAll(Ids.STUDY).first().size)
    }

    @Test
    fun `the cursor persisted is the server's exact string, microseconds and all`() = runTest {
        remote.seedStudy()
        remote.participants.clear()
        remote.participants += participantDto(updatedAt = ts(3, micros = 654321))

        engine().sync()

        assertEquals(ts(3, 654321), cursors.peek(SyncCursors.participants(Ids.STUDY)))
    }

    @Test
    fun `a study the device does not hold is not queried for children`() = runTest {
        remote.studies += studyDto()
        remote.forms += formDto(studyId = Ids.OTHER_STUDY, id = "unreachable")

        engine().sync()

        assertEquals(emptyList<String>(), db.forms().formIds(Ids.OTHER_STUDY))
    }

    @Test
    fun `pagination drains every page`() = runTest {
        remote.studies += studyDto()
        repeat(7) { i ->
            remote.participants += participantDto(
                id = "44444444-0000-0000-0000-00000000000$i",
                code = "K-10$i",
                updatedAt = ts(10, micros = i),
            )
        }

        engine(pageSize = 3).sync()

        assertEquals(7, db.participants().observeAll(Ids.STUDY).first().size)
    }

    /**
     * The bulk-publish case. Ten versions written by one statement share one
     * `updated_at`, and the page cuts through the middle of them.
     */
    @Test
    fun `a tie group spanning a page boundary is not skipped`() = runTest {
        remote.studies += studyDto()
        remote.forms += formDto()
        remote.versions += versionDto(id = "33333333-0000-0000-0000-000000000001", version = 1, updatedAt = ts(9))
        remote.versions += versionDto(id = "33333333-0000-0000-0000-000000000002", version = 2, updatedAt = ts(10))
        remote.versions += versionDto(id = "33333333-0000-0000-0000-000000000003", version = 3, updatedAt = ts(11))
        remote.versions += versionDto(id = "33333333-0000-0000-0000-000000000004", version = 4, updatedAt = ts(11))

        engine(pageSize = 3).sync()

        assertEquals(4, db.forms().versionIds(listOf(Ids.FORM)).size)
    }

    @Test
    fun `a full page sharing one timestamp stalls instead of losing the rest`() = runTest {
        remote.studies += studyDto()
        repeat(4) { i ->
            remote.participants += participantDto(
                id = "44444444-0000-0000-0000-00000000000$i",
                code = "K-20$i",
                updatedAt = ts(12),
            )
        }

        val failure = runCatching { engine(pageSize = 3).sync() }.exceptionOrNull()

        assertTrue(failure is SyncException.CursorStalled)
        assertNull(cursors.peek(SyncCursors.participants(Ids.STUDY)))
    }

    // ---- the scope-widening trap ----------------------------------------

    /**
     * `form_versions` is fetched by an explicit list of form ids, so its cursor
     * is only valid for the list it was taken under. Add a form later and its
     * versions are older than the cursor — without voiding the cursor they would
     * never be fetched, not once, because a cursor only moves forward.
     */
    @Test
    fun `adding a form re-pulls versions the old cursor would have hidden forever`() = runTest {
        remote.seedStudy()
        engine().sync()

        remote.forms += formDto(id = Ids.FORM_2, code = "recapture", updatedAt = ts(21))
        remote.versions += versionDto(
            id = Ids.VERSION_2,
            formId = Ids.FORM_2,
            version = 1,
            updatedAt = ts(2),
        )

        engine().sync()

        assertEquals(
            setOf(Ids.VERSION_1, Ids.VERSION_2),
            db.forms().versionIds(listOf(Ids.FORM, Ids.FORM_2)).toSet(),
        )
    }

    @Test
    fun `an unchanged form list keeps its cursor`() = runTest {
        remote.seedStudy()
        engine().sync()
        val afterFirst = cursors.peek(SyncCursors.formVersions(Ids.STUDY))

        engine().sync()

        assertEquals(afterFirst, cursors.peek(SyncCursors.formVersions(Ids.STUDY)))
        assertEquals(ts(2), afterFirst)
    }

    // ---- the silent-drop trap -------------------------------------------

    /**
     * Room's `@Upsert` discards a row with a fresh primary key that collides on a
     * unique index, and raises nothing. Undetected, the cursor would advance past
     * a row that never landed and it would never be offered again.
     */
    @Test
    fun `a delta that does not land fails the run and leaves the cursor put`() = runTest {
        remote.seedStudy()
        engine().sync()
        val before = cursors.peek(SyncCursors.forms(Ids.STUDY))

        remote.forms += formDto(id = "22222222-0000-0000-0000-00000000dead", code = "baseline_intake", updatedAt = ts(22))

        val failure = runCatching { engine().sync() }.exceptionOrNull()

        assertTrue(failure is SyncException.DeltaIncomplete)
        assertTrue(failure!!.message!!.contains("forms"))
        assertEquals(before, cursors.peek(SyncCursors.forms(Ids.STUDY)))
    }

    @Test
    fun `a failed delta rolls back the rows that did land`() = runTest {
        remote.seedStudy()
        engine().sync()

        remote.forms += formDto(id = "22222222-0000-0000-0000-00000000aaaa", code = "recapture", updatedAt = ts(22))
        remote.forms += formDto(id = "22222222-0000-0000-0000-00000000dead", code = "baseline_intake", updatedAt = ts(23))

        runCatching { engine().sync() }

        assertEquals(listOf(Ids.FORM), db.forms().formIds(Ids.STUDY))
    }

    // ---- push ------------------------------------------------------------

    @Test
    fun `a queued submission reaches the server and is marked uploaded`() = runTest {
        remote.seedStudy()
        engine().sync()
        db.submissions().upsert(queued())

        val outcome = engine().sync()

        assertEquals(1, outcome.pushed)
        assertEquals(1, remote.submissions.size)
        val stored = row()!!
        assertEquals(SyncState.UPLOADED, stored.syncState)
        assertNotNull(stored.id)
        assertNull(stored.pendingSince)
    }

    /**
     * Replay is the whole reason `client_id` is minted when a form opens rather
     * than when it saves. Here the first push reaches the server and the response
     * never gets back, so the row is still `QUEUED` and goes again.
     */
    @Test
    fun `replaying a push that was never acknowledged leaves one row, not two`() = runTest {
        remote.seedStudy()
        engine().sync()
        db.submissions().upsert(queued())

        runCatching { SyncEngine(db, LosesTheResponse(remote), cursors).sync() }
        assertEquals(SyncState.QUEUED, row()!!.syncState)
        assertEquals(1, remote.submissions.size)

        engine().sync()

        assertEquals(1, remote.submissions.size)
        assertEquals(1, rows().size)
        assertEquals(SyncState.UPLOADED, row()!!.syncState)
    }

    /**
     * The device clock is not trustworthy — the fixture's is set to 2099. What
     * ends up in `updated_at` has to be the server's value, because that column
     * is what last-write-wins compares.
     */
    @Test
    fun `the server's updated_at replaces the device's, however skewed the clock`() = runTest {
        remote.seedStudy()
        engine().sync()
        db.submissions().upsert(queued(updatedAt = Instant.parse("2099-01-01T00:00:00Z")))

        engine().sync()

        val stored = row()!!
        assertTrue(stored.updatedAt < Instant.parse("2030-01-01T00:00:00Z"))
        assertEquals(Instant.parse("2026-07-01T09:30:00Z"), stored.updatedAt)
    }

    @Test
    fun `a push never sends updated_at`() = runTest {
        remote.seedStudy()
        engine().sync()
        db.submissions().upsert(queued())

        engine().sync()

        assertNull(remote.pushBatches.single().single().updatedAt)
    }

    @Test
    fun `the queue leaves oldest first`() = runTest {
        remote.seedStudy()
        engine().sync()
        db.submissions().upsert(queued(clientId = "second", pendingSince = Instant.parse("2026-07-01T09:20:00Z")))
        db.submissions().upsert(queued(clientId = "first", pendingSince = Instant.parse("2026-07-01T09:10:00Z")))

        engine().sync()

        assertEquals(listOf("first", "second"), remote.pushBatches.single().map { it.clientId })
    }

    @Test
    fun `a form version is pinned by the submission and survives a newer publication`() = runTest {
        remote.seedStudy()
        engine().sync()
        db.submissions().upsert(queued(formVersionId = Ids.VERSION_1))

        remote.versions += versionDto(id = Ids.VERSION_3, version = 2, updatedAt = ts(24))
        engine().sync()

        assertEquals(2, db.forms().observeCurrentVersion(Ids.FORM).first()?.version)
        assertEquals(Ids.VERSION_1, row()!!.formVersionId)
        assertEquals(Ids.VERSION_1, remote.submissions.single().formVersionId)
    }

    // ---- push failure modes ---------------------------------------------

    /**
     * One row the server refuses must not wedge the rest of the queue behind it.
     * The batch fails as a unit, so the rows are retried individually and only
     * the one that fails alone is marked.
     */
    @Test
    fun `a rejected row is isolated and the rest of the queue still goes`() = runTest {
        remote.seedStudy()
        engine().sync()
        db.submissions().upsert(queued(clientId = "good-1"))
        db.submissions().upsert(queued(clientId = "poison"))
        db.submissions().upsert(queued(clientId = "good-2"))
        remote.rejects = { it.clientId == "poison" }

        val outcome = engine().sync()

        assertEquals(2, outcome.pushed)
        assertEquals(1, outcome.failed)
        assertEquals(SyncState.FAILED, row("poison")!!.syncState)
        assertEquals(SyncState.UPLOADED, row("good-1")!!.syncState)
        assertEquals(SyncState.UPLOADED, row("good-2")!!.syncState)
    }

    /**
     * When every row fails on its own it is the network, not the rows. Marking
     * them `FAILED` would be a lie about whose fault it is, and worse, it is the
     * state a collector reads as "this did not send and will not".
     */
    @Test
    fun `a push that cannot send leaves everything queued rather than blaming the rows`() = runTest {
        remote.seedStudy()
        engine().sync()
        db.submissions().upsert(queued(clientId = "one"))
        db.submissions().upsert(queued(clientId = "two"))
        remote.pushOffline = true

        val failure = runCatching { engine().sync() }.exceptionOrNull()

        assertTrue(failure is SyncException.PushUnavailable)
        assertEquals(SyncState.QUEUED, row("one")!!.syncState)
        assertEquals(SyncState.QUEUED, row("two")!!.syncState)
    }

    /** Airplane mode: nothing sends, nothing arrives, and nothing on the device is damaged. */
    @Test
    fun `a fully offline run changes nothing and keeps the queue intact`() = runTest {
        remote.seedStudy()
        engine().sync()
        db.submissions().upsert(queued(clientId = "one"))
        val cursorBefore = cursors.peek(SyncCursors.forms(Ids.STUDY))
        remote.offline = true

        val failure = runCatching { engine().sync() }.exceptionOrNull()

        assertTrue(failure is java.io.IOException)
        assertEquals(SyncState.QUEUED, row("one")!!.syncState)
        assertEquals(cursorBefore, cursors.peek(SyncCursors.forms(Ids.STUDY)))
    }

    @Test
    fun `a sync with no signed-in user refuses before touching anything`() = runTest {
        remote.currentUser = null

        val failure = runCatching { engine().sync() }.exceptionOrNull()

        assertTrue(failure is SyncException.NotSignedIn)
        assertTrue(remote.fetchLog.isEmpty())
    }

    // ---- push before pull ------------------------------------------------

    @Test
    fun `push happens before pull`() = runTest {
        remote.seedStudy()
        engine().sync()
        db.submissions().upsert(queued())
        remote.events.clear()

        engine().sync()

        assertEquals("push", remote.events.first())
        assertTrue(remote.events.drop(1).all { it.startsWith("fetch:") })
    }

    /**
     * The amend conflict, which is the only real one in this model: a coordinator
     * edits a submission on the server while the collector who owns it has an
     * unsent edit on their device. Push runs first, so anything still pending
     * when the pull lands failed to send — and its copy is the only copy.
     */
    @Test
    fun `a pull does not write over a queued local edit`() = runTest {
        remote.seedStudy()
        engine().sync()
        db.submissions().upsert(queued(mass = 268.0))
        remote.submissions += submissionDto(
            id = "server-amended",
            clientId = "cccccccc-0000-0000-0000-000000000001",
            mass = 999.0,
            updatedAt = ts(29),
        )
        remote.pushOffline = true

        val failure = runCatching { engine().sync() }.exceptionOrNull()

        assertTrue(failure is SyncException.PushUnavailable)
        assertEquals(JsonPrimitive(268.0), row()!!.data["body_mass"])
        assertEquals(SyncState.QUEUED, row()!!.syncState)
    }

    /**
     * A row the server rejected outright is just as protected as a queued one.
     * `FAILED` still means the device holds the only copy — it says the last
     * attempt did not work, not that the observation is disposable.
     */
    @Test
    fun `a pull does not write over a row the server rejected`() = runTest {
        remote.seedStudy()
        engine().sync()
        db.submissions().upsert(queued(clientId = "amended", mass = 268.0, syncState = SyncState.FAILED))
        remote.submissions += submissionDto(clientId = "amended", mass = 999.0, updatedAt = ts(29))

        engine().sync()

        assertEquals(JsonPrimitive(268.0), row("amended")!!.data["body_mass"])
        assertEquals(SyncState.FAILED, row("amended")!!.syncState)
    }

    /**
     * The other half of not aborting: when writes are blocked but reads work, a
     * collector still gets the form a coordinator published this morning.
     */
    @Test
    fun `a pull still runs when the push could not send`() = runTest {
        remote.seedStudy()
        engine().sync()
        db.submissions().upsert(queued())
        remote.participants += participantDto(id = "44444444-0000-0000-0000-000000000002", code = "K-015", updatedAt = ts(25))
        remote.pushOffline = true

        runCatching { engine().sync() }

        assertEquals(2, db.participants().observeAll(Ids.STUDY).first().size)
    }

    /**
     * The other side of the same rule. Once a row is `UPLOADED` its local
     * `updated_at` *is* the server's, so anything the pull returns for it is by
     * definition newer and should win.
     */
    @Test
    fun `a pull does write over an uploaded row`() = runTest {
        remote.seedStudy()
        engine().sync()
        db.submissions().upsert(queued())
        engine().sync()

        val amended = remote.submissions.single().copy(
            data = buildJsonObject { put("body_mass", 271.5) },
            updatedAt = ts(40),
        )
        remote.submissions.clear()
        remote.submissions += amended

        engine().sync()

        assertEquals(JsonPrimitive(271.5), row()!!.data["body_mass"])
    }

    @Test
    fun `a voided submission comes down as a tombstone and leaves the list`() = runTest {
        remote.seedStudy()
        engine().sync()
        remote.submissions += submissionDto(clientId = "voided", deletedAt = ts(31), updatedAt = ts(31))

        engine().sync()

        assertTrue(row("voided")!!.isVoided)
        assertEquals(emptyList<String>(), rows().map { it.clientId })
    }

    @Test
    fun `a locked submission comes down flagged`() = runTest {
        remote.seedStudy()
        engine().sync()
        remote.submissions += submissionDto(clientId = "locked", lockedAt = ts(32), updatedAt = ts(32))

        engine().sync()

        assertTrue(row("locked")!!.isLocked)
    }

    @Test
    fun `translations arrive scoped to the versions the device holds`() = runTest {
        remote.seedStudy()
        remote.translations += translationDto()
        remote.translations += translationDto(id = "orphan", formVersionId = "33333333-0000-0000-0000-0000000000ff")

        engine().sync()

        assertEquals(
            listOf("translation-fr"),
            db.translations().observeAll(Ids.VERSION_1).first().map { it.id },
        )
    }
}

/**
 * A server that stores the push and then loses the reply on the way back, which
 * is what a tunnel or a dropped connection looks like from the device.
 */
private class LosesTheResponse(private val delegate: FakeRemote) : RemoteDataSource by delegate {
    override suspend fun push(submissions: List<SubmissionDto>): List<SubmissionDto> {
        delegate.push(submissions)
        throw java.io.IOException("connection reset after the server committed")
    }
}
