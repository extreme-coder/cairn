package app.cairn.feature.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cairn.core.database.dao.FormDao
import app.cairn.core.database.dao.SubmissionDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Instant

/**
 * What the app bar shows. `forms` has no display-name column on the server, so
 * the title is derived from the code until one exists.
 */
public data class FormHeader(
    public val code: String,
    public val version: Int,
) {
    public val title: String
        get() = code.replace('_', ' ').replaceFirstChar { it.uppercase() }

    public val versionLabel: String get() = "v$version"
}

public sealed interface CaptureUiState {
    public data object Loading : CaptureUiState

    public data class Unopenable(public val reason: UnopenableReason) : CaptureUiState

    public data class Editing(
        public val form: FormHeader,
        public val capture: CaptureState,
        public val queuedCount: Int,
        public val savedClientId: String? = null,
    ) : CaptureUiState
}

/**
 * Holds a [CaptureState] and forwards to it. Deliberately thin: every rule about
 * what a value means, when an error is shown and what reaches the payload lives
 * in [CaptureState], where it is tested on the JVM without a device. If logic
 * starts accumulating here, it is in the wrong place.
 */
public class CaptureViewModel(
    private val repository: CaptureRepository,
    private val forms: FormDao,
    submissions: SubmissionDao,
    private val studyId: String,
    private val formId: String,
    private val collectedBy: String,
    private val now: () -> Instant = ::systemNow,
) : ViewModel() {

    private val opened = MutableStateFlow<Opened>(Opened.Loading)

    public val uiState: StateFlow<CaptureUiState> =
        combine(opened, submissions.observeUnsyncedCount()) { state, queued ->
            when (state) {
                is Opened.Loading -> CaptureUiState.Loading
                is Opened.Failed -> CaptureUiState.Unopenable(state.reason)
                is Opened.Ready -> CaptureUiState.Editing(
                    form = state.header,
                    capture = state.capture,
                    queuedCount = queued,
                    savedClientId = state.savedClientId,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CaptureUiState.Loading)

    init {
        openForm()
        reopenWhenAVersionArrives()
    }

    /**
     * A form that could not be opened is not permanently unopenable.
     *
     * Forms and their versions arrive in separate pulls, so a device syncing for
     * the first time — after a sign-in, or on a phone handed to a second
     * collector — sees the form row seconds before the version that makes it
     * fillable. Opening once left the screen stuck on "no published version yet"
     * until the app was restarted, which is a lie the collector cannot argue
     * with.
     *
     * Only re-opens from a failed state. A version landing mid-entry must not
     * take the form away from someone who is filling it in, which is why the
     * opened state is read here and not simply overwritten.
     */
    private fun reopenWhenAVersionArrives() {
        viewModelScope.launch {
            forms.observeCurrentVersion(formId)
                .filterNotNull()
                .collect { if (opened.value is Opened.Failed) openForm() }
        }
    }

    /** Also the "Discard" action: a fresh open means a fresh `client_id`. */
    public fun openForm() {
        opened.value = Opened.Loading
        viewModelScope.launch {
            val opening = repository.openForm(
                studyId = studyId,
                formId = formId,
                collectedBy = collectedBy,
                openedAt = now(),
            )
            opened.value = when (opening) {
                is FormOpening.Unopenable -> Opened.Failed(opening.reason)
                is FormOpening.Ready -> {
                    val version = forms.version(opening.state.formVersionId)
                    Opened.Ready(
                        header = FormHeader(
                            code = formCode(),
                            version = version?.version ?: 0,
                        ),
                        capture = opening.state,
                    )
                }
            }
        }
    }

    public fun edit(transform: (CaptureState) -> CaptureState) {
        val current = opened.value as? Opened.Ready ?: return
        opened.value = current.copy(capture = transform(current.capture), savedClientId = null)
    }

    public fun save() {
        val current = opened.value as? Opened.Ready ?: return
        viewModelScope.launch {
            when (val outcome = repository.save(current.capture, now())) {
                is SaveOutcome.Invalid ->
                    opened.value = current.copy(capture = outcome.state)
                is SaveOutcome.Queued ->
                    opened.value = current.copy(
                        capture = outcome.state,
                        savedClientId = outcome.state.clientId,
                    )
            }
        }
    }

    private suspend fun formCode(): String =
        forms.observeForms(studyId).first().firstOrNull { it.id == formId }?.code.orEmpty()

    private sealed interface Opened {
        data object Loading : Opened

        data class Failed(val reason: UnopenableReason) : Opened

        data class Ready(
            val header: FormHeader,
            val capture: CaptureState,
            val savedClientId: String? = null,
        ) : Opened
    }
}

private fun systemNow(): Instant = Instant.fromEpochMilliseconds(System.currentTimeMillis())
