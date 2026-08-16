package app.cairn.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cairn.core.database.dao.ProgressDay
import app.cairn.core.database.dao.StudyDao
import app.cairn.core.database.dao.SubmissionDao
import app.cairn.core.model.axisDayLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Instant

/**
 * How much has been collected in one study, by day.
 *
 * The device-side reading of the server's `v_study_progress`. A view cannot be
 * pulled — there is no cursor over one and no primary key to upsert on — but it
 * does not need to be: a coordinator's pull already brings every submission in
 * the study down, so the same aggregate is available from Room. Which keeps the
 * rule the whole app is built on: the UI reads from the database, never from the
 * network.
 */
public class ProgressViewModel(
    studies: StudyDao,
    submissions: SubmissionDao,
    studyId: String,
    private val now: () -> Instant = ::systemNow,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val window: Int = WINDOW_DAYS,
) : ViewModel() {

    public val uiState: StateFlow<ProgressUiState> =
        combine(
            studies.observe(studyId),
            submissions.observeProgress(studyId, zoneOffsetMillis()),
            submissions.observeReviewCounts(studyId),
        ) { study, days, counts ->
            ProgressUiState(
                studyName = study?.name.orEmpty(),
                counts = counts,
                bars = bars(days, today()),
                caption = studyProgressCaption(window),
                loaded = true,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), ProgressUiState())

    /**
     * The window, filled in — including the days nothing happened on.
     *
     * A chart built only from the rows the query returned would leave out every
     * empty day and silently compress the axis, so a fortnight with one busy
     * afternoon in it would read as a fortnight of steady work. Days with no
     * observations are the fact a PI is looking for.
     */
    private fun bars(days: List<ProgressDay>, today: LocalDate): List<ProgressBar> {
        val counted = days.associateBy { it.day }
        return (window - 1 downTo 0).map { back ->
            val day = today.minusDays(back.toLong())
            val key = day.toString()
            ProgressBar(
                day = key,
                axisLabel = axisDayLabel(day),
                count = counted[key]?.submissions ?: 0,
            )
        }
    }

    private fun today(): LocalDate =
        java.time.Instant.ofEpochMilli(now().toEpochMilliseconds()).atZone(zone).toLocalDate()

    /**
     * The offset the query groups days by, taken at *now* rather than per row.
     *
     * One offset for the whole window, so a daylight saving change inside it
     * moves an hour of observations onto the neighbouring bar. Over a fortnight
     * that is at most one bar off by a fraction, and the alternative — grouping
     * in Kotlin — means reading every submission in the study into memory to
     * draw fourteen numbers.
     */
    private fun zoneOffsetMillis(): Long =
        zone.rules.getOffset(java.time.Instant.ofEpochMilli(now().toEpochMilliseconds()))
            .totalSeconds
            .toLong() * MILLIS_PER_SECOND

    private companion object {
        const val STOP_TIMEOUT = 5_000L
        const val WINDOW_DAYS = 14
        const val MILLIS_PER_SECOND = 1_000L
    }
}
