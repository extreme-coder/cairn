package app.cairn.feature.auth

import androidx.compose.runtime.Composable
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
 * Renders the auth surfaces to PNGs on the JVM, fonts and all. Files land in
 * `build/outputs/roborazzi`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class AuthScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent { CairnTheme { content() } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun signIn(state: SignInUiState) = @Composable {
        SignInScreen(
            state = state,
            onEmail = {},
            onPassword = {},
            onToggleReveal = {},
            onSignIn = {},
        )
    }

    @Test
    fun empty() {
        shoot("signin-empty", signIn(SignInUiState(server = SERVER)))
    }

    @Test
    fun filled() {
        shoot(
            "signin-filled",
            signIn(
                SignInUiState(
                    server = SERVER,
                    email = "adaku@cairn.test",
                    password = "cairn-dev-password",
                ),
            ),
        )
    }

    @Test
    fun refused() {
        shoot(
            "signin-refused",
            signIn(
                SignInUiState(
                    server = SERVER,
                    email = "adaku@cairn.test",
                    password = "wrong",
                    problem = SignInProblem.REFUSED,
                ),
            ),
        )
    }

    @Test
    fun unreachable() {
        shoot(
            "signin-unreachable",
            signIn(
                SignInUiState(
                    server = SERVER,
                    email = "adaku@cairn.test",
                    password = "cairn-dev-password",
                    problem = SignInProblem.UNREACHABLE,
                ),
            ),
        )
    }

    @Test
    fun starting() {
        shoot("starting", { StartingScreen() })
    }

    @Test
    fun signing_out() {
        shoot("signout-confirm") {
            SignOutDialog(SignOutUiState.Confirming, onConfirm = {}, onDismiss = {})
        }
    }

    @Test
    fun signing_out_with_a_queue() {
        shoot("signout-queue") {
            SignOutDialog(SignOutUiState.WouldDiscard(3), onConfirm = {}, onDismiss = {})
        }
    }

    private companion object {
        const val SERVER = "cairn.psych.ubc.ca"
    }
}
