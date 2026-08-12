package app.cairn.core.network

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import java.io.IOException

/**
 * Sign-in, sign-out and cold start, against a mock server.
 *
 * These are the paths that decide whether someone can work today, so they are
 * asserted against the responses a real GoTrue gives rather than against a fake
 * of our own: a 400 is a wrong password, an `IOException` is a valley with no
 * signal, and the two lead to different places.
 */
class SupabaseAuthTest {

    private class Store : SessionStore {
        var value: String? = null

        override suspend fun read(): String? = value

        override suspend fun write(value: String) {
            this.value = value
        }

        override suspend fun clear() {
            value = null
        }
    }

    private var offline = false

    private val engine = MockEngine { request ->
        if (offline) throw IOException("no route to host")
        when {
            request.url.encodedPath.endsWith("/token") -> respond(
                content = TOKEN_RESPONSE,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )

            request.url.encodedPath.endsWith("/logout") -> respond(
                content = "",
                status = HttpStatusCode.NoContent,
            )

            else -> respond(
                content = "[]",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
    }

    private fun source(sessions: StoredSessionManager? = null) = SupabaseRemoteDataSource(
        cairnSupabaseClient(URL, KEY, engine, sessions),
        sessions,
    )

    @Test
    fun `a sign-in that works leaves a signed-in user`() = runTest {
        val source = source()

        assertEquals(SignInOutcome.Success, source.signIn("adaku@cairn.test", "password"))
        assertEquals(ADAKU, source.currentUserId())
    }

    @Test
    fun `a refused sign-in is rejected, not a failure to reach the server`() = runTest {
        val refusing = MockEngine {
            respondError(
                status = HttpStatusCode.BadRequest,
                content = """{"error":"invalid_grant","error_description":"Invalid login credentials"}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val source = SupabaseRemoteDataSource(cairnSupabaseClient(URL, KEY, refusing))

        val outcome = source.signIn("adaku@cairn.test", "wrong")

        assertIs<SignInOutcome.Rejected>(outcome)
        assertNull(source.currentUserId())
    }

    /**
     * The password may well be right. Telling someone to check it, when the real
     * answer is that there is no network, is how a collector loses an afternoon.
     */
    @Test
    fun `a sign-in with no network is unreachable, not refused`() = runTest {
        offline = true

        assertEquals(SignInOutcome.Unreachable, source().signIn("adaku@cairn.test", "password"))
    }

    /**
     * A phone being handed over is not always in coverage. The revoke is
     * best-effort; ending the session on the device is not.
     */
    @Test
    fun `signing out with no network still ends the session here`() = runTest {
        val sessions = StoredSessionManager(Store())
        val source = source(sessions)
        source.signIn("adaku@cairn.test", "password")

        offline = true
        source.signOut()

        assertNull(source.currentUserId())
        assertNull(sessions.knownUserId.value)
    }

    @Test
    fun `signing in writes the session to the store`() = runTest {
        val store = Store()
        val source = source(StoredSessionManager(store))

        source.signIn("adaku@cairn.test", "password")

        assertEquals(true, store.value?.contains(ADAKU))
    }

    @Test
    fun `signing out empties the store`() = runTest {
        val store = Store()
        val source = source(StoredSessionManager(store))
        source.signIn("adaku@cairn.test", "password")

        source.signOut()

        assertNull(store.value)
    }

    /**
     * The point of persisting anything: a second launch is signed in without a
     * password and without a round trip, which is also what makes launching in
     * airplane mode work.
     */
    @Test
    fun `a cold start with a stored session needs no password`() = runTest {
        val store = Store()
        StoredSessionManager(store).saveSession(
            UserSession(
                accessToken = "jwt",
                refreshToken = "refresh",
                expiresIn = 3600,
                tokenType = "bearer",
                user = UserInfo(aud = "authenticated", id = ADAKU),
            ),
        )

        val sessions = StoredSessionManager(store)
        val restarted = cairnSupabaseClient(URL, KEY, engine, sessions)
        restarted.auth.awaitInitialization()

        assertEquals(ADAKU, SupabaseRemoteDataSource(restarted, sessions).currentUserId())
    }

    private companion object {
        const val URL = "https://example.supabase.co"
        const val KEY = "sb_publishable_test"
        const val ADAKU = "55555555-5555-5555-5555-555555555551"

        val TOKEN_RESPONSE = """
            {
              "access_token": "jwt",
              "token_type": "bearer",
              "expires_in": 3600,
              "refresh_token": "refresh",
              "user": { "id": "$ADAKU", "aud": "authenticated" }
            }
        """.trimIndent()
    }
}
