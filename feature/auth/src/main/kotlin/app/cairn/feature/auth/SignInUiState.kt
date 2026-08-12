package app.cairn.feature.auth

import app.cairn.core.network.SignInOutcome

/**
 * Why the last attempt did not sign anyone in.
 *
 * The copy is here rather than in the composable, the same way `FieldError` and
 * `UnopenableReason` carry theirs. A screen should not be the place a sentence
 * about the network lives, and these three sentences are the only difference
 * between "check what you typed" and "walk uphill".
 */
public enum class SignInProblem {

    /** The server answered and refused. */
    REFUSED,

    /** The request never arrived. The password may be perfectly correct. */
    UNREACHABLE,

    /** No server address was compiled into this build. Only a developer sees this. */
    UNCONFIGURED,
    ;

    public fun message(): String = when (this) {
        REFUSED -> "The email address or password is incorrect."
        UNREACHABLE -> "Cairn cannot reach the server. Check the connection and try again."
        UNCONFIGURED -> "This app was built without a server address."
    }
}

/**
 * Everything the Sign in screen draws.
 *
 * [server] is shown and not edited. Cairn is self-hosted per research group and
 * the address is compiled in, so the field is there to let a collector confirm
 * which server they are about to hand a password to — not to point the app
 * somewhere else. Making it editable is a real feature and a bigger one than it
 * looks, because the whole client graph is built from that address at start-up.
 */
public data class SignInUiState(
    public val server: String = "",
    public val email: String = "",
    public val password: String = "",
    public val revealPassword: Boolean = false,
    public val submitting: Boolean = false,
    public val problem: SignInProblem? = null,
) {
    /**
     * Both fields non-empty and nothing in flight. Deliberately not an email
     * format check: the server is the authority on what its addresses look like,
     * and a regex here would reject somebody's valid address on a hillside with
     * no way to argue.
     */
    public val canSubmit: Boolean
        get() = !submitting && server.isNotBlank() && email.isNotBlank() && password.isNotEmpty()
}

internal fun SignInOutcome.problem(): SignInProblem? = when (this) {
    SignInOutcome.Success -> null
    is SignInOutcome.Rejected -> SignInProblem.REFUSED
    SignInOutcome.Unreachable -> SignInProblem.UNREACHABLE
}
