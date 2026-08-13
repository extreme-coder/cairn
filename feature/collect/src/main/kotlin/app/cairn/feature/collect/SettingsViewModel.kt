package app.cairn.feature.collect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cairn.core.database.dao.SubmissionDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Instant

/**
 * The Settings screen: who is signed in, which server, when this device last
 * synced, and the way out.
 *
 * The values that never change on this device — the address the app was built
 * against and its version — are constructor arguments. The ones that do are
 * flows, because a session can go stale and a queue can drain while the screen
 * is open, and both change what this screen is allowed to say.
 */
public class SettingsViewModel(
    submissions: SubmissionDao,
    userId: String,
    server: String,
    version: String,
    email: Flow<String?>,
    stale: Flow<Boolean>,
    lastSyncedAt: Flow<Instant?>,
    now: () -> Instant = ::systemNow,
) : ViewModel() {

    public val uiState: StateFlow<SettingsUiState> =
        combine(
            submissions.observeCounts(userId),
            email,
            stale,
            lastSyncedAt,
        ) { counts, address, isStale, syncedAt ->
            SettingsUiState(
                email = address,
                userId = userId,
                server = server,
                stale = isStale,
                lastSynced = lastSyncedLabel(syncedAt, now()),
                pendingCount = counts.pending,
                version = version,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT),
            SettingsUiState(userId = userId, server = server, version = version),
        )

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
