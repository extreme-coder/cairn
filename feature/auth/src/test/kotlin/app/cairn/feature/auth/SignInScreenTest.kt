package app.cairn.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import app.cairn.core.designsystem.CairnTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Drives the screen with a hoisted state, the way the ViewModel does but without
 * a session layer. What is under test is the rendering and the wiring.
 */
@RunWith(RobolectricTestRunner::class)
class SignInScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var submits = 0

    @Composable
    private fun Harness(initial: SignInUiState = SignInUiState(server = SERVER)) {
        var state by remember { mutableStateOf(initial) }
        CairnTheme {
            SignInScreen(
                state = state,
                onEmail = { state = state.copy(email = it, problem = null) },
                onPassword = { state = state.copy(password = it, problem = null) },
                onToggleReveal = { state = state.copy(revealPassword = !state.revealPassword) },
                onSignIn = { submits++ },
            )
        }
    }

    @Test
    fun `the server being signed in to is shown`() {
        compose.setContent { Harness() }

        compose.onNodeWithTag("server").assertTextContains(SERVER)
    }

    @Test
    fun `sign in is refused until both fields are filled`() {
        compose.setContent { Harness() }

        compose.onNodeWithTag("sign_in").assertIsNotEnabled()

        compose.onNodeWithTag("email").performTextInput("adaku@cairn.test")
        compose.onNodeWithTag("sign_in").assertIsNotEnabled()

        compose.onNodeWithTag("password").performTextInput("cairn-dev-password")
        compose.onNodeWithTag("sign_in").assertIsEnabled()
    }

    @Test
    fun `tapping sign in submits once`() {
        compose.setContent { Harness() }
        compose.onNodeWithTag("email").performTextInput("adaku@cairn.test")
        compose.onNodeWithTag("password").performTextInput("cairn-dev-password")

        compose.onNodeWithTag("sign_in").performScrollTo().performClick()

        assertEquals(1, submits)
    }

    /** The password is dots until asked for, and the ask is a word, not an icon. */
    @Test
    fun `the password is masked until it is revealed`() {
        compose.setContent { Harness() }
        compose.onNodeWithTag("password").performTextInput("cairn-dev-password")

        compose.onNodeWithText("Show").assertIsDisplayed()
        compose.onNodeWithTag("password").assertTextContains("•".repeat("cairn-dev-password".length))

        compose.onNodeWithTag("reveal").performClick()

        compose.onNodeWithText("Hide").assertIsDisplayed()
        compose.onNodeWithTag("password").assertTextContains("cairn-dev-password")
    }

    @Test
    fun `a refusal names the password`() {
        compose.setContent { Harness(SignInUiState(server = SERVER, problem = SignInProblem.REFUSED)) }

        compose.onNodeWithTag("problem")
            .performScrollTo()
            .assertTextContains("The email address or password is incorrect.")
    }

    /** The other sentence entirely: the password may be perfectly correct. */
    @Test
    fun `an unreachable server names the connection`() {
        compose.setContent {
            Harness(SignInUiState(server = SERVER, problem = SignInProblem.UNREACHABLE))
        }

        compose.onNodeWithTag("problem")
            .performScrollTo()
            .assertTextContains("Cairn cannot reach the server. Check the connection and try again.")
    }

    @Test
    fun `nothing can be typed or submitted while an attempt is in flight`() {
        compose.setContent {
            Harness(
                SignInUiState(
                    server = SERVER,
                    email = "adaku@cairn.test",
                    password = "cairn-dev-password",
                    submitting = true,
                ),
            )
        }

        compose.onNodeWithTag("sign_in").assertIsNotEnabled()
        compose.onNodeWithText("Signing in").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("email").assertIsNotEnabled()
    }

    @Test
    fun `the offline promise is on the screen`() {
        compose.setContent { Harness() }

        compose.onNodeWithText("Submissions save on this device and upload when you reconnect.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    private companion object {
        const val SERVER = "cairn.psych.ubc.ca"
    }
}
