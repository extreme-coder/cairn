package app.cairn.core.network

import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.io.IOException

/**
 * The session half of the contract, tested without a server.
 *
 * Two things are being pinned. First, that what reaches the [SessionStore] is a
 * whole session and comes back as one, because a session that will not
 * round-trip means a collector is signed out every cold start. Second, that
 * "the server said no" and "there is no server today" stay different answers
 * all the way through.
 */
class SessionTest {

    private class RecordingStore : SessionStore {
        var value: String? = null
        var clears = 0

        override suspend fun read(): String? = value

        override suspend fun write(value: String) {
            this.value = value
        }

        override suspend fun clear() {
            clears++
            value = null
        }
    }

    private fun session(userId: String? = ADAKU, email: String? = null) = UserSession(
        accessToken = "jwt",
        refreshToken = "refresh",
        expiresIn = 3600,
        tokenType = "bearer",
        user = userId?.let { UserInfo(aud = "authenticated", id = it, email = email) },
    )

    @Test
    fun `a saved session comes back whole`() = runTest {
        val store = RecordingStore()
        val manager = StoredSessionManager(store)

        manager.saveSession(session())
        val loaded = StoredSessionManager(store).loadSessionOrNull()

        assertEquals("refresh", loaded?.refreshToken)
        assertEquals("jwt", loaded?.accessToken)
        assertEquals(ADAKU, loaded?.user?.id)
    }

    /**
     * The access token is kept, not only the refresh token. A cold start inside
     * its hour with no signal is then a working session rather than a session
     * waiting on a refresh that cannot happen.
     */
    @Test
    fun `the stored blob carries the access token`() = runTest {
        val store = RecordingStore()

        StoredSessionManager(store).saveSession(session())

        assertTrue(store.value.orEmpty().contains("jwt"))
    }

    @Test
    fun `the user id survives a load, so a failed refresh still knows whose device this is`() = runTest {
        val store = RecordingStore()
        StoredSessionManager(store).saveSession(session())

        val manager = StoredSessionManager(store)
        assertNull(manager.knownUserId.value)
        manager.loadSessionOrNull()

        assertEquals(ADAKU, manager.knownUserId.value)
    }

    /**
     * A blob that will not decode is a downgrade, a truncated write, or a
     * restore onto a device whose keystore key is gone. All of them mean sign in
     * again, and none of them should be a crash on launch.
     */
    @Test
    fun `an undecodable blob reads as no session and is dropped`() = runTest {
        val store = RecordingStore().apply { value = "not a session" }

        val loaded = StoredSessionManager(store).loadSessionOrNull()

        assertNull(loaded)
        assertEquals(1, store.clears)
    }

    @Test
    fun `deleting the session empties the store and forgets the user`() = runTest {
        val store = RecordingStore()
        val manager = StoredSessionManager(store)
        manager.saveSession(session())

        manager.deleteSession()

        assertNull(store.value)
        assertNull(manager.knownUserId.value)
    }

    /**
     * The email is remembered for the same reason the id is, and it matters in
     * the same situation: a device that cannot reach the server should still be
     * able to say who is signed in to it, in the words that person would use.
     */
    @Test
    fun `the email survives a load, so a failed refresh can still name the collector`() = runTest {
        val store = RecordingStore()
        StoredSessionManager(store).saveSession(session(email = "adaku@cairn.test"))

        val manager = StoredSessionManager(store)
        assertNull(manager.knownEmail.value)
        manager.loadSessionOrNull()

        assertEquals("adaku@cairn.test", manager.knownEmail.value)
    }

    @Test
    fun `deleting the session forgets the email too`() = runTest {
        val store = RecordingStore()
        val manager = StoredSessionManager(store)
        manager.saveSession(session(email = "adaku@cairn.test"))

        manager.deleteSession()

        assertNull(manager.knownEmail.value)
    }

    @Test
    fun `an authenticated status carries the email alongside the id`() {
        val state = sessionStateOf(
            SessionStatus.Authenticated(session(email = "adaku@cairn.test")),
            null,
        )

        assertEquals(SessionState.SignedIn(ADAKU, "adaku@cairn.test"), state)
    }

    @Test
    fun `a stale session still knows the email it was signed in with`() {
        val failure = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(IOException("no route")))

        val state = sessionStateOf(failure, ADAKU, "adaku@cairn.test")

        assertEquals(SessionState.Stale(ADAKU, "adaku@cairn.test"), state)
        assertEquals("adaku@cairn.test", state.email)
    }

    /**
     * A session stored by an earlier build has no email in it, and the screen
     * that reads it falls back rather than the state becoming unusable.
     */
    @Test
    fun `a session with no email is still a signed-in session`() {
        val state = sessionStateOf(SessionStatus.Authenticated(session()), null)

        assertEquals(ADAKU, state.userId)
        assertNull(state.email)
    }

    @Test
    fun `initializing is not signed out`() {
        assertEquals(SessionState.Unknown, sessionStateOf(SessionStatus.Initializing, null))
    }

    @Test
    fun `an authenticated status carries the collector id`() {
        val state = sessionStateOf(SessionStatus.Authenticated(session()), null)

        assertEquals(SessionState.SignedIn(ADAKU), state)
    }

    /**
     * The case the whole distinction exists for: no signal on a cold start. The
     * refresh token is almost certainly fine and the server cannot say so, and a
     * collector standing in a valley still has to be able to record a bird.
     */
    @Test
    fun `a refresh that could not reach the server keeps the collector signed in`() {
        val failure = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(IOException("no route")))

        val state = sessionStateOf(failure, ADAKU)

        assertEquals(SessionState.Stale(ADAKU), state)
        assertEquals(ADAKU, state.userId)
    }

    @Test
    fun `a refresh failure with nobody known is signed out`() {
        val failure = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(IOException("no route")))

        assertEquals(SessionState.SignedOut, sessionStateOf(failure, null))
    }

    /** A dead refresh token: supabase-kt clears the stored session itself here. */
    @Test
    fun `not authenticated is signed out even when a user was known`() {
        val state = sessionStateOf(SessionStatus.NotAuthenticated(isSignOut = false), ADAKU)

        assertEquals(SessionState.SignedOut, state)
        assertNull(state.userId)
    }

    @Test
    fun `only a signed-out state has no collector`() {
        assertFalse(SessionState.SignedIn(ADAKU).userId == null)
        assertFalse(SessionState.Stale(ADAKU).userId == null)
        assertNull(SessionState.Unknown.userId)
        assertNull(SessionState.SignedOut.userId)
    }

    private companion object {
        const val ADAKU = "55555555-5555-5555-5555-555555555551"
    }
}
