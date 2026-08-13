package app.cairn

import android.app.Application
import android.util.Log
import androidx.room.Room
import app.cairn.core.database.CairnDatabase
import app.cairn.core.network.SessionState
import app.cairn.core.network.StoredSessionManager
import app.cairn.core.network.SupabaseRemoteDataSource
import app.cairn.core.network.cairnSupabaseClient
import app.cairn.core.session.SessionRepository
import app.cairn.core.session.cairnSessionStore
import app.cairn.core.sync.SyncDependencies
import app.cairn.core.sync.SyncEngine
import app.cairn.core.sync.SyncLog
import app.cairn.core.sync.SyncWorker
import app.cairn.core.sync.cairnSyncStores
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Holds the one Room database and wires sync and the session to it.
 *
 * Still hand-wired rather than injected. Four edges now — database, session
 * store, remote, cursors — which is more than there were and still fewer than
 * Koin is worth.
 */
public open class CairnApplication : Application() {

    /**
     * Where this install points, and the key it presents.
     *
     * Open so a test can build the whole app graph without a server, which is
     * not a contrivance: a machine with no `local.properties` builds exactly
     * this app, and it is the path the Sign in screen's "Not configured" state
     * exists for.
     */
    internal open val serverUrl: String get() = BuildConfig.SUPABASE_URL

    internal open val serverKey: String get() = BuildConfig.SUPABASE_KEY

    /**
     * Open so an instrumented test can substitute an in-memory one. A device
     * test that seeded the real `cairn.db` would be destroying whoever is
     * signed in on that device's collected work to check a back stack.
     */
    public open val database: CairnDatabase by lazy {
        Room.databaseBuilder(this, CairnDatabase::class.java, CairnDatabase.NAME).build()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val offline = MutableStateFlow<SessionState>(SessionState.SignedOut)

    /**
     * Who this device is signed in as. Starts [SessionState.Unknown] while the
     * stored session is read, so nothing downstream mistakes "not looked yet"
     * for "signed out".
     */
    public var session: StateFlow<SessionState> = offline
        private set

    /** Null until the app is configured to reach a server at all. */
    public var sessions: SessionRepository? = null
        private set

    /**
     * When this device last completed a sync. Null on a build with no server,
     * which is also the only build where the answer would be "never" forever.
     */
    public var syncLog: SyncLog? = null
        private set

    /** What Settings shows under About. */
    public val version: String
        get() = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    /**
     * What the Sign in screen shows in its Server field: the host, without the
     * scheme. Cairn is self-hosted per research group, so which server a
     * password is about to be handed to is worth stating — and the collector
     * reads a hostname, not a URL.
     */
    public val serverAddress: String
        get() = serverUrl
            .substringAfter("://")
            .trimEnd('/')

    override fun onCreate() {
        super.onCreate()

        if (serverUrl.isEmpty()) {
            Log.w(TAG, "no cairn.supabase.url in local.properties; running with no server")
            return
        }

        val stored = StoredSessionManager(cairnSessionStore(this))
        val remote = SupabaseRemoteDataSource(
            cairnSupabaseClient(serverUrl, serverKey, sessions = stored),
            stored,
        )
        // Cursors and the last-synced time come as a pair because they share a
        // file and are cleared together on sign-out.
        val stores = cairnSyncStores(this)
        val cursors = stores.cursors
        syncLog = stores.log

        SyncDependencies.install(SyncEngine(database, remote, cursors), syncLog)
        SyncWorker.schedule(this)

        sessions = SessionRepository(remote, database, cursors)
        session = remote.sessionState.stateIn(scope, SharingStarted.Eagerly, SessionState.Unknown)

        scope.launch { session.collect { Log.i(TAG, "session: $it") } }
        scope.launch { syncWheneverSignedIn() }
    }

    /**
     * Every time this device becomes signed in, not only the first.
     *
     * A cold start that restores a session has data to fetch and a queue that may
     * have been waiting since the last time there was signal. So does a sign-in
     * on a device that was just wiped by a sign-out — and that one is the more
     * visible case, because the collector is looking at an empty screen while it
     * happens. Waiting for the periodic worker would mean up to fifteen minutes
     * of "No studies yet".
     *
     * A `Stale` session that refreshes counts too: the token just started
     * working again.
     */
    private suspend fun syncWheneverSignedIn() {
        session.collect { if (it is SessionState.SignedIn) SyncWorker.syncNow(this) }
    }

    /**
     * What the Sign in screen starts with in its email field.
     *
     * The headless dev sign-in that used to live here is gone: it was the
     * stand-in for a screen that now exists, and leaving it in would mean the
     * real one was never exercised on a device. Only the address is offered —
     * a password that fills itself in is a habit, not a convenience.
     */
    public val suggestedEmail: String get() = BuildConfig.DEV_EMAIL

    private companion object {
        const val TAG = "CairnApp"
    }
}
