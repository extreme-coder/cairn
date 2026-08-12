package app.cairn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cairn.core.database.CairnDatabase
import app.cairn.core.designsystem.CairnEmptyState
import app.cairn.core.designsystem.CairnTheme
import app.cairn.core.network.SessionState
import app.cairn.core.network.SignInOutcome
import app.cairn.core.session.SessionRepository
import app.cairn.core.session.SignOutOutcome
import app.cairn.core.sync.SyncStatus
import app.cairn.core.sync.SyncWorker
import app.cairn.feature.auth.SignInScreen
import app.cairn.feature.auth.SignInViewModel
import app.cairn.feature.auth.SignOutDialog
import app.cairn.feature.auth.SignOutUiState
import app.cairn.feature.auth.SignOutViewModel
import app.cairn.feature.auth.StartingScreen
import app.cairn.feature.capture.CaptureRepository
import app.cairn.feature.capture.CaptureScreen
import app.cairn.feature.capture.CaptureUiState
import app.cairn.feature.capture.CaptureViewModel

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
 * The whole of navigation, for now.
 *
 * Three destinations chosen by one value. `SessionState` is the only thing that
 * decides which is on screen, so there is no second copy of "is anyone signed
 * in" to drift out of step with the first — signing out anywhere replaces the
 * screen everywhere, because the state it read has changed.
 *
 * `Stale` shows the capture screen, not the sign-in one. A refresh that could
 * not reach the server is not a signed-out user, and a collector in that
 * situation still has a form to fill in.
 */
@Composable
private fun CairnApp(application: CairnApplication) {
    val session: SessionState by application.session.collectAsStateWithLifecycle()
    val sessions = application.sessions

    when (val current = session) {
        SessionState.Unknown -> StartingScreen()
        SessionState.SignedOut ->
            SignInRoute(sessions, application.serverAddress, application.suggestedEmail)
        else -> SignedIn(application, sessions, current.userId.orEmpty())
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

@Composable
private fun SignedIn(
    application: CairnApplication,
    sessions: SessionRepository?,
    userId: String,
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

    CaptureRoute(application.database, userId) {
        TextButton(onClick = signOut::ask, modifier = Modifier.testTag("sign_out")) {
            Text(
                text = "Sign out",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }

    if (prompt != SignOutUiState.Hidden) {
        SignOutDialog(prompt, onConfirm = signOut::confirm, onDismiss = signOut::dismiss)
    }
}

/**
 * Opens the first form of the first study the device has synced.
 *
 * Picking the first of each is a placeholder for navigation, not a decision —
 * study → form list → capture is the next piece of work, and until it exists the
 * app can only open one form.
 *
 * An empty database says one of two things and the rows cannot tell them apart,
 * because both are no rows: the first pull has not landed, or this collector is
 * in no study at all. [SyncStatus] is what separates "wait" from "ask your
 * coordinator", and a sign-out resets it so the answer belongs to whoever is
 * signed in now.
 */
@Composable
private fun CaptureRoute(
    database: CairnDatabase,
    collectedBy: String,
    actions: @Composable RowScope.() -> Unit,
) {
    val synced by SyncStatus.hasCompletedOnce.collectAsStateWithLifecycle()
    val studies by database.studies().observeAll().collectAsStateWithLifecycle(emptyList())
    val study = studies.firstOrNull() ?: return CairnEmptyState(
        heading = "No studies yet",
        body = if (synced) {
            "Ask your coordinator to add you to a study."
        } else {
            "Downloading your studies from the server."
        },
    )

    val forms by database.forms().observeForms(study.id).collectAsStateWithLifecycle(emptyList())
    val form = forms.firstOrNull() ?: return CairnEmptyState(
        heading = "No forms yet",
        body = "Forms appear here once a coordinator publishes one.",
    )

    val viewModel: CaptureViewModel = viewModel(
        key = "${study.id}:${form.id}:$collectedBy",
        factory = viewModelFactory {
            initializer {
                CaptureViewModel(
                    repository = CaptureRepository(database.forms(), database.submissions()),
                    forms = database.forms(),
                    submissions = database.submissions(),
                    studyId = study.id,
                    formId = form.id,
                    collectedBy = collectedBy,
                )
            }
        },
    )
    val state: CaptureUiState by viewModel.uiState.collectAsStateWithLifecycle()

    /*
     * Ask for a sync whenever the queue is not empty, rather than firing one from
     * the save handler. `queuedCount` is read out of Room, so by the time it
     * rises the row is on disk — a worker started from the button could otherwise
     * outrun the write it was started for. The work is unique, so asking twice
     * costs nothing.
     */
    val context = LocalContext.current
    val queuedCount = (state as? CaptureUiState.Editing)?.queuedCount ?: 0
    LaunchedEffect(queuedCount) {
        if (queuedCount > 0) SyncWorker.syncNow(context)
    }

    CaptureScreen(
        state = state,
        onEdit = viewModel::edit,
        onSave = viewModel::save,
        onStartAnother = viewModel::openForm,
        actions = actions,
    )
}
