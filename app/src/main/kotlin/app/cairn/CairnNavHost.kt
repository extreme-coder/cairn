package app.cairn

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import app.cairn.core.designsystem.CairnBottomBar
import app.cairn.core.designsystem.CairnDestination
import app.cairn.core.designsystem.CairnIcons
import app.cairn.core.sync.SyncStatus
import app.cairn.core.sync.SyncWorker
import app.cairn.feature.capture.CaptureRepository
import app.cairn.feature.capture.CaptureScreen
import app.cairn.feature.capture.CaptureViewModel
import app.cairn.feature.collect.CollectScreen
import app.cairn.feature.collect.CollectViewModel
import app.cairn.feature.collect.QueueScreen
import app.cairn.feature.collect.QueueViewModel
import app.cairn.feature.collect.SettingsScreen
import app.cairn.feature.collect.SettingsViewModel
import app.cairn.feature.collect.StudiesScreen
import app.cairn.feature.collect.StudiesViewModel
import app.cairn.feature.review.ProgressScreen
import app.cairn.feature.review.ProgressViewModel
import app.cairn.feature.review.ReviewRepository
import app.cairn.feature.review.SubmissionDetailScreen
import app.cairn.feature.review.SubmissionDetailViewModel
import app.cairn.feature.review.SubmissionsScreen
import app.cairn.feature.review.SubmissionsViewModel
import kotlinx.coroutines.flow.map

/**
 * The signed-in app: three destinations, one of which is a stack.
 *
 * Studies → one study → a form is a stack rather than three tabs because each
 * step narrows the one before it, and because the study a submission belongs to
 * is stamped into the row and cannot be changed afterwards. Queue and Settings
 * are not narrowings of anything, so they are siblings.
 */
@Composable
internal fun CairnNavHost(
    application: CairnApplication,
    userId: String,
    email: String?,
    stale: Boolean,
    onSignOut: () -> Unit,
    controller: NavHostController = rememberNavController(),
) {
    val database = application.database
    val entry by controller.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    val counts by database.submissions().observeCounts(userId)
        .collectAsStateWithLifecycle(app.cairn.core.database.dao.QueueCounts(0, 0, 0))

    /*
     * Ask for a sync whenever anything is waiting, from wherever the collector
     * is standing.
     *
     * This used to live inside the capture screen, which meant a device that had
     * saved a submission and then walked back to the study list waited for the
     * fifteen-minute periodic worker. `pending` is read out of Room, so by the
     * time it rises the row is on disk — a worker started from the save handler
     * could outrun the write it was started for. The work is unique, so asking
     * twice costs nothing.
     */
    val context = LocalContext.current
    LaunchedEffect(counts.pending) {
        if (counts.pending > 0) SyncWorker.syncNow(context)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        // Each screen pads itself for the status bar; the bottom bar pads for
        // the navigation bar. A scaffold inset here would apply both twice.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (CairnDestinations.showsBottomBar(route)) {
                CairnBottomBar(
                    destinations = listOf(
                        CairnDestination("Collect", CairnIcons.Form),
                        CairnDestination("Queue", CairnIcons.Upload, badge = counts.pending),
                        CairnDestination("Settings", CairnIcons.Settings),
                    ),
                    selected = CairnDestinations.tabOf(route),
                    onSelect = { controller.switchTab(it) },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = controller,
            startDestination = CairnDestinations.COLLECT_GRAPH,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        ) {
            navigation(
                startDestination = CairnDestinations.STUDIES,
                route = CairnDestinations.COLLECT_GRAPH,
            ) {
                composable(CairnDestinations.STUDIES) {
                    StudiesRoute(application, userId) { studyId ->
                        controller.navigate(CairnDestinations.study(studyId))
                    }
                }
                composable(CairnDestinations.STUDY_PATTERN) { backStackEntry ->
                    val studyId = backStackEntry.arguments
                        ?.getString(CairnDestinations.ARG_STUDY)
                        .orEmpty()
                    CollectRoute(
                        application = application,
                        userId = userId,
                        studyId = studyId,
                        onForm = { formId ->
                            controller.navigate(CairnDestinations.capture(studyId, formId))
                        },
                        onSubmissions = {
                            controller.navigate(CairnDestinations.submissions(studyId))
                        },
                        onProgress = {
                            controller.navigate(CairnDestinations.progress(studyId))
                        },
                        onBack = { controller.popBackStack() },
                    )
                }
                composable(CairnDestinations.SUBMISSIONS_PATTERN) { backStackEntry ->
                    val studyId = backStackEntry.arguments
                        ?.getString(CairnDestinations.ARG_STUDY)
                        .orEmpty()
                    SubmissionsRoute(
                        application = application,
                        studyId = studyId,
                        onSubmission = { collectedBy, clientId ->
                            controller.navigate(
                                CairnDestinations.submission(studyId, collectedBy, clientId),
                            )
                        },
                        onBack = { controller.popBackStack() },
                    )
                }
                composable(CairnDestinations.PROGRESS_PATTERN) { backStackEntry ->
                    ProgressRoute(
                        application = application,
                        studyId = backStackEntry.arguments
                            ?.getString(CairnDestinations.ARG_STUDY)
                            .orEmpty(),
                        onBack = { controller.popBackStack() },
                    )
                }
                composable(CairnDestinations.SUBMISSION_PATTERN) { backStackEntry ->
                    val arguments = backStackEntry.arguments
                    SubmissionDetailRoute(
                        application = application,
                        userId = userId,
                        studyId = arguments?.getString(CairnDestinations.ARG_STUDY).orEmpty(),
                        collectedBy = arguments?.getString(CairnDestinations.ARG_COLLECTED_BY).orEmpty(),
                        clientId = arguments?.getString(CairnDestinations.ARG_CLIENT).orEmpty(),
                        onBack = { controller.popBackStack() },
                    )
                }
                composable(CairnDestinations.CAPTURE_PATTERN) { backStackEntry ->
                    val arguments = backStackEntry.arguments
                    CaptureRoute(
                        application = application,
                        collectedBy = userId,
                        studyId = arguments?.getString(CairnDestinations.ARG_STUDY).orEmpty(),
                        formId = arguments?.getString(CairnDestinations.ARG_FORM).orEmpty(),
                    ) {
                        IconButton(
                            onClick = { controller.popBackStack() },
                            modifier = Modifier.testTag("close"),
                        ) {
                            Icon(
                                imageVector = CairnIcons.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
            }

            composable(CairnDestinations.QUEUE) {
                QueueRoute(application, userId)
            }

            composable(CairnDestinations.SETTINGS) {
                SettingsRoute(application, userId, email, stale, onSignOut)
            }
        }
    }
}

/**
 * Switching tabs keeps each tab where it was.
 *
 * `saveState`/`restoreState` are what make walking into a study, checking the
 * queue and coming back land on the study rather than at the top of the list —
 * and `launchSingleTop` is what stops tapping Queue six times from building six
 * queues to press back through.
 */
private fun NavHostController.switchTab(index: Int) {
    val destination = when (index) {
        1 -> CairnDestinations.QUEUE
        2 -> CairnDestinations.SETTINGS
        else -> CairnDestinations.COLLECT_GRAPH
    }
    navigate(destination) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun StudiesRoute(
    application: CairnApplication,
    userId: String,
    onStudy: (String) -> Unit,
) {
    val viewModel: StudiesViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                StudiesViewModel(
                    studies = application.database.studies(),
                    userId = userId,
                    syncedOnce = SyncStatus.hasCompletedOnce,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    StudiesScreen(state = state, onStudy = onStudy)
}

@Composable
private fun CollectRoute(
    application: CairnApplication,
    userId: String,
    studyId: String,
    onForm: (String) -> Unit,
    onSubmissions: () -> Unit,
    onProgress: () -> Unit,
    onBack: () -> Unit,
) {
    val database = application.database
    val viewModel: CollectViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                CollectViewModel(
                    studies = database.studies(),
                    members = database.members(),
                    forms = database.forms(),
                    submissions = database.submissions(),
                    studyId = studyId,
                    userId = userId,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    CollectScreen(
        state = state,
        onForm = onForm,
        onBack = onBack,
        onSubmissions = onSubmissions,
        onProgress = onProgress,
    )
}

/**
 * The coordinator's list of everything collected in one study.
 *
 * It asks for `study_id` and nothing about who is reading it: the rows on the
 * device are already what row-level security let this account pull, so a
 * collector opening the same route would see only their own — which is why the
 * route is only offered where the role affords it, and why offering it wrongly
 * would leak nothing.
 */
@Composable
private fun SubmissionsRoute(
    application: CairnApplication,
    studyId: String,
    onSubmission: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    val database = application.database
    val viewModel: SubmissionsViewModel = viewModel(
        key = "submissions:$studyId",
        factory = viewModelFactory {
            initializer {
                SubmissionsViewModel(
                    studies = database.studies(),
                    submissions = database.submissions(),
                    studyId = studyId,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SubmissionsScreen(
        state = state,
        onSubmission = onSubmission,
        onFilter = viewModel::select,
        onBack = onBack,
    )
}

@Composable
private fun ProgressRoute(
    application: CairnApplication,
    studyId: String,
    onBack: () -> Unit,
) {
    val database = application.database
    val viewModel: ProgressViewModel = viewModel(
        key = "progress:$studyId",
        factory = viewModelFactory {
            initializer {
                ProgressViewModel(
                    studies = database.studies(),
                    submissions = database.submissions(),
                    studyId = studyId,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ProgressScreen(state = state, onBack = onBack)
}

/**
 * One submission, and the only route in the app that hands a feature the
 * network.
 *
 * `application.remote` is null on a build with no server configured, and
 * [ReviewRepository] answers every action offline in that case rather than this
 * route branching on it.
 */
@Composable
private fun SubmissionDetailRoute(
    application: CairnApplication,
    userId: String,
    studyId: String,
    collectedBy: String,
    clientId: String,
    onBack: () -> Unit,
) {
    val database = application.database
    val viewModel: SubmissionDetailViewModel = viewModel(
        key = "submission:$collectedBy/$clientId",
        factory = viewModelFactory {
            initializer {
                SubmissionDetailViewModel(
                    submissions = database.submissions(),
                    members = database.members(),
                    studyId = studyId,
                    collectedBy = collectedBy,
                    clientId = clientId,
                    userId = userId,
                    repository = ReviewRepository(database.submissions(), application.remote),
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SubmissionDetailScreen(
        state = state,
        onAsk = viewModel::ask,
        onConfirm = viewModel::confirm,
        onDismiss = viewModel::dismiss,
        onBack = onBack,
    )
}

@Composable
private fun QueueRoute(application: CairnApplication, userId: String) {
    val context = LocalContext.current
    val viewModel: QueueViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                QueueViewModel(
                    submissions = application.database.submissions(),
                    userId = userId,
                    requestSync = { SyncWorker.syncNow(context) },
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    QueueScreen(
        state = state,
        onUploadNow = viewModel::uploadNow,
        onToggleUploaded = viewModel::toggleUploaded,
    )
}

@Composable
private fun SettingsRoute(
    application: CairnApplication,
    userId: String,
    email: String?,
    stale: Boolean,
    onSignOut: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(
        key = "settings:$userId",
        factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    submissions = application.database.submissions(),
                    userId = userId,
                    server = application.serverAddress,
                    version = application.version,
                    // Read off the session flow rather than the value captured
                    // when this route was composed: a refresh can succeed or
                    // fail while Settings is open, and this screen says so.
                    email = application.session.map { it.email ?: email },
                    stale = application.session.map { it is app.cairn.core.network.SessionState.Stale },
                    lastSyncedAt = application.syncLog?.lastSyncedAt
                        ?: kotlinx.coroutines.flow.flowOf(null),
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreen(state = state, onSignOut = onSignOut)
}

/**
 * Opens one form of one study.
 *
 * Both ids come off the route now. Until navigation existed this route picked
 * the first form of the first study, which was a placeholder and read on screen
 * as an app that could only do one thing.
 */
@Composable
private fun CaptureRoute(
    application: CairnApplication,
    collectedBy: String,
    studyId: String,
    formId: String,
    actions: @Composable RowScope.() -> Unit,
) {
    val database = application.database
    val viewModel: CaptureViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                CaptureViewModel(
                    repository = CaptureRepository(database.forms(), database.submissions()),
                    forms = database.forms(),
                    submissions = database.submissions(),
                    studyId = studyId,
                    formId = formId,
                    collectedBy = collectedBy,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    CaptureScreen(
        state = state,
        onEdit = viewModel::edit,
        onSave = viewModel::save,
        onStartAnother = viewModel::openForm,
        actions = actions,
    )
}
