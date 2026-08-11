package app.cairn.feature.capture

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import app.cairn.core.designsystem.CairnTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the screen to a PNG on the JVM.
 *
 * No emulator and no device: Robolectric's native graphics mode draws the real
 * Compose tree, fonts included, so the output is what the app looks like rather
 * than an approximation. Files land in `build/outputs/roborazzi`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class CaptureScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: CaptureUiState) {
        compose.setContent {
            CairnTheme {
                CaptureScreen(state, onEdit = {}, onSave = {}, onStartAnother = {})
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun empty_form() {
        shoot("capture-empty", previewState())
    }

    @Test
    fun with_errors_and_queue() {
        shoot("capture-errors", previewState(showErrors = true, queuedCount = 3))
    }

    @Test
    fun after_saving() {
        shoot(
            "capture-saved",
            previewState(queuedCount = 1, savedClientId = "cccccccc-0000-0000-0000-000000000001"),
        )
    }

    @Test
    fun cannot_be_opened() {
        shoot(
            "capture-unopenable",
            CaptureUiState.Unopenable(UnopenableReason.SCHEMA_NOT_UNDERSTOOD),
        )
    }
}
