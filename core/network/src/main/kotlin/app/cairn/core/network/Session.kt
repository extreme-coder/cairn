package app.cairn.core.network

import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * Who this device is signed in as, as far as the client can tell.
 *
 * The distinction that matters is [Stale] against [SignedOut]. A collector who
 * cold-starts the app in a valley with no signal has a refresh token that is
 * almost certainly still good and a server that cannot say so. Treating that as
 * signed out would throw away the only account the device knows and leave
 * someone unable to record an observation until they walk back into coverage —
 * which is the exact situation this app exists for. The session is kept, the
 * user id is still known, and sync simply has nothing to do until a refresh
 * succeeds.
 */
public sealed interface SessionState {

    /** The `collected_by` every submission this device creates carries. */
    public val userId: String?

    /**
     * What a person recognises themselves by. Null when the stored session
     * predates this field or the server did not send one — a device signed in as
     * an id nobody can read is still signed in, so the screen falls back rather
     * than the state becoming unusable.
     */
    public val email: String?

    /** Storage has not been read yet. Distinct from signed out, and not a screen. */
    public data object Unknown : SessionState {
        override val userId: String? get() = null
        override val email: String? get() = null
    }

    public data object SignedOut : SessionState {
        override val userId: String? get() = null
        override val email: String? get() = null
    }

    public data class SignedIn(
        override val userId: String,
        override val email: String? = null,
    ) : SessionState

    /**
     * Signed in, but the access token could not be refreshed. Capture works;
     * anything that talks to the server waits.
     */
    public data class Stale(
        override val userId: String,
        override val email: String? = null,
    ) : SessionState
}

/**
 * Why a sign-in did not produce a session.
 *
 * [Rejected] and [Unreachable] are the same screen with different copy and very
 * different advice: one means the password is wrong, the other means the
 * password may be perfectly right and there is no network to check it against.
 * Collapsing them would tell a collector in the field to re-type a password that
 * was never the problem.
 */
public sealed interface SignInOutcome {

    public data object Success : SignInOutcome

    /** The server answered and said no. */
    public data class Rejected(public val reason: String?) : SignInOutcome

    /** The server could not be reached at all. */
    public data object Unreachable : SignInOutcome
}

/**
 * Somewhere durable to keep a session between launches.
 *
 * Deliberately a string in and a string out. This module is pure Kotlin and has
 * no business knowing about the Android Keystore; the implementation that does
 * lives in `:core:session`, and it is the one that must guarantee the bytes on
 * disk are not readable.
 */
public interface SessionStore {

    public suspend fun read(): String?

    public suspend fun write(value: String)

    public suspend fun clear()
}

/**
 * Bridges supabase-kt's session persistence onto a [SessionStore].
 *
 * The whole [UserSession] is stored, not just the refresh token: on a cold start
 * with no network the access token in it may still be within its hour, and the
 * user id in it is what lets the app know whose device this is before any server
 * confirms it.
 *
 * [knownUserId] exists because `SessionStatus.RefreshFailure` carries only a
 * cause, not a session — so at the one moment the app most needs to say "this is
 * still Adaku's device, we just cannot reach the server", the status alone
 * cannot. This remembers the last id that was written or read.
 */
public class StoredSessionManager(
    private val store: SessionStore,
) : SessionManager {

    private val known = MutableStateFlow<String?>(null)

    private val knownAddress = MutableStateFlow<String?>(null)

    public val knownUserId: StateFlow<String?> = known.asStateFlow()

    /**
     * Remembered for the same reason [knownUserId] is: a refresh failure carries
     * no session, and "signed in, cannot reach the server" is a state the app has
     * to be able to describe out loud. Saying it as *Adaku* rather than as a UUID
     * is the difference between a settings screen and a diagnostic.
     */
    public val knownEmail: StateFlow<String?> = knownAddress.asStateFlow()

    override suspend fun saveSession(session: UserSession) {
        session.user?.id?.let { known.value = it }
        session.user?.email?.let { knownAddress.value = it }
        store.write(json.encodeToString(session))
    }

    /**
     * A blob that will not decode raises `NoSessionFoundException`, the same as
     * an empty store, rather than propagating a deserialisation error into
     * start-up. A downgrade, a truncated write and a restore onto a device whose
     * keystore key is gone all look like this, and the recovery from every one
     * of them is the same: sign in again. supabase-kt calls `loadSessionOrNull`,
     * which turns it back into a null.
     */
    override suspend fun loadSession(): UserSession {
        val stored = store.read() ?: throw NoSessionFoundException()
        val session = runCatching { json.decodeFromString<UserSession>(stored) }.getOrNull()
        if (session == null) {
            store.clear()
            throw NoSessionFoundException()
        }
        known.value = session.user?.id
        knownAddress.value = session.user?.email
        return session
    }

    override suspend fun deleteSession() {
        known.value = null
        knownAddress.value = null
        store.clear()
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * Both refresh failures keep the user signed in.
 *
 * `NetworkError` is the obvious one. `InternalServerError` is kept too: a 5xx
 * from the auth server says nothing about whether this refresh token is valid,
 * and signing someone out because the server had a bad minute would wipe the
 * device on the strength of a transient fault. A refresh token that is genuinely
 * dead produces `NotAuthenticated`, and supabase-kt deletes the stored session
 * itself when that happens.
 */
internal fun sessionStateOf(
    status: SessionStatus,
    knownUserId: String?,
    knownEmail: String? = null,
): SessionState =
    when (status) {
        is SessionStatus.Initializing -> SessionState.Unknown
        is SessionStatus.Authenticated ->
            (status.session.user?.id ?: knownUserId)
                ?.let { SessionState.SignedIn(it, status.session.user?.email ?: knownEmail) }
                ?: SessionState.SignedOut
        is SessionStatus.RefreshFailure ->
            knownUserId?.let { SessionState.Stale(it, knownEmail) } ?: SessionState.SignedOut
        is SessionStatus.NotAuthenticated -> SessionState.SignedOut
        else -> SessionState.Unknown
    }
