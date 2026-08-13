package app.cairn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cairn.core.designsystem.CairnTheme
import app.cairn.core.network.SessionState
import app.cairn.core.network.SignInOutcome
import app.cairn.core.session.SessionRepository
import app.cairn.core.session.SignOutOutcome
import app.cairn.feature.auth.SignInScreen
import app.cairn.feature.auth.SignInViewModel
import app.cairn.feature.auth.SignOutDialog
import app.cairn.feature.auth.SignOutUiState
import app.cairn.feature.auth.SignOutViewModel
import app.cairn.feature.auth.StartingScreen

public class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val application = this.application as CairnApplication
        setContent {
            CairnTheme {
                CairnApp(application)
            }
        }
    }
}

/**
 * The outermost decision, and the only one that is not navigation.
 *
 * `SessionState` chooses between three worlds — not looked yet, signed out, and
 * signed in — and it is the single source of that answer, so signing out
 * anywhere replaces the screen everywhere. Inside the signed-in world,
 * [CairnNavHost] owns the back stack.
 *
 * `Stale` shows the app, not the sign-in screen. A refresh that could not reach
 * the server is not a signed-out user, and a collector in that situation still
 * has a form to fill in.
 */
@Composable
private fun CairnApp(application: CairnApplication) {
    val session: SessionState by application.session.collectAsStateWithLifecycle()
    val sessions = application.sessions

    when (val current = session) {
        SessionState.Unknown -> StartingScreen()
        SessionState.SignedOut ->
            SignInRoute(sessions, application.serverAddress, application.suggestedEmail)
        else -> SignedIn(application, sessions, current)
    }
}

@Composable
private fun SignInRoute(sessions: SessionRepository?, server: String, suggestedEmail: String) {
    val viewModel: SignInViewModel = viewModel(
        key = "sign-in",
        factory = viewModelFactory {
            initializer {
                SignInViewModel(
                    // A build with no server configured still renders the screen,
                    // which then says so rather than failing at start-up.
                    signIn = { email, password ->
                        sessions?.signIn(email, password) ?: NO_SERVER
                    },
                    server = server,
                    email = suggestedEmail,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    SignInScreen(
        state = state,
        onEmail = viewModel::setEmail,
        onPassword = viewModel::setPassword,
        onToggleReveal = viewModel::toggleReveal,
        onSignIn = viewModel::signIn,
    )
}

/** A build with no server address behaves as if the server were unreachable, because it is. */
private val NO_SERVER = SignInOutcome.Unreachable

/**
 * Signing out is asked for on Settings and confirmed here.
 *
 * The dialog stays at this level because it must survive the screen underneath
 * it going away: the second confirmation is what wipes the device, and by the
 * time it returns there is no Settings screen left to have been hosting it.
 */
@Composable
private fun SignedIn(
    application: CairnApplication,
    sessions: SessionRepository?,
    session: SessionState,
) {
    val signOut: SignOutViewModel = viewModel(
        key = "sign-out",
        factory = viewModelFactory {
            initializer {
                SignOutViewModel { force ->
                    sessions?.signOut(force) ?: SignOutOutcome.SignedOut
                }
            }
        },
    )
    val prompt by signOut.state.collectAsStateWithLifecycle()

    CairnNavHost(
        application = application,
        userId = session.userId.orEmpty(),
        email = session.email,
        stale = session is SessionState.Stale,
        onSignOut = signOut::ask,
    )

    if (prompt != SignOutUiState.Hidden) {
        SignOutDialog(prompt, onConfirm = signOut::confirm, onDismiss = signOut::dismiss)
    }
}
