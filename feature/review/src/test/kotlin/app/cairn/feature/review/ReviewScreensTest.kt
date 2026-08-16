package app.cairn.feature.review

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.cairn.core.database.dao.ReviewCounts
import app.cairn.core.designsystem.CairnTheme
import app.cairn.core.model.ReviewState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The three screens, rendered.
 *
 * Stateless composables driven by a hand-built state, so what is under test is
 * the rendering and the wiring — the rules belong to the ViewModel tests. Nodes
 * below the fold are scrolled to first: a Compose assertion on an off-screen
 * node fails with a message about the component rather than about scrolling.
 */
@RunWith(RobolectricTestRunner::class)
class ReviewScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { CairnTheme { content() } }
    }

    private fun submissions(
        state: SubmissionsUiState = previewSubmissions(),
        onSubmission: (String, String) -> Unit = { _, _ -> },
        onFilter: (ReviewFilter) -> Unit = {},
    ) = show {
        SubmissionsScreen(state = state, onSubmission = onSubmission, onFilter = onFilter, onBack = {})
    }

    private fun detail(
        state: DetailUiState = previewDetail(),
        onAsk: (ReviewAction) -> Unit = {},
        onConfirm: () -> Unit = {},
        onDismiss: () -> Unit = {},
    ) = show {
        SubmissionDetailScreen(
            state = state,
            onAsk = onAsk,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            onBack = {},
        )
    }

    // ---- Submissions ----

    @Test
    fun `a submission row shows its code, its form and what has been done to it`() {
        submissions()

        compose.onNodeWithTag("screen_title").assertTextEquals("Submissions")
        compose.onNodeWithText("KL-0148").assertIsDisplayed()
        compose.onNodeWithText("Baseline intake v3 · 09:14").assertIsDisplayed()
        // The whole row is one merged node, so the state word is asserted as
        // part of it — "Open" alone also matches the filter chip.
        compose.onNodeWithTag("row_c-148").assertTextContains("Open")
        compose.onNodeWithTag("row_c-141").performScrollTo().assertTextContains("Voided")
    }

    @Test
    fun `tapping a row opens that submission and not another`() {
        var opened: Pair<String, String>? = null
        submissions(onSubmission = { by, id -> opened = by to id })

        compose.onNodeWithTag("row_c-146").performScrollTo().performClick()

        assertEquals("u-marta" to "c-146", opened)
    }

    @Test
    fun `every filter is offered and tapping one asks for it`() {
        var chosen: ReviewFilter? = null
        submissions(onFilter = { chosen = it })

        ReviewFilter.entries.forEach { compose.onNodeWithTag("filter_${it.name.lowercase()}").assertIsDisplayed() }
        compose.onNodeWithTag("filter_locked").performClick()

        assertEquals(ReviewFilter.LOCKED, chosen)
    }

    @Test
    fun `the count line says how much the study holds and how much is locked`() {
        submissions()

        compose.onNodeWithTag("count_line").assertTextEquals("148 submissions · 96 locked")
    }

    @Test
    fun `a filter that matches nothing says so without claiming the study is empty`() {
        submissions(
            state = previewSubmissions().copy(
                rows = previewSubmissions().rows.filter { it.state == ReviewState.OPEN },
                filter = ReviewFilter.VOIDED,
            ),
        )

        compose.onNodeWithTag("empty_heading").assertTextEquals("Nothing voided here")
    }

    @Test
    fun `a study with nothing in it says what will appear`() {
        submissions(
            state = previewSubmissions().copy(rows = emptyList(), counts = ReviewCounts(0, 0, 0, 0)),
        )

        compose.onNodeWithTag("empty_heading").assertTextEquals("Nothing collected yet")
        compose.onNodeWithTag("empty_body")
            .assertTextEquals("Submissions appear here as collectors upload them.")
    }

    @Test
    fun `a study removed from the device says so rather than showing an empty list`() {
        submissions(state = SubmissionsUiState.Gone)

        compose.onNodeWithTag("empty_heading").assertTextEquals("This study is gone")
    }

    // ---- One submission ----

    @Test
    fun `the detail screen shows what the submission is and when it was collected`() {
        detail()

        compose.onNodeWithTag("submission_label").assertTextEquals("KL-0148")
        compose.onNodeWithTag("version").assertTextEquals("v3")
        compose.onNodeWithTag("collected_at").assertTextEquals("13 Aug 2026 · 09:14")
        compose.onNodeWithText("Kluane ground squirrel survey").assertIsDisplayed()
    }

    @Test
    fun `every answer is shown against the label the collector saw`() {
        detail()

        // Sentence case, the way the capture screen sets a field label — a
        // running head would uppercase it and a coordinator checking an answer
        // should read the words the collector read.
        compose.onNodeWithTag("answer_Body mass").performScrollTo()
        compose.onNodeWithText("Body mass").assertIsDisplayed()
        compose.onNodeWithText("268 g").assertIsDisplayed()

        compose.onNodeWithTag("answer_Sex").performScrollTo()
        compose.onNodeWithText("Female").assertIsDisplayed()

        compose.onNodeWithTag("answer_Ectoparasites").performScrollTo()
        compose.onNodeWithText("Fleas, ticks").assertIsDisplayed()

        compose.onNodeWithTag("answer_Notes").performScrollTo()
        compose.onNodeWithText("Not answered").assertIsDisplayed()
    }

    @Test
    fun `a coordinator is offered both actions`() {
        detail()

        compose.onNodeWithTag("action_lock").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("action_void").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a locked submission is offered neither, and says why`() {
        detail(state = previewLockedDetail())

        compose.onNodeWithTag("action_lock").assertDoesNotExist()
        compose.onNodeWithTag("action_void").assertDoesNotExist()
        compose.onNodeWithTag("note").performScrollTo()
            .assertTextEquals("Locked submissions cannot be changed. Unlocking is not possible from Cairn.")
    }

    @Test
    fun `tapping lock asks rather than locking`() {
        var asked: ReviewAction? = null
        detail(onAsk = { asked = it })

        compose.onNodeWithTag("action_lock").performScrollTo().performClick()

        assertEquals(ReviewAction.LOCK, asked)
    }

    /**
     * `DESIGN.md`: a heading ending in `?`, one line stating the consequence,
     * and a confirming action carrying exactly the words of the button that
     * opened it.
     */
    @Test
    fun `the lock dialog states the consequence and confirms in the same words`() {
        detail(state = previewDetail().copy(confirming = ReviewAction.LOCK))

        compose.onNodeWithTag("dialog_title").assertTextEquals("Lock submission?")
        compose.onNodeWithTag("dialog_body")
            .assertTextEquals("It can no longer be amended, by anyone, and it cannot be unlocked from Cairn.")
        compose.onNodeWithTag("dialog_confirm").assertTextEquals("Lock submission")
        compose.onNodeWithTag("dialog_cancel").assertTextEquals("Cancel")
    }

    @Test
    fun `the void dialog says the row is kept and the change can be undone`() {
        detail(state = previewDetail().copy(confirming = ReviewAction.VOID))

        compose.onNodeWithTag("dialog_title").assertTextEquals("Void submission?")
        compose.onNodeWithTag("dialog_body")
            .assertTextEquals("It stays in the record and leaves the analysis. You can restore it afterwards.")
    }

    @Test
    fun `confirming and cancelling reach the two different handlers`() {
        var confirmed = 0
        var dismissed = 0
        detail(
            state = previewDetail().copy(confirming = ReviewAction.VOID),
            onConfirm = { confirmed++ },
            onDismiss = { dismissed++ },
        )

        compose.onNodeWithTag("dialog_confirm").performClick()
        assertEquals(1, confirmed)

        compose.onNodeWithTag("dialog_cancel").performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun `a refusal is shown in the server's own words`() {
        detail(state = previewDetail().copy(problem = "The server did not change this submission."))

        compose.onNodeWithTag("problem").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("The server did not change this submission.").assertIsDisplayed()
    }

    @Test
    fun `a write in flight says so and the buttons stop accepting taps`() {
        detail(state = previewDetail().copy(working = true))

        compose.onNodeWithTag("working").performScrollTo().assertTextEquals("Asking the server…")
        compose.onNodeWithTag("action_lock").assertIsNotEnabled()
    }

    @Test
    fun `a schema this build cannot read still shows the submission's provenance`() {
        detail(state = DetailUiState.Unreadable(previewHeader()))

        compose.onNodeWithTag("submission_label").assertTextEquals("KL-0148")
        compose.onNodeWithTag("unreadable").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a submission removed from the device says so`() {
        detail(state = DetailUiState.Gone)

        compose.onNodeWithTag("empty_heading").assertTextEquals("This submission is gone")
    }

    @Test
    fun `a key the pinned schema does not declare is shown under its own heading`() {
        detail(
            state = previewDetail().copy(
                extras = listOf(AnsweredField("burrow_depth", "41", mono = true)),
            ),
        )

        compose.onNodeWithText("NOT IN THIS FORM VERSION").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("extras").performScrollTo().assertIsDisplayed()
    }

    // ---- Progress ----

    @Test
    fun `progress shows three numbers and a captioned chart`() {
        show { ProgressScreen(state = previewProgress(), onBack = {}) }

        compose.onNodeWithTag("screen_title").assertTextEquals("Progress")
        compose.onNodeWithTag("stat_collected").assertTextEquals("147")
        compose.onNodeWithTag("stat_locked").assertTextEquals("96")
        compose.onNodeWithTag("stat_voided").assertTextEquals("1")
        compose.onNodeWithTag("chart_caption", useUnmergedTree = true)
            .assertTextEquals("SUBMISSIONS PER DAY · LAST 14 DAYS")
    }

    @Test
    fun `the chart says out loud what it draws`() {
        show { ProgressScreen(state = previewProgress(), onBack = {}) }

        compose.onNodeWithTag("chart", useUnmergedTree = true)
            .assert(hasContentDescription("most on 11 Aug with 14", substring = true))
    }

    @Test
    fun `progress names the participants and what is still open`() {
        show { ProgressScreen(state = previewProgress(), onBack = {}) }

        compose.onNodeWithTag("participants").performScrollTo()
            .assertTextEquals("42 participants · 51 still open")
    }

    @Test
    fun `a study with nothing collected says what will appear here`() {
        show {
            ProgressScreen(
                state = ProgressUiState(studyName = "Kluane", caption = "x", loaded = true),
                onBack = {},
            )
        }

        compose.onNodeWithTag("empty_heading").assertTextEquals("Nothing collected yet")
    }

    @Test
    fun `nothing is drawn before the database has answered`() {
        show { ProgressScreen(state = ProgressUiState(), onBack = {}) }

        compose.onNodeWithTag("summary").assertDoesNotExist()
        compose.onNodeWithTag("empty_heading").assertDoesNotExist()
    }

    @Test
    fun `every action is a word rather than a glyph`() {
        assertNull(ReviewAction.entries.firstOrNull { it.label.isBlank() })
    }
}
