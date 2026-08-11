package app.cairn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

public class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val database = (application as CairnApplication).database
        setContent {
            CairnTheme {
                CaptureRoute(database)
            }
        }
    }
}

@Composable
private fun CaptureRoute(database: CairnDatabase) {
    var seeded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        DevSeed.applyIfEmpty(database)
        seeded = true
    }

    if (!seeded) return

    val viewModel: CaptureViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                CaptureViewModel(
                    repository = CaptureRepository(database.forms(), database.submissions()),
                    forms = database.forms(),
                    submissions = database.submissions(),
                    studyId = DevSeed.STUDY_ID,
                    formId = DevSeed.FORM_ID,
                    collectedBy = DevSeed.COLLECTOR_ID,
                )
            }
        },
    )
    val state: CaptureUiState by viewModel.uiState.collectAsStateWithLifecycle()

    CaptureScreen(
        state = state,
        onEdit = viewModel::edit,
        onSave = viewModel::save,
        onStartAnother = viewModel::openForm,
    )
}
