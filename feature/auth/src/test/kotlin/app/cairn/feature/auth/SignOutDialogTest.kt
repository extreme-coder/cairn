package app.cairn.feature.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.cairn.core.designsystem.CairnTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The dialog states the consequence in the same words as the button that carries
 * it out, and quantifies what is about to be lost. Both are voice rules, and
 * this is the one action in the app that can destroy an observation.
 */
@RunWith(RobolectricTestRunner::class)
class SignOutDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private var confirms = 0
    private var dismissals = 0

    private fun show(state: SignOutUiState) {
        compose.setContent {
            CairnTheme {
                SignOutDialog(state, onConfirm = { confirms++ }, onDismiss = { dismissals++ })
            }
        }
    }

    @Test
    fun `hidden draws nothing`() {
        show(SignOutUiState.Hidden)

        compose.onNodeWithTag("confirm_sign_out").assertDoesNotExist()
    }

    @Test
    fun `the first ask states what signing out costs`() {
        show(SignOutUiState.Confirming)

        compose.onNodeWithTag("sign_out_body")
            .assertTextContains("This device keeps nothing after you sign out. " +
                "Everything already uploaded stays on the server.")
        compose.onNodeWithTag("confirm_sign_out").assertTextContains("Sign out")
    }

    @Test
    fun `the warning counts what would be deleted`() {
        show(SignOutUiState.WouldDiscard(3))

        compose.onNodeWithTag("pending_warning")
            .assertTextContains("3 submissions have not uploaded yet. Signing out deletes them from this device.")
    }

    @Test
    fun `one queued submission reads in the singular`() {
        show(SignOutUiState.WouldDiscard(1))

        compose.onNodeWithTag("pending_warning")
            .assertTextContains("1 submission has not uploaded yet. Signing out deletes it from this device.")
    }

    /** The button carries the consequence once the consequence is deletion. */
    @Test
    fun `the destructive confirmation says what it destroys`() {
        show(SignOutUiState.WouldDiscard(2))

        compose.onNodeWithTag("confirm_sign_out").assertTextContains("Delete and sign out")
    }

    @Test
    fun `cancel and confirm report separately`() {
        show(SignOutUiState.Confirming)

        compose.onNodeWithTag("cancel_sign_out").performClick()
        compose.onNodeWithTag("confirm_sign_out").performClick()

        assertEquals(1, dismissals)
        assertEquals(1, confirms)
    }

    @Test
    fun `there is always a way out`() {
        show(SignOutUiState.WouldDiscard(4))

        compose.onNodeWithText("Cancel").assertIsDisplayed()
    }
}
