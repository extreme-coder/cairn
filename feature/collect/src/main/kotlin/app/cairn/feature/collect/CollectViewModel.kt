package app.cairn.feature.collect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cairn.core.database.dao.FormDao
import app.cairn.core.database.dao.StudyDao
import app.cairn.core.database.dao.StudyMemberDao
import app.cairn.core.database.dao.SubmissionDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import kotlin.time.Instant

/**
 * One study's forms, plus what has recently been collected in it.
 *
 * [Gone] is a real state, not defensive coding: a sign-out wipes the database
 * while this screen may be on top of the stack, and so does a PI removing a
 * collector from a study. Rendering an empty form list under the old study's
 * name would leave someone tapping at a study that no longer exists.
 */
public class CollectViewModel(
    studies: StudyDao,
    members: StudyMemberDao,
    forms: FormDao,
    submissions: SubmissionDao,
    studyId: String,
    userId: String,
    private val now: () -> Instant = ::systemNow,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    public val uiState: StateFlow<CollectUiState> =
        combine(
            studies.observe(studyId),
            members.observeRole(studyId, userId),
            forms.observeFormSummaries(studyId),
            submissions.observeRecent(studyId, userId),
            submissions.observeCounts(userId),
        ) { study, role, formRows, recent, counts ->
            if (study == null) {
                CollectUiState.Gone
            } else {
                val at = now()
                CollectUiState.Ready(
                    studyName = study.name,
                    role = role,
                    forms = formRows.map { form ->
                        FormRow(
                            id = form.id,
                            title = formTitle(form.code),
                            detail = formDetail(form.version, form.schema),
                            versionLabel = form.version?.let { "v$it" },
                            openable = formOpenable(form.version, form.schema),
                        )
                    },
                    recent = recent.map { it.toRow(at, zone) },
                    pendingCount = counts.pending,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), CollectUiState.Loading)

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}

internal fun systemNow(): Instant = Instant.fromEpochMilliseconds(System.currentTimeMillis())
