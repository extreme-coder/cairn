package app.cairn.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cairn.core.session.SignOutOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Where a sign-out has got to.
 *
 * [WouldDiscard] is not reached by asking how many rows are queued. It is
 * reached by *trying* to sign out and being refused, so the number on the screen
 * is the number the repository actually counted at the moment it declined —
 * there is no window in which the dialog says two and the wipe takes three.
 */
public sealed interface SignOutUiState {

    public data object Hidden : SignOutUiState

    public data object Confirming : SignOutUiState

    public data class WouldDiscard(public val pending: Int) : SignOutUiState
}

public class SignOutViewModel(
    private val signOut: suspend (Boolean) -> SignOutOutcome,
) : ViewModel() {

    private val _state = MutableStateFlow<SignOutUiState>(SignOutUiState.Hidden)

    public val state: StateFlow<SignOutUiState> = _state.asStateFlow()

    public fun ask() {
        _state.value = SignOutUiState.Confirming
    }

    public fun dismiss() {
        _state.value = SignOutUiState.Hidden
    }

    /**
     * Confirming from the warning is what forces the wipe. The first confirm
     * never can: someone who has not been told they are about to delete three
     * observations has not agreed to delete three observations.
     */
    public fun confirm() {
        val force = _state.value is SignOutUiState.WouldDiscard
        viewModelScope.launch {
            _state.value = when (val outcome = signOut(force)) {
                is SignOutOutcome.HeldBack -> SignOutUiState.WouldDiscard(outcome.pending)
                SignOutOutcome.SignedOut -> SignOutUiState.Hidden
            }
        }
    }
}
