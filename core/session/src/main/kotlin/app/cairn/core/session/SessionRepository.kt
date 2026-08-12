package app.cairn.core.session

import app.cairn.core.database.CairnDatabase
import app.cairn.core.network.RemoteDataSource
import app.cairn.core.network.SessionState
import app.cairn.core.network.SignInOutcome
import app.cairn.core.sync.SyncCursors
import app.cairn.core.sync.SyncStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * What a sign-out did, or refused to do.
 *
 * [HeldBack] is not an error. It is the repository declining to destroy
 * observations that exist nowhere else, and handing the count back so the caller
 * can say how many and ask whether they are really meant to go.
 */
public sealed interface SignOutOutcome {

    public data object SignedOut : SignOutOutcome

    public data class HeldBack(public val pending: Int) : SignOutOutcome
}

/**
 * Who is signed in, and what changes when that changes.
 *
 * The sign-out half is the reason this exists. A device is shared: the phone
 * that Adaku carried this morning is Tomás's this afternoon, and everything the
 * first collector's session left behind — rows, cursors, the session itself —
 * has to be gone before the second one signs in. A cursor that outlives its user
 * is the quiet version of that bug: nothing looks wrong, and the new user simply
 * never receives the rows that arrived before their first sync.
 */
public class SessionRepository(
    private val remote: RemoteDataSource,
    private val database: CairnDatabase,
    private val cursors: SyncCursors,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    public val state: Flow<SessionState> = remote.sessionState

    /**
     * Whitespace is trimmed off the email because it is typed on a phone
     * keyboard in the field, often with gloves on, and a trailing space is a
     * rejected sign-in that looks exactly like a wrong password.
     */
    public suspend fun signIn(email: String, password: String): SignInOutcome =
        remote.signIn(email.trim(), password)

    /**
     * Ends the session and wipes the device.
     *
     * Refuses while anything is still queued, unless [force] says otherwise: an
     * unsynced submission is the only copy of an observation that exists
     * anywhere, and a sign-out is one tap. The order matters — the session goes
     * first, so no sync can start against a database that is being emptied
     * underneath it.
     *
     * The local wipe happens whatever the session end does. A device being
     * handed to a colleague is not always in coverage, and "sign-out failed, you
     * are still signed in as Adaku" is the wrong answer to give the person
     * holding it. The remote is expected to end the session locally itself; this
     * is the second lock on the same door.
     */
    public suspend fun signOut(force: Boolean = false): SignOutOutcome {
        val pending = database.submissions().pendingKeys().size
        if (pending > 0 && !force) return SignOutOutcome.HeldBack(pending)

        runCatching { remote.signOut() }
        cursors.clear()
        SyncStatus.reset()
        withContext(io) { database.clearAllTables() }
        return SignOutOutcome.SignedOut
    }
}
