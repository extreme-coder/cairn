package app.cairn.feature.review

import app.cairn.core.database.dao.ReviewCounts
import app.cairn.core.database.dao.ReviewSubmission
import app.cairn.core.database.toFormSchema
import app.cairn.core.model.ReviewState
import app.cairn.core.model.SyncState
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

/**
 * The mapping into what the review screens draw.
 *
 * Pure functions of their arguments, so none of this needs a database, a clock
 * or a device. Every assertion is on finished text, because finished text is
 * what a coordinator reads and what a composable is not allowed to reassemble.
 */
class ReviewUiStateTest {

    private fun row(
        clientId: String = "c-148",
        participantCode: String? = "KL-0148",
        lockedAt: Instant? = null,
        deletedAt: Instant? = null,
        collectedAt: Instant = at(14),
    ) = ReviewSubmission(
        collectedBy = Ids.ADAKU,
        clientId = clientId,
        id = Ids.SERVER_ID,
        formCode = "baseline_intake",
        version = 2,
        participantCode = participantCode,
        collectedAt = collectedAt,
        lockedAt = lockedAt,
        deletedAt = deletedAt,
        syncState = SyncState.UPLOADED,
    ).toRow(now = at(20), zone = UTC)

    @Test
    fun `a row reads as its participant code, its form, its version and its time`() {
        val mapped = row()

        assertEquals("KL-0148", mapped.label)
        assertEquals("Baseline intake v2 · 09:14", mapped.detail)
        assertEquals(ReviewState.OPEN, mapped.state)
    }

    /**
     * Capture attaches no participant yet, so this is the ordinary case today
     * rather than an edge one. An unlabelled row in a review list is one a
     * coordinator cannot discuss with the person who collected it.
     */
    @Test
    fun `a row with no participant falls back to the client id`() {
        assertEquals("C-148", row(participantCode = null).label)
    }

    @Test
    fun `locking and voiding read as their states`() {
        assertEquals(ReviewState.LOCKED, row(lockedAt = at(30)).state)
        assertEquals(ReviewState.VOIDED, row(deletedAt = at(30)).state)
    }

    /**
     * A row can be both: voiding does not lock, so a voided row is still
     * unlocked and can then be locked. The chip has one word and the fact that
     * matters to the study is that the observation is out of the analysis.
     */
    @Test
    fun `a row that is both voided and locked reads as voided`() {
        assertEquals(ReviewState.VOIDED, row(lockedAt = at(30), deletedAt = at(31)).state)
    }

    // ---- Filters ----

    private fun ready(vararg states: ReviewState) = SubmissionsUiState.Ready(
        studyName = "Kluane ground squirrel survey",
        rows = states.mapIndexed { index, state ->
            ReviewRow(Ids.ADAKU, "c-$index", "KL-000$index", "Baseline intake v2 · 09:14", state)
        },
        counts = ReviewCounts(collected = states.size, locked = 1, voided = 1, participants = 3),
    )

    @Test
    fun `a filter shows only its own state and All shows everything`() {
        val state = ready(ReviewState.OPEN, ReviewState.LOCKED, ReviewState.VOIDED)

        assertEquals(3, state.visible.size)
        assertEquals(1, state.copy(filter = ReviewFilter.OPEN).visible.size)
        assertEquals(1, state.copy(filter = ReviewFilter.LOCKED).visible.size)
        assertEquals(1, state.copy(filter = ReviewFilter.VOIDED).visible.size)
    }

    @Test
    fun `every filter is named with the word its state is named with`() {
        assertEquals(listOf("All", "Open", "Locked", "Voided"), ReviewFilter.entries.map { it.label })
    }

    /**
     * Two absences that need two sentences. Saying "nothing collected yet" while
     * four hundred rows sit behind the All chip is a lie a coordinator would act
     * on.
     */
    @Test
    fun `an empty filter is a different absence from an empty study`() {
        val filtered = ready(ReviewState.OPEN).copy(filter = ReviewFilter.VOIDED)
        assertTrue(filtered.emptyBecauseOfFilter)

        val empty = ready().copy(counts = ReviewCounts(0, 0, 0, 0))
        assertFalse(empty.emptyBecauseOfFilter)
        assertTrue(empty.visible.isEmpty())
    }

    /**
     * The unfiltered number is the study's, from the database — the list is
     * capped, so counting the rows on screen would be a claim about the study
     * that a long season makes false.
     */
    @Test
    fun `the unfiltered count is the study's, not the list's`() {
        val state = ready(ReviewState.OPEN, ReviewState.LOCKED, ReviewState.VOIDED)
            .copy(counts = ReviewCounts(collected = 147, locked = 96, voided = 1, participants = 42))

        assertEquals("148 submissions · 96 locked", countLine(state))
    }

    @Test
    fun `a filtered count says how much of what is loaded is on screen`() {
        val state = ready(ReviewState.OPEN, ReviewState.LOCKED, ReviewState.VOIDED)

        assertEquals("1 of 3 shown", countLine(state.copy(filter = ReviewFilter.OPEN)))
    }

    @Test
    fun `one submission is not one submissions`() {
        assertEquals(
            "1 submission · 0 locked",
            countLine(ready(ReviewState.OPEN).copy(counts = ReviewCounts(1, 0, 0, 1))),
        )
    }

    // ---- Which actions a submission offers ----

    @Test
    fun `an open uploaded row offers lock and void, and explains nothing`() {
        val (actions, note) = actionsFor(locked = false, voided = false, uploaded = true, canReview = true)

        assertEquals(listOf(ReviewAction.LOCK, ReviewAction.VOID), actions)
        assertNull(note)
    }

    /**
     * The surprising one, and the reason it is stated rather than left as a
     * missing button: RLS has `locked_at is null` in the `using` clause of the
     * only UPDATE policy, so a locked row matches no client update at all —
     * including one that would unlock it.
     */
    @Test
    fun `a locked row offers nothing and says that unlocking is not possible`() {
        val (actions, note) = actionsFor(locked = true, voided = false, uploaded = true, canReview = true)

        assertTrue(actions.isEmpty())
        assertEquals(
            "Locked submissions cannot be changed. Unlocking is not possible from Cairn.",
            note,
        )
    }

    /**
     * A lock is a write to a row by its server id, and a row that has not been
     * pushed has no server id. Offering the button and failing would be the
     * worse answer.
     */
    @Test
    fun `a row that has not uploaded offers nothing and says why`() {
        val (actions, note) = actionsFor(locked = false, voided = false, uploaded = false, canReview = true)

        assertTrue(actions.isEmpty())
        assertEquals(
            "This submission has not uploaded yet. Locking and voiding happen on the server.",
            note,
        )
    }

    @Test
    fun `a voided row offers restore, because a judgement can be revised`() {
        val (actions, note) = actionsFor(locked = false, voided = true, uploaded = true, canReview = true)

        assertEquals(listOf(ReviewAction.RESTORE), actions)
        assertNull(note)
    }

    /**
     * A viewer reads the whole study and changes none of it. No note either —
     * there is nothing they can do about their role from this screen, and
     * explaining an absent button they were never going to press is noise.
     */
    @Test
    fun `someone without the lock affordance is offered nothing and told nothing`() {
        val (actions, note) = actionsFor(locked = false, voided = false, uploaded = true, canReview = false)

        assertTrue(actions.isEmpty())
        assertNull(note)
    }

    @Test
    fun `a dialog confirms with exactly the words of the button that opened it`() {
        ReviewAction.entries.forEach { action ->
            assertTrue(action.question.endsWith("?"))
            assertEquals(action.label, action.question.removeSuffix("?"))
        }
    }

    // ---- Answers ----

    private fun answersOf(payload: kotlinx.serialization.json.JsonObject) =
        answers(baselineV2.toFormSchema(), payload)

    @Test
    fun `a select renders the label the collector saw, not the value that was stored`() {
        val fields = answersOf(fullPayload())

        assertEquals("Female", fields.single { it.label == "Sex" }.value)
    }

    @Test
    fun `a multi select joins the labels of everything chosen`() {
        assertEquals("Fleas, Ticks", answersOf(fullPayload()).single { it.label == "Ectoparasites" }.value)
    }

    /**
     * `268` and `g` are one fact. Splitting them across a label and a value
     * makes a reviewer reassemble them, which is exactly what this layer exists
     * to stop.
     */
    @Test
    fun `a unit travels with the answer, not with the label`() {
        val mass = answersOf(fullPayload()).single { it.label == "Body mass" }

        assertEquals("268.0 g", mass.value)
        assertTrue(mass.mono)
    }

    /**
     * A field left empty is absent from the payload, never an empty string —
     * that is `CaptureState`'s rule. This is the reading of it: absent says
     * "not answered", and a blank line would say nothing at all.
     */
    @Test
    fun `a field nobody answered says so rather than showing an empty line`() {
        val notes = answersOf(fullPayload()).single { it.label == "Notes" }

        assertEquals("Not answered", notes.value)
        assertFalse(notes.mono)
    }

    @Test
    fun `answers come back in the order the schema declares them`() {
        assertEquals(
            listOf("Body mass", "Sex", "Ectoparasites", "Date first seen", "Notes"),
            answersOf(fullPayload()).map { it.label },
        )
    }

    @Test
    fun `a stored string is shown without the quotes it is stored with`() {
        assertEquals("2026-08-11", answersOf(fullPayload()).single { it.label == "Date first seen" }.value)
    }

    /**
     * Should always be empty: a submission pins the version it was collected
     * under, so by construction its schema declares every key. Shown rather
     * than dropped, because if it is ever non-empty something upstream is wrong
     * and the person deciding whether to lock the row should see it.
     */
    @Test
    fun `a key the pinned schema does not declare is surfaced, not dropped`() {
        val payload = buildJsonObject {
            put("body_mass", 268.0)
            put("burrow_depth", 41)
        }

        assertEquals(listOf("burrow_depth"), extras(baselineV2.toFormSchema(), payload).map { it.label })
        assertTrue(extras(baselineV2.toFormSchema(), fullPayload()).isEmpty())
    }

    @Test
    fun `the chart caption names what is counted and over what period`() {
        assertEquals("Submissions per day · last 14 days", studyProgressCaption(14))
        assertEquals("Submissions per day · last 1 day", studyProgressCaption(1))
    }
}
