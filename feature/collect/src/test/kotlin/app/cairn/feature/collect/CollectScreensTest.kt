package app.cairn.feature.collect

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.cairn.core.database.dao.QueueCounts
import app.cairn.core.designsystem.CairnTheme
import app.cairn.core.model.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The four screens, rendered.
 *
 * Stateless composables driven by a hand-built state, so what is under test is
 * the rendering and the wiring — the rules already belong to the ViewModel
 * tests. Nodes below the fold are scrolled to first: a Compose assertion on an
 * off-screen node fails with a message about the component rather than about
 * scrolling.
 */
@RunWith(RobolectricTestRunner::class)
class CollectScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { CairnTheme { content() } }
    }

    // ---- Studies ----

    @Test
    fun `a study row shows its name, its contents, its role and its status`() {
        show { StudiesScreen(state = previewStudies(), onStudy = {}) }

        compose.onNodeWithText("Kluane ground squirrel survey").assertIsDisplayed()
        compose.onNodeWithText("3 forms · 148 submissions").assertIsDisplayed()
        compose.onNodeWithText("Collector").assertIsDisplayed()
        // Inside a merged clickable row, so the unmerged tree is where the tag lives.
        compose.onNodeWithTag("status_study-kluane", useUnmergedTree = true)
            .assertTextEquals("6 queued")
    }

    @Test
    fun `tapping a study opens that study and not another`() {
        var opened: String? = null
        show { StudiesScreen(state = previewStudies(), onStudy = { opened = it }) }

        compose.onNodeWithTag("study_study-peel").performScrollTo().performClick()

        assertEquals("study-peel", opened)
    }

    @Test
    fun `the app bar counts the studies`() {
        show { StudiesScreen(state = previewStudies(), onStudy = {}) }

        compose.onNodeWithTag("study_count").assertTextEquals("3 studies")
    }

    /**
     * Both are zero rows. Only whether a sync has finished tells them apart, and
     * saying the wrong one sends a collector to make a phone call they did not
     * need to make.
     */
    @Test
    fun `a device still downloading says so`() {
        show { StudiesScreen(state = StudiesUiState.Empty(synced = false), onStudy = {}) }

        compose.onNodeWithTag("empty_body")
            .assertTextEquals("Downloading your studies from the server.")
    }

    @Test
    fun `a synced device with no studies says who to ask`() {
        show { StudiesScreen(state = StudiesUiState.Empty(synced = true), onStudy = {}) }

        compose.onNodeWithTag("empty_body")
            .assertTextEquals("A PI adds you to a study. Ask them for access.")
    }

    // ---- Collect ----

    @Test
    fun `a form row shows its title and the version a submission would be collected under`() {
        show { CollectScreen(state = previewCollect(), onForm = {}, onBack = {}) }

        compose.onNodeWithTag("study_name").assertTextEquals("Kluane ground squirrel survey")
        compose.onNodeWithText("Baseline intake").assertIsDisplayed()
        compose.onNodeWithText("12 fields").assertIsDisplayed()
        compose.onNodeWithText("v3").assertIsDisplayed()
    }

    @Test
    fun `tapping a form opens it`() {
        var opened: String? = null
        show { CollectScreen(state = previewCollect(), onForm = { opened = it }, onBack = {}) }

        compose.onNodeWithTag("form_form-weekly").performScrollTo().performClick()

        assertEquals("form-weekly", opened)
    }

    /**
     * Tapping through to a capture screen that immediately fails is a worse
     * answer than not offering the tap. The row still appears, carrying the
     * reason.
     */
    @Test
    fun `a form with no published version cannot be opened`() {
        var opened: String? = null
        show { CollectScreen(state = previewCollect(), onForm = { opened = it }, onBack = {}) }

        compose.onNodeWithTag("form_form-trap").performScrollTo().performClick()

        assertNull(opened)
        compose.onNodeWithText("No published version yet").assertIsDisplayed()
    }

    @Test
    fun `the queue banner states the count and what happens next`() {
        show { CollectScreen(state = previewCollect(), onForm = {}, onBack = {}) }

        compose.onNodeWithTag("queue_banner").assertExists()
        compose.onNodeWithText("6 queued, uploading when you reconnect.").assertIsDisplayed()
    }

    /** Furniture stops being read. The banner is present only when it has a fact to state. */
    @Test
    fun `with nothing queued there is no banner`() {
        show {
            CollectScreen(
                state = previewCollect().copy(pendingCount = 0),
                onForm = {},
                onBack = {},
            )
        }

        compose.onNodeWithTag("queue_banner").assertDoesNotExist()
    }

    @Test
    fun `back goes to the studies list`() {
        var back = 0
        show { CollectScreen(state = previewCollect(), onForm = {}, onBack = { back++ }) }

        compose.onNodeWithTag("back").performClick()

        assertEquals(1, back)
    }

    @Test
    fun `a study removed from the device says so rather than looking empty`() {
        show { CollectScreen(state = CollectUiState.Gone, onForm = {}, onBack = {}) }

        compose.onNodeWithTag("empty_heading").assertTextEquals("This study is gone")
    }

    // ---- Queue ----

    @Test
    fun `the queue counts queued, failed and uploaded`() {
        show { QueueScreen(state = previewQueue(), onUploadNow = {}, onToggleUploaded = {}) }

        compose.onNodeWithTag("stat_queued").assertTextEquals("4")
        compose.onNodeWithTag("stat_failed").assertTextEquals("1")
        compose.onNodeWithTag("stat_uploaded").assertTextEquals("148")
    }

    @Test
    fun `queued and failed rows are in separate sections`() {
        show { QueueScreen(state = previewQueue(), onUploadNow = {}, onToggleUploaded = {}) }

        compose.onNodeWithTag("queued_rows").assertExists()
        compose.onNodeWithTag("failed_rows").assertExists()
        compose.onNodeWithText("KL-0141").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `upload now is offered when something is waiting`() {
        var uploads = 0
        show { QueueScreen(state = previewQueue(), onUploadNow = { uploads++ }, onToggleUploaded = {}) }

        compose.onNodeWithTag("upload_now").assertIsEnabled().performClick()

        assertEquals(1, uploads)
    }

    /** A button that does nothing when pressed teaches people to stop believing the screen. */
    @Test
    fun `upload now is refused when nothing is waiting`() {
        show {
            QueueScreen(
                state = QueueUiState(counts = QueueCounts(0, 0, 12), loaded = true),
                onUploadNow = {},
                onToggleUploaded = {},
            )
        }

        compose.onNodeWithTag("upload_now").assertIsNotEnabled()
    }

    @Test
    fun `the uploaded list is offered by count and hides again`() {
        show { QueueScreen(state = previewQueue(), onUploadNow = {}, onToggleUploaded = {}) }

        compose.onNodeWithTag("toggle_uploaded").performScrollTo()
            .assertTextEquals("Show all 148 uploaded")
    }

    @Test
    fun `showing the uploaded list changes what the control offers`() {
        show {
            QueueScreen(
                state = previewQueue().copy(
                    showingUploaded = true,
                    uploaded = listOf(
                        SubmissionRow("c-1", "KL-0100", "Trap check v5 · 07:00", SyncState.UPLOADED),
                    ),
                ),
                onUploadNow = {},
                onToggleUploaded = {},
            )
        }

        compose.onNodeWithTag("toggle_uploaded").performScrollTo().assertTextEquals("Hide uploaded")
        compose.onNodeWithTag("uploaded_rows").assertExists()
    }

    @Test
    fun `the retry promise is stated where the button is`() {
        show { QueueScreen(state = previewQueue(), onUploadNow = {}, onToggleUploaded = {}) }

        compose.onNodeWithTag("upload_note")
            .assertTextEquals("Uploads retry automatically. Retrying never creates a duplicate.")
    }

    @Test
    fun `a device with nothing on it says what will appear here`() {
        show {
            QueueScreen(
                state = QueueUiState(loaded = true),
                onUploadNow = {},
                onToggleUploaded = {},
            )
        }

        compose.onNodeWithTag("empty_heading").assertTextEquals("Nothing collected yet")
    }

    // ---- Settings ----

    @Test
    fun `settings says who is signed in, where, and when it last synced`() {
        show { SettingsScreen(state = previewSettings(), onSignOut = {}) }

        compose.onNodeWithTag("initials").assertTextEquals("AO")
        compose.onNodeWithTag("signed_in_as").assertTextEquals("adaku.obi@cairn.test")
        compose.onNodeWithTag("server").performScrollTo().assertTextEquals("cairn.psych.ubc.ca")
        compose.onNodeWithTag("last_synced").performScrollTo().assertTextEquals("14 minutes ago")
        compose.onNodeWithTag("version").performScrollTo().assertTextEquals("0.1.0 (1)")
    }

    /** The number the sign-out guard will refuse over, stated before the tap. */
    @Test
    fun `settings warns before a sign-out that would be refused`() {
        show {
            SettingsScreen(
                state = previewSettings().copy(pendingCount = 3),
                onSignOut = {},
            )
        }

        compose.onNodeWithTag("pending_warning").performScrollTo()
            .assertTextEquals("3 submissions have not uploaded yet.")
    }

    @Test
    fun `one unsent submission reads as one, not as a plural`() {
        show { SettingsScreen(state = previewSettings().copy(pendingCount = 1), onSignOut = {}) }

        compose.onNodeWithTag("pending_warning").performScrollTo()
            .assertTextEquals("1 submission has not uploaded yet.")
    }

    @Test
    fun `with nothing unsent there is no warning`() {
        show { SettingsScreen(state = previewSettings(), onSignOut = {}) }

        compose.onNodeWithTag("pending_warning").assertDoesNotExist()
    }

    @Test
    fun `signing out is asked for here and confirmed elsewhere`() {
        var asked = 0
        show { SettingsScreen(state = previewSettings(), onSignOut = { asked++ }) }

        compose.onNodeWithTag("sign_out").performScrollTo().performClick()

        assertEquals(1, asked)
    }

    /** Capture keeps working while a refresh cannot reach the server, so this is not an error. */
    @Test
    fun `a stale session is described rather than alarmed about`() {
        show { SettingsScreen(state = previewSettings().copy(stale = true), onSignOut = {}) }

        compose.onNodeWithText("Signed in, waiting for the server").assertIsDisplayed()
    }
}
