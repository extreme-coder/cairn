package app.cairn.core.designsystem

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What makes drawn path data reviewable.
 *
 * The icons in [CairnIcons] are curves and coordinates, and nobody can tell by
 * reading them whether the eye is lopsided or the gear's teeth land on their
 * marks. This renders every one of them at 24dp and again at 96dp into
 * `build/outputs/roborazzi`, so the answer is a file somebody can open.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class IconSheetScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun icons() {
        compose.setContent { CairnTheme { CairnIconSheet() } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/icons.png")
    }

    /** The components that grew an icon, in one place, since no screen owns them. */
    @Test
    fun components() {
        compose.setContent { CairnTheme { ComponentGallery() } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/components.png")
    }
}
