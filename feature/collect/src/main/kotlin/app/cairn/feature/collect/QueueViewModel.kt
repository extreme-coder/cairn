package app.cairn.feature.collect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cairn.core.database.dao.SubmissionDao
import app.cairn.core.model.SyncState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import kotlin.time.Instant

/**
 * Everything this collector has recorded that the server has not acknowledged.
 *
 * The Queue is the screen that answers "is my morning's work safe". It is
 * therefore the one screen that must never be optimistic: a row is shown in the
 * state the database says it is in, and the only action offered is the one that
 * actually helps.
 */
public class QueueViewModel(
    private val submissions: SubmissionDao,
    private val userId: String,
    private val requestSync: () -> Unit,
    private val now: () -> Instant = ::systemNow,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val showUploaded = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val uploaded = showUploaded.flatMapLatest { showing ->
        // Not queried until asked for. A device three months into a season holds
        // thousands of uploaded rows and none of them need anything done to them.
        if (showing) submissions.observeUploaded(userId) else flowOf(emptyList())
    }

    public val uiState: StateFlow<QueueUiState> =
        combine(
            submissions.observeCounts(userId),
            submissions.observePending(userId),
            uploaded,
            showUploaded,
        ) { counts, pending, uploadedRows, showing ->
            val at = now()
            val rows = pending.map { it.toRow(at, zone) }
            QueueUiState(
                counts = counts,
                queued = rows.filter { it.state == SyncState.QUEUED },
                failed = rows.filter { it.state == SyncState.FAILED },
                uploaded = uploadedRows.map { it.toRow(at, zone) },
                showingUploaded = showing,
                loaded = true,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), QueueUiState())

    public fun toggleUploaded() {
        showUploaded.value = !showUploaded.value
    }

    /**
     * What "Upload now" does.
     *
     * Re-queues the failed rows first, then asks for a sync. Without the requeue
     * the button would be a lie for exactly the rows the collector pressed it
     * for: a `FAILED` row is not in `awaiting()`, so a sync would walk straight
     * past it and report success.
     *
     * Asking twice is free — the work is unique — and the sync itself is
     * WorkManager's problem, so nothing here waits on a network.
     */
    public fun uploadNow() {
        viewModelScope.launch {
            submissions.requeueFailed()
            requestSync()
        }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
