package app.cairn.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cairn.core.database.dao.StudyDao
import app.cairn.core.database.dao.SubmissionDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.ZoneId
import kotlin.time.Instant

/**
 * Every submission in one study, whoever collected it.
 *
 * The rows come from RLS: a coordinator's pull returned the whole study and a
 * collector's returned only their own, so this query says `where study_id` and
 * nothing about who is reading it. The client implements no authorization, and
 * this screen is the clearest place that shows — it is the same query for all
 * four roles and it returns four different lists.
 *
 * The filter is local state rather than a route argument. It is a way of looking
 * at one list, not a different place, and putting it in the back stack would
 * make the system back button undo a chip tap.
 */
public class SubmissionsViewModel(
    studies: StudyDao,
    submissions: SubmissionDao,
    studyId: String,
    private val now: () -> Instant = ::systemNow,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val filter = MutableStateFlow(ReviewFilter.ALL)

    public val uiState: StateFlow<SubmissionsUiState> =
        combine(
            studies.observe(studyId),
            submissions.observeForReview(studyId),
            submissions.observeReviewCounts(studyId),
            filter,
        ) { study, rows, counts, selected ->
            if (study == null) {
                SubmissionsUiState.Gone
            } else {
                val at = now()
                SubmissionsUiState.Ready(
                    studyName = study.name,
                    rows = rows.map { it.toRow(at, zone) },
                    filter = selected,
                    counts = counts,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), SubmissionsUiState.Loading)

    public fun select(next: ReviewFilter) {
        filter.update { next }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}

internal fun systemNow(): Instant = Instant.fromEpochMilliseconds(System.currentTimeMillis())
