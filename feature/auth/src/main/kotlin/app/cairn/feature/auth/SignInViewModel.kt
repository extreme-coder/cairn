package app.cairn.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cairn.core.network.SignInOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds what has been typed and hands it to the session layer.
 *
 * Takes the sign-in function rather than the repository. There is one call, it
 * is easy to fake, and a screen that could reach the whole session layer would
 * eventually reach for the sign-out on it too.
 *
 * Nothing here navigates. A successful sign-in changes `SessionState`, the app
 * observes that, and the screen is replaced — so there is no second source of
 * truth about whether anyone is signed in.
 */
public class SignInViewModel(
    private val signIn: suspend (String, String) -> SignInOutcome,
    server: String,
    email: String = "",
) : ViewModel() {

    private val _state = MutableStateFlow(
        SignInUiState(
            server = server,
            email = email,
            problem = if (server.isBlank()) SignInProblem.UNCONFIGURED else null,
        ),
    )

    public val state: StateFlow<SignInUiState> = _state.asStateFlow()

    /** Editing clears the last problem: the answer to a rejection is a new attempt. */
    public fun setEmail(email: String) {
        _state.update { it.copy(email = email, problem = it.problem.clearedByTyping()) }
    }

    public fun setPassword(password: String) {
        _state.update { it.copy(password = password, problem = it.problem.clearedByTyping()) }
    }

    public fun toggleReveal() {
        _state.update { it.copy(revealPassword = !it.revealPassword) }
    }

    public fun signIn() {
        if (!_state.value.canSubmit) return
        _state.update { it.copy(submitting = true, problem = null) }
        viewModelScope.launch {
            val attempt = _state.value
            val outcome = signIn(attempt.email, attempt.password)
            _state.update {
                it.copy(
                    submitting = false,
                    problem = outcome.problem(),
                    // The password has done its work. Holding it in a retained
                    // object after that is a leak waiting for a heap dump.
                    password = if (outcome is SignInOutcome.Success) "" else it.password,
                )
            }
        }
    }

    /** An unconfigured build does not become configured because somebody typed. */
    private fun SignInProblem?.clearedByTyping(): SignInProblem? =
        if (this == SignInProblem.UNCONFIGURED) this else null
}
