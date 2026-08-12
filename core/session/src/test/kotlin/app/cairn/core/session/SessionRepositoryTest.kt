package app.cairn.core.session

import app.cairn.core.model.SyncState
import app.cairn.core.network.SignInOutcome
import app.cairn.core.sync.SyncCursors
import app.cairn.core.sync.SyncStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.robolectric.RobolectricTestRunner

/**
 * What a sign-out leaves behind, which on a shared device is the whole question.
 *
 * The phone Adaku carried this morning is Tomás's this afternoon. Everything the
 * first session left — rows, cursors, the session itself — has to be gone before
 * the second one signs in, and the one thing that must *not* be thrown away is
 * an observation that has never reached the server.
 */
@RunWith(RobolectricTestRunner::class)
class SessionRepositoryTest {

    private val database = testDatabase()
    private val remote = FakeRemote()
    private val cursors = InMemoryCursors()

    private val repository = SessionRepository(remote, database, cursors)

    @After
    fun close() {
        database.close()
    }

    @Test
    fun `signing out ends the session, clears the cursors and empties the database`() = runTest {
        database.seed()
        cursors.write(SyncCursors.STUDIES, "all", "2026-07-01T09:30:00.123456+00:00")

        val outcome = repository.signOut()

        assertEquals(SignOutOutcome.SignedOut, outcome)
        assertTrue(remote.signedOut)
        assertTrue(cursors.isEmpty())
        assertTrue(database.studies().observeAll().first().isEmpty())
    }

    /**
     * The bug this is really about: a cursor that outlives its user. Nothing
     * looks wrong afterwards — the next collector simply never receives the rows
     * that arrived before their first sync, permanently, because the cursor only
     * moves forward.
     */
    @Test
    fun `a cursor never survives a sign-out`() = runTest {
        cursors.write(SyncCursors.forms(Ids.STUDY), "all", "2026-07-01T09:30:00.123456+00:00")

        repository.signOut()

        assertEquals(1, cursors.cleared)
        assertNull(cursors.read(SyncCursors.forms(Ids.STUDY), "all"))
    }

    /**
     * An unsynced submission is the only copy of an observation that exists
     * anywhere, and a sign-out is one tap. The count comes back so the caller can
     * say how many are about to be lost.
     */
    @Test
    fun `signing out is refused while anything is still queued`() = runTest {
        database.seed()
        database.submissions().upsert(submission("client-1"))
        database.submissions().upsert(submission("client-2"))

        val outcome = repository.signOut()

        assertEquals(SignOutOutcome.HeldBack(2), outcome)
        assertFalse(remote.signedOut)
        assertEquals(2, database.submissions().pendingKeys().size)
    }

    /** `FAILED` means the last attempt did not work, not that the row is disposable. */
    @Test
    fun `a failed row counts as pending`() = runTest {
        database.seed()
        database.submissions().upsert(submission("client-1", SyncState.FAILED))

        assertTrue(repository.signOut() is SignOutOutcome.HeldBack)
    }

    @Test
    fun `an uploaded row does not hold a sign-out back`() = runTest {
        database.seed()
        database.submissions().upsert(submission("client-1", SyncState.UPLOADED))

        assertEquals(SignOutOutcome.SignedOut, repository.signOut())
    }

    @Test
    fun `a forced sign-out wipes the queue too`() = runTest {
        database.seed()
        database.submissions().upsert(submission("client-1"))

        val outcome = repository.signOut(force = true)

        assertEquals(SignOutOutcome.SignedOut, outcome)
        assertTrue(database.submissions().pendingKeys().isEmpty())
    }

    /**
     * A collector handing the phone over is not always in coverage. If the
     * revoke fails the device must still be wiped — the alternative is a phone
     * that says "sign-out failed" and stays signed in as someone else.
     */
    @Test
    fun `a sign-out whose revoke fails still wipes the device`() = runTest {
        database.seed()
        remote.signOutThrows = true

        val outcome = repository.signOut()

        assertEquals(SignOutOutcome.SignedOut, outcome)
        assertTrue(cursors.isEmpty())
        assertTrue(database.studies().observeAll().first().isEmpty())
    }

    /**
     * Otherwise the next collector's empty database reads as "you are in no
     * study" when it actually means "your first sync has not run yet".
     */
    @Test
    fun `signing out forgets that a sync had completed`() = runTest {
        SyncStatus.succeeded()

        repository.signOut()

        assertFalse(SyncStatus.hasCompletedOnce.value)
    }

    @Test
    fun `a wrong password is rejected rather than thrown`() = runTest {
        assertTrue(repository.signIn("adaku@cairn.test", "wrong") is SignInOutcome.Rejected)
    }

    @Test
    fun `an email typed with a trailing space still signs in`() = runTest {
        assertEquals(
            SignInOutcome.Success,
            repository.signIn("  adaku@cairn.test ", "cairn-dev-password"),
        )
    }
}
