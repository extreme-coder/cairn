package app.cairn.feature.capture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import app.cairn.core.designsystem.CairnTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Drives the screen with a hoisted state, exactly as `CaptureViewModel` does but
 * without a database. What is under test is the rendering and the wiring, not
 * the rules, which `CaptureStateTest` already owns.
 */
@RunWith(RobolectricTestRunner::class)
class CaptureScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var saves = 0

    @Composable
    private fun Harness(initial: CaptureUiState.Editing = previewState()) {
        var state by remember { mutableStateOf(initial) }
        CairnTheme {
            CaptureScreen(
                state = state,
                onEdit = { transform ->
                    state = state.copy(capture = transform(state.capture), savedClientId = null)
                },
                onSave = {
                    saves++
                    val attempted = state.capture.attemptSave()
                    state = state.copy(
                        capture = attempted,
                        savedClientId = if (attempted.isValid) attempted.clientId else null,
                    )
                },
                onStartAnother = {},
            )
        }
    }

    @Test
    fun `the form renders from its schema, label and requiredness included`() {
        compose.setContent { Harness() }

        compose.onNodeWithTag("form_title").assertTextContains("Baseline intake")
        compose.onNodeWithTag("form_version").assertTextContains("v3")
        compose.onNodeWithText("OBSERVATION").assertIsDisplayed()
        previewSchema.fields.forEach { spec ->
            compose.onNodeWithTag("field_${spec.key}").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(spec.label).assertIsDisplayed()
        }
        val required = previewSchema.fields.count { it.required }
        compose.onAllNodesWithText("Required").assertCountEquals(required)
        compose.onAllNodesWithText("Optional")
            .assertCountEquals(previewSchema.fields.size - required)
    }

    @Test
    fun `no error is shown before the collector tries to save`() {
        compose.setContent { Harness() }

        compose.onNodeWithTag("error_body_mass").assertDoesNotExist()
        compose.onNodeWithTag("primary_action").assertTextContains("Save submission")
    }

    @Test
    fun `saving an incomplete form shows the error derived from the schema`() {
        compose.setContent { Harness() }

        compose.onNodeWithTag("primary_action").performClick()

        compose.onNodeWithTag("error_body_mass")
            .performScrollTo()
            .assertTextContains("Body mass is required.")
        compose.onNodeWithTag("save_note")
            .assertTextContains("Saved on this device. Uploads when you reconnect.")
    }

    /**
     * The upload clause was cut on 2026-08-11 because no build could keep the
     * promise, and a test pinned its absence. `:core:sync` made that false — a
     * queued submission now really does go up when the network returns — so the
     * pin is inverted: the screen has to say so.
     */
    @Test
    fun `the screen promises the upload the build now performs`() {
        compose.setContent { Harness(previewState(queuedCount = 3)) }

        compose.onNodeWithTag("save_note")
            .assertTextContains("Saved on this device. Uploads when you reconnect.")
    }

    @Test
    fun `an out of range value reports the bound from the schema, not a written string`() {
        compose.setContent { Harness() }

        compose.onNodeWithTag("input_body_mass").performScrollTo().performTextInput("412")
        compose.onNodeWithTag("primary_action").performClick()

        compose.onNodeWithTag("error_body_mass")
            .performScrollTo()
            .assertTextContains("Body mass must be between 90 and 400 g.")
    }

    @Test
    fun `fixing the field clears the error without a second save`() {
        compose.setContent { Harness() }

        compose.onNodeWithTag("input_body_mass").performScrollTo().performTextInput("412")
        compose.onNodeWithTag("primary_action").performClick()
        compose.onNodeWithTag("error_body_mass").assertExists()

        compose.onNodeWithTag("input_body_mass").performScrollTo().performTextReplacement("268")

        compose.onNodeWithTag("error_body_mass").assertDoesNotExist()
    }

    /** The number box must show what was typed, not `268.0` echoed back. */
    @Test
    fun `typing a number does not rewrite the box`() {
        compose.setContent { Harness() }

        compose.onNodeWithTag("input_body_mass").performScrollTo().performTextInput("268")

        compose.onNodeWithTag("input_body_mass").assertTextContains("268")
    }

    @Test
    fun `choosing an option selects it and leaves the others alone`() {
        compose.setContent { Harness() }

        compose.onNodeWithTag("option_sex_male").performScrollTo().performClick()

        compose.onNodeWithTag("option_sex_male").assertIsSelected()
        compose.onNodeWithTag("option_sex_female").assertIsNotSelected()
    }

    @Test
    fun `a checklist option toggles off again`() {
        compose.setContent { Harness() }

        compose.onNodeWithTag("option_behaviours_foraging").performScrollTo().assertIsOn()
        compose.onNodeWithTag("option_behaviours_foraging").performClick()
        compose.onNodeWithTag("option_behaviours_foraging").assertIsOff()
    }

    @Test
    fun `a complete form saves and the screen offers the next one`() {
        compose.setContent { Harness() }

        compose.onNodeWithTag("input_body_mass").performScrollTo().performTextInput("268")
        compose.onNodeWithTag("primary_action").performClick()

        assert(saves == 1)
        compose.onNodeWithTag("primary_action").assertTextContains("New submission")
        compose.onNodeWithTag("discard").assertDoesNotExist()
    }

    @Test
    fun `the queue banner states the count and what happens next`() {
        compose.setContent { Harness(previewState(queuedCount = 3)) }

        compose.onNodeWithTag("queue_banner")
            .assertTextContains("3 queued, uploading when you reconnect.")
    }

    @Test
    fun `a form this build cannot open says so instead of rendering fields`() {
        compose.setContent {
            CairnTheme {
                CaptureScreen(
                    state = CaptureUiState.Unopenable(UnopenableReason.SCHEMA_NOT_UNDERSTOOD),
                    onEdit = {},
                    onSave = {},
                    onStartAnother = {},
                )
            }
        }

        compose.onNodeWithTag("message")
            .assertTextContains(
                "This form needs a newer version of Cairn. Update the app, then open the form again.",
            )
        compose.onNodeWithTag("primary_action").assertDoesNotExist()
    }
}
