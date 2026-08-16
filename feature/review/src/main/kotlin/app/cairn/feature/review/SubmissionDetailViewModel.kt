package app.cairn.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cairn.core.database.dao.StudyMemberDao
import app.cairn.core.database.dao.SubmissionDao
import app.cairn.core.database.dao.SubmissionDetail
import app.cairn.core.database.toFormSchema
import app.cairn.core.model.SyncState
import app.cairn.core.model.collectedFullLabel
import app.cairn.core.model.formTitle
import app.cairn.core.model.reviewStateOf
import app.cairn.core.model.submissionLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import java.time.ZoneId
import kotlin.time.Instant

/**
 * One submission, read against the schema it was collected under, and the two
 * things a coordinator can do to it.
 *
 * Room stays the source of truth even though this screen writes to the server.
 * A lock is applied remotely, the row the server echoed is written into Room by
 * [ReviewRepository], and the screen redraws because its `Flow` re-emitted. It
 * never renders the answer it hoped for — which is what stops a refused write
 * from leaving a "Locked" chip on a row the server did not lock.
 */
public class SubmissionDetailViewModel(
    private val submissions: SubmissionDao,
    members: StudyMemberDao,
    studyId: String,
    private val collectedBy: String,
    private val clientId: String,
    userId: String,
    private val repository: ReviewRepository,
    private val now: () -> Instant = ::systemNow,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    /**
     * What the screen holds that the database does not: which dialog is open,
     * whether a write is in flight, and the last refusal.
     *
     * Kept apart from the row so a redraw caused by any other write cannot clear
     * a message the coordinator has not read yet.
     */
    private val interaction = MutableStateFlow(Interaction())

    public val uiState: StateFlow<DetailUiState> =
        combine(
            submissions.observeDetail(collectedBy, clientId),
            members.observeRole(studyId, userId),
            interaction,
        ) { detail, role, held ->
            if (detail == null) {
                DetailUiState.Gone
            } else {
                render(detail, canReview = role?.showsLockAction == true, held = held)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), DetailUiState.Loading)

    /** Opens the confirmation. Nothing is written until it is confirmed. */
    public fun ask(action: ReviewAction) {
        interaction.update { it.copy(confirming = action, problem = null) }
    }

    public fun dismiss() {
        interaction.update { it.copy(confirming = null) }
    }

    /**
     * Applies the action the dialog is confirming.
     *
     * The server id is read out of Room here rather than captured when the
     * screen was drawn. A sync can land while the dialog is open — that is
     * exactly when it is likely to, because the queue drains in the background —
     * and the row that was `id`-less when the button appeared may have one by
     * the time it is pressed. Reading late means confirming acts on the row as
     * it is, and a row that still has no id refuses rather than writing nowhere.
     */
    public fun confirm() {
        val action = interaction.value.confirming ?: return
        interaction.update { it.copy(confirming = null, working = true, problem = null) }

        viewModelScope.launch {
            val serverId = submissions.observeDetail(collectedBy, clientId).first()?.id
            if (serverId == null) {
                interaction.update { it.copy(working = false, problem = NOT_UPLOADED) }
                return@launch
            }

            val outcome = when (action) {
                ReviewAction.LOCK -> repository.lock(collectedBy, clientId, serverId, now())
                ReviewAction.VOID -> repository.void(collectedBy, clientId, serverId, now())
                ReviewAction.RESTORE -> repository.restore(collectedBy, clientId, serverId)
            }
            interaction.update {
                it.copy(
                    working = false,
                    problem = when (outcome) {
                        is ReviewOutcome.Applied -> null
                        is ReviewOutcome.Offline -> offlineMessage(action)
                        is ReviewOutcome.Refused -> outcome.message
                    },
                )
            }
        }
    }

    private fun render(detail: SubmissionDetail, canReview: Boolean, held: Interaction): DetailUiState {
        val locked = detail.lockedAt != null
        val voided = detail.deletedAt != null
        val uploaded = detail.syncState == SyncState.UPLOADED && detail.id != null

        val header = DetailHeader(
            label = submissionLabel(detail.participantCode, detail.clientId),
            studyName = detail.studyName,
            formTitle = formTitle(detail.formCode),
            versionLabel = "v${detail.version}",
            collected = collectedFullLabel(detail.collectedAt, zone),
            state = reviewStateOf(detail.lockedAt, detail.deletedAt),
            locked = locked,
            voided = voided,
        )

        // Decoding is what can fail, and it fails for one submission rather than
        // for the query behind every screen. The header above is already built,
        // so a form this build cannot read still says what it is and when it was
        // collected.
        val schema = try {
            detail.schema.toFormSchema()
        } catch (_: SerializationException) {
            return DetailUiState.Unreadable(header)
        }

        val (actions, note) = actionsFor(locked, voided, uploaded, canReview)
        return DetailUiState.Ready(
            header = header,
            fields = answers(schema, detail.data),
            extras = extras(schema, detail.data),
            actions = actions,
            note = note,
            confirming = held.confirming,
            working = held.working,
            problem = held.problem,
        )
    }

    private data class Interaction(
        val confirming: ReviewAction? = null,
        val working: Boolean = false,
        val problem: String? = null,
    )

    private companion object {
        const val STOP_TIMEOUT = 5_000L

        const val NOT_UPLOADED =
            "This submission has not uploaded yet. Locking and voiding happen on the server."

        /**
         * Named for what was not done, not for the network.
         *
         * "No connection" states the cause and leaves the reader to work out the
         * consequence; the consequence is the part they have to act on, and on
         * this screen it is that the submission is still open.
         */
        fun offlineMessage(action: ReviewAction): String = when (action) {
            ReviewAction.LOCK -> "Not locked — the server could not be reached. Try again when you reconnect."
            ReviewAction.VOID -> "Not voided — the server could not be reached. Try again when you reconnect."
            ReviewAction.RESTORE -> "Not restored — the server could not be reached. Try again when you reconnect."
        }
    }
}
