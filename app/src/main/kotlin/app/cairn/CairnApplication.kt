package app.cairn

import android.app.Application
import android.util.Log
import androidx.room.Room
import app.cairn.core.database.CairnDatabase
import app.cairn.core.network.SupabaseRemoteDataSource
import app.cairn.core.network.cairnSupabaseClient
import app.cairn.core.sync.SyncDependencies
import app.cairn.core.sync.SyncEngine
import app.cairn.core.sync.SyncWorker
import app.cairn.core.sync.cairnSyncCursors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Holds the one Room database and wires sync to it.
 *
 * Still hand-wired rather than injected. There are three edges worth naming now
 * — database, remote, cursors — which is more than there were and still fewer
 * than Koin is worth.
 */
public class CairnApplication : Application() {

    public val database: CairnDatabase by lazy {
        Room.databaseBuilder(this, CairnDatabase::class.java, CairnDatabase.NAME).build()
    }

    private val signedIn = MutableStateFlow<String?>(null)

    /** The `collected_by` every submission this device creates will carry. */
    public val signedInUserId: StateFlow<String?> get() = signedIn.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.SUPABASE_URL.isEmpty()) {
            Log.w(TAG, "no cairn.supabase.url in local.properties; running with no server")
            return
        }

        val remote = SupabaseRemoteDataSource(
            cairnSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY),
        )
        SyncDependencies.install(SyncEngine(database, remote, cairnSyncCursors(this)))
        SyncWorker.schedule(this)

        /*
         * Headless sign-in, because there is no Sign in screen yet and the
         * refresh token has nowhere safe to live. Both are tracked; neither has
         * to exist for the sync itself to be real.
         */
        if (BuildConfig.DEV_EMAIL.isEmpty()) return
        scope.launch {
            runCatching { remote.signIn(BuildConfig.DEV_EMAIL, BuildConfig.DEV_PASSWORD) }
                .onFailure { Log.e(TAG, "dev sign-in failed", it) }
                .onSuccess {
                    signedIn.value = remote.currentUserId()
                    Log.i(TAG, "signed in as ${signedIn.value}")
                    SyncWorker.syncNow(this@CairnApplication)
                }
        }
    }

    private companion object {
        const val TAG = "CairnApp"
    }
}
