package app.cairn.feature.review

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import app.cairn.core.database.dao.ReviewCounts
import app.cairn.core.designsystem.CairnTheme
import app.cairn.core.model.ReviewState
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders each review screen to a PNG on the JVM.
 *
 * These are for looking at as much as for regression. The chart in particular is
 * drawn on a `Canvas` and can be subtly wrong in a way no assertion catches and
 * a person spots in a second — which is how the hand-drawn gear that rendered as
 * a sun was found. Files land in `build/outputs/roborazzi`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ReviewScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent { CairnTheme { content() } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun submissions(name: String, state: SubmissionsUiState) = shoot(name) {
        SubmissionsScreen(state = state, onSubmission = { _, _ -> }, onFilter = {}, onBack = {})
    }

    private fun detail(name: String, state: DetailUiState) = shoot(name) {
        SubmissionDetailScreen(
            state = state,
            onAsk = {},
            onConfirm = {},
            onDismiss = {},
            onBack = {},
        )
    }

    @Test
    fun submissions() {
        submissions("submissions", previewSubmissions())
    }

    @Test
    fun submissions_filtered() {
        submissions("submissions-filtered", previewSubmissions().copy(filter = ReviewFilter.OPEN))
    }

    @Test
    fun submissions_with_none() {
        submissions(
            "submissions-empty",
            previewSubmissions().copy(rows = emptyList(), counts = ReviewCounts(0, 0, 0, 0)),
        )
    }

    @Test
    fun submissions_when_the_study_is_gone() {
        submissions("submissions-gone", SubmissionsUiState.Gone)
    }

    @Test
    fun submission() {
        detail("submission", previewDetail())
    }

    @Test
    fun submission_with_lock_dialog() {
        detail("submission-lock-dialog", previewDetail().copy(confirming = ReviewAction.LOCK))
    }

    @Test
    fun submission_locked() {
        detail("submission-locked", previewLockedDetail())
    }

    @Test
    fun submission_voided() {
        detail(
            "submission-voided",
            previewDetail().copy(
                header = previewHeader(ReviewState.VOIDED),
                actions = listOf(ReviewAction.RESTORE),
            ),
        )
    }

    @Test
    fun submission_refused() {
        detail(
            "submission-refused",
            previewDetail().copy(
                problem = "The server did not change this submission. It may already be locked, " +
                    "or your role in this study may not allow it.",
            ),
        )
    }

    @Test
    fun progress() {
        shoot("progress") { ProgressScreen(state = previewProgress(), onBack = {}) }
    }

    @Test
    fun progress_with_none() {
        shoot("progress-empty") {
            ProgressScreen(
                state = ProgressUiState(
                    studyName = "Kluane ground squirrel survey",
                    caption = studyProgressCaption(14),
                    loaded = true,
                ),
                onBack = {},
            )
        }
    }
}
