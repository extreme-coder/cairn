package app.cairn.feature.review

import app.cairn.core.database.dao.ReviewCounts
import app.cairn.core.database.dao.ReviewSubmission
import app.cairn.core.model.FieldSpec
import app.cairn.core.model.FieldType
import app.cairn.core.model.FormSchema
import app.cairn.core.model.ReviewState
import app.cairn.core.model.collectedLabel
import app.cairn.core.model.formTitle
import app.cairn.core.model.plural
import app.cairn.core.model.reviewStateOf
import app.cairn.core.model.submissionLabel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.ZoneId
import kotlin.time.Instant

/**
 * What the coordinator's three screens draw.
 *
 * Finished text, the same rule `:feature:collect` follows: a composable here
 * reads [ReviewRow.detail] and puts it on screen. It does not know that a detail
 * line is a form title, a version and a time, and it never formats a date. That
 * is what makes these screens renderable in a preview and assertable in a JVM
 * test with no database, and it is where the copy rules are enforced — a
 * sentence assembled inside a composable is a sentence nothing tests.
 */

/** A submission on the Submissions list. */
public data class ReviewRow(
    public val collectedBy: String,
    public val clientId: String,
    public val label: String,
    public val detail: String,
    public val state: ReviewState,
)

/**
 * The filter chips above the list.
 *
 * A coordinator's question is almost never "show me everything" — it is "what
 * have I not looked at yet", which is [OPEN]. The chips are the whole navigation
 * of a list that will be thousands of rows long in a real season.
 */
public enum class ReviewFilter(public val state: ReviewState?) {
    ALL(null),
    OPEN(ReviewState.OPEN),
    LOCKED(ReviewState.LOCKED),
    VOIDED(ReviewState.VOIDED),
    ;

    public val label: String get() = state?.label ?: "All"

    public fun accepts(row: ReviewRow): Boolean = state == null || row.state == state
}

public sealed interface SubmissionsUiState {

    /** The database has not answered yet. Distinct from having answered "none". */
    public data object Loading : SubmissionsUiState

    /** The study was removed from this device while its screen was open. */
    public data object Gone : SubmissionsUiState

    public data class Ready(
        public val studyName: String,
        public val rows: List<ReviewRow>,
        public val filter: ReviewFilter = ReviewFilter.ALL,
        public val counts: ReviewCounts = ReviewCounts(0, 0, 0, 0),
    ) : SubmissionsUiState {

        public val visible: List<ReviewRow> get() = rows.filter(filter::accepts)

        /**
         * Two different absences, and they need different sentences. Nothing has
         * been collected at all, or nothing matches the chip that is selected —
         * and telling someone "nothing collected yet" while 400 rows sit behind
         * the All chip is a lie they will act on.
         */
        public val emptyBecauseOfFilter: Boolean get() = rows.isNotEmpty() && visible.isEmpty()
    }
}

/** One answer on the detail screen. */
public data class AnsweredField(
    public val label: String,
    public val value: String,
    /** Codes, numbers and dates are read character by character, so they set in mono. */
    public val mono: Boolean = false,
)

/** What a coordinator can do to a submission from here. */
public enum class ReviewAction {
    LOCK,
    VOID,
    RESTORE,
    ;

    /** The button, and — per `DESIGN.md` — the dialog's confirming action, word for word. */
    public val label: String
        get() = when (this) {
            LOCK -> "Lock submission"
            VOID -> "Void submission"
            RESTORE -> "Restore submission"
        }

    public val question: String
        get() = when (this) {
            LOCK -> "Lock submission?"
            VOID -> "Void submission?"
            RESTORE -> "Restore submission?"
        }

    /**
     * One line stating the consequence. The lock line says the part people get
     * wrong: it cannot be undone from Cairn by anyone, at any role.
     */
    public val consequence: String
        get() = when (this) {
            LOCK -> "It can no longer be amended, by anyone, and it cannot be unlocked from Cairn."
            VOID -> "It stays in the record and leaves the analysis. You can restore it afterwards."
            RESTORE -> "It goes back into the analysis for this study."
        }
}

public sealed interface DetailUiState {

    public data object Loading : DetailUiState

    /** The submission was removed from this device while its screen was open. */
    public data object Gone : DetailUiState

    /**
     * The schema this submission pins is one this build cannot decode.
     *
     * The raw-`JsonObject` decision from `:core:database` arriving at the last
     * screen that reads a schema. The row is safely on disk and its provenance
     * still renders; only the answers cannot be laid out.
     */
    public data class Unreadable(public val header: DetailHeader) : DetailUiState

    public data class Ready(
        public val header: DetailHeader,
        public val fields: List<AnsweredField>,
        /**
         * Keys in the payload that this version of the form does not declare.
         *
         * Should be empty — a submission pins the version it was collected
         * under, so its schema is by construction the right one. Shown rather
         * than dropped because if it is ever non-empty something upstream is
         * wrong, and a coordinator deciding whether to lock a row should see
         * everything the row contains.
         */
        public val extras: List<AnsweredField> = emptyList(),
        public val actions: List<ReviewAction> = emptyList(),
        /** Why no action is offered, when that needs explaining rather than hiding. */
        public val note: String? = null,
        public val confirming: ReviewAction? = null,
        public val working: Boolean = false,
        /** The server's last refusal, in the server's own words where it gave any. */
        public val problem: String? = null,
    ) : DetailUiState
}

/** Everything above the answers: what this is, and what has been done to it. */
public data class DetailHeader(
    public val label: String,
    public val studyName: String,
    public val formTitle: String,
    public val versionLabel: String,
    public val collected: String,
    public val state: ReviewState,
    public val locked: Boolean,
    public val voided: Boolean,
)

public data class ProgressUiState(
    public val studyName: String = "",
    public val counts: ReviewCounts = ReviewCounts(0, 0, 0, 0),
    public val bars: List<ProgressBar> = emptyList(),
    public val caption: String = "",
    /**
     * False until the database has answered. Four zeroes are otherwise
     * indistinguishable from "not asked yet", and the difference is an empty
     * state shown for a frame to someone with a season's work behind them.
     */
    public val loaded: Boolean = false,
) {
    public val isEmpty: Boolean get() = loaded && counts.collected == 0 && counts.voided == 0
}

/** One day of the chart. [day] is `YYYY-MM-DD`, which is what the query grouped on. */
public data class ProgressBar(
    public val day: String,
    public val axisLabel: String,
    public val count: Int,
)

// ---- Mapping, in one place ----

internal fun ReviewSubmission.toRow(now: Instant, zone: ZoneId): ReviewRow = ReviewRow(
    collectedBy = collectedBy,
    clientId = clientId,
    label = submissionLabel(participantCode, clientId),
    detail = "${formTitle(formCode)} v$version · ${collectedLabel(collectedAt, now, zone)}",
    state = reviewStateOf(lockedAt, deletedAt),
)

/**
 * Which actions a submission offers, and why it offers none when it does not.
 *
 * The reasons are stated rather than left as a missing button. Two of the three
 * are surprising — a locked row cannot be unlocked by anyone, and a row that has
 * not reached the server cannot be reviewed at all, because a lock is a write to
 * a row the server has not been told about yet.
 */
internal fun actionsFor(
    locked: Boolean,
    voided: Boolean,
    uploaded: Boolean,
    canReview: Boolean,
): Pair<List<ReviewAction>, String?> = when {
    !canReview -> emptyList<ReviewAction>() to null
    locked -> emptyList<ReviewAction>() to
        "Locked submissions cannot be changed. Unlocking is not possible from Cairn."
    !uploaded -> emptyList<ReviewAction>() to
        "This submission has not uploaded yet. Locking and voiding happen on the server."
    voided -> listOf(ReviewAction.RESTORE) to null
    else -> listOf(ReviewAction.LOCK, ReviewAction.VOID) to null
}

/**
 * One stored answer, rendered against the field that declared it.
 *
 * Reads the spec rather than the JSON type: `single_select` stores a value like
 * `female` and the form declared the word a person should see. Rendering the
 * stored value would show a coordinator the wire spelling of an answer their
 * collector never saw.
 */
internal fun answer(spec: FieldSpec, value: kotlinx.serialization.json.JsonElement?): AnsweredField {
    val text = when {
        value == null -> NOT_ANSWERED
        spec.type == FieldType.SINGLE_SELECT -> spec.optionLabel(value.text())
        spec.type == FieldType.MULTI_SELECT -> (value as? JsonArray)
            ?.joinToString(", ") { spec.optionLabel(it.text()) }
            ?.ifBlank { NOT_ANSWERED }
            ?: spec.optionLabel(value.text())
        else -> value.text()
    }
    return AnsweredField(
        label = spec.label,
        // The unit belongs to the answer, not to the label. `268 g` is one fact;
        // "Body mass (g)" and "268" is the same fact split across two columns.
        value = if (text == NOT_ANSWERED || spec.unit == null) text else "$text ${spec.unit}",
        mono = spec.type in MONO_TYPES,
    )
}

/** Renders every answer the schema declares, in the order the schema declares them. */
internal fun answers(schema: FormSchema, data: JsonObject): List<AnsweredField> =
    schema.fields.map { answer(it, data[it.key]) }

/** Whatever the payload carries that the schema does not declare. */
internal fun extras(schema: FormSchema, data: JsonObject): List<AnsweredField> =
    data.entries
        .filter { schema.field(it.key) == null }
        .map { (key, value) -> AnsweredField(label = key, value = value.text(), mono = true) }

internal fun studyProgressCaption(days: Int): String =
    "Submissions per day · last $days ${plural(days, "day")}"

private fun FieldSpec.optionLabel(stored: String): String =
    options.firstOrNull { it.value == stored }?.label ?: stored

/**
 * The stored value as a person reads it.
 *
 * `JsonPrimitive.content` rather than `toString()`, which would wrap a string in
 * the quotes it is stored with. A number keeps whatever precision it was stored
 * at — rounding a body mass on the way to a screen is how a reviewer ends up
 * checking the wrong figure against their notebook.
 */
private fun kotlinx.serialization.json.JsonElement.text(): String = when (this) {
    is JsonPrimitive -> content
    else -> toString()
}

private const val NOT_ANSWERED = "Not answered"

private val MONO_TYPES = setOf(FieldType.NUMBER, FieldType.DATE)
