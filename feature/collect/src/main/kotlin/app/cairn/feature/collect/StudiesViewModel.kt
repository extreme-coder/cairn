package app.cairn.feature.collect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cairn.core.database.dao.StudyDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The Studies screen.
 *
 * Takes [syncedOnce] as a flow rather than reaching for `SyncStatus` itself, so
 * this module stays free of `:core:sync` and the empty state can be tested by
 * handing it a boolean. It is the only thing that separates "your studies are
 * downloading" from "you are in no study" — both are zero rows.
 */
public class StudiesViewModel(
    studies: StudyDao,
    userId: String,
    syncedOnce: Flow<Boolean>,
) : ViewModel() {

    public val uiState: StateFlow<StudiesUiState> =
        combine(studies.observeSummaries(userId), syncedOnce) { rows, synced ->
            when {
                rows.isEmpty() -> StudiesUiState.Empty(synced)
                else -> StudiesUiState.Ready(
                    rows.map { row ->
                        StudyRow(
                            id = row.id,
                            name = row.name,
                            role = row.role,
                            detail = studyDetail(row.formCount, row.submissionCount),
                            status = studyStatus(row.role, row.pendingCount),
                            pendingCount = row.pendingCount,
                        )
                    },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), StudiesUiState.Loading)

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
