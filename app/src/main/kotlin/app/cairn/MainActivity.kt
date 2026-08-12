package app.cairn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cairn.core.database.CairnDatabase
import app.cairn.core.designsystem.CairnTheme
import app.cairn.feature.capture.CaptureRepository
import app.cairn.feature.capture.CaptureScreen
import app.cairn.feature.capture.CaptureUiState
import app.cairn.feature.capture.CaptureViewModel
import app.cairn.core.sync.SyncWorker
import kotlinx.coroutines.flow.StateFlow

public class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val application = this.application as CairnApplication
        setContent {
            CairnTheme {
                CaptureRoute(application.database, application.signedInUserId)
            }
        }
    }
}

/**
 * Opens the first form of the first study the device has synced.
 *
 * The ids used to come from `DevSeed`; they now come from Room, which is filled
 * by the pull. Picking the first of each is a placeholder for navigation, not a
 * decision — study → form list → capture is the next piece of work, and until it
 * exists the app can only open one form.
 *
 * Nothing renders until the first sync has landed a study, a form and a signed-in
 * user. That is a real gap rather than a loading state: a loading state is a
 * screen, and screens are not built here without asking.
 */
@Composable
private fun CaptureRoute(database: CairnDatabase, signedInUserId: StateFlow<String?>) {
    val userId: String? by signedInUserId.collectAsStateWithLifecycle()
    val studies by database.studies().observeAll().collectAsStateWithLifecycle(emptyList())
    val study = studies.firstOrNull() ?: return

    val forms by database.forms().observeForms(study.id).collectAsStateWithLifecycle(emptyList())
    val form = forms.firstOrNull() ?: return
    val collectedBy = userId ?: return

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
    )
}
