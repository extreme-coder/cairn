package app.cairn.feature.capture

import app.cairn.core.model.FieldError
import app.cairn.core.model.FormSchema
import app.cairn.core.model.validate
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

/**
 * One collector filling in one form.
 *
 * Pure data with pure transitions. A `ViewModel` will hold one of these and
 * forward calls to it, which is what keeps the whole of capture testable on the
 * JVM: `:core:database` is an Android library, but nothing here touches it.
 */
public data class CaptureState(
    public val clientId: String,
    public val collectedBy: String,
    public val studyId: String,
    public val formVersionId: String,
    public val schema: FormSchema,
    public val openedAt: Instant,
    public val participantId: String? = null,
    public val values: Map<String, JsonElement> = emptyMap(),
    public val hasAttemptedSave: Boolean = false,
) {

    /**
     * What is written to `submissions.data`.
     *
     * Assembled in schema order rather than the order the collector happened to
     * fill the form in, so the same answers always produce the same bytes and an
     * amend diffs cleanly against what it replaced. A key the schema does not
     * declare cannot reach the server through here.
     */
    public val payload: JsonObject
        get() = JsonObject(
            schema.fields
                .mapNotNull { spec -> values[spec.key]?.let { spec.key to it } }
                .toMap(),
        )

    public val errors: List<FieldError> get() = schema.validate(payload)

    public val isValid: Boolean get() = errors.isEmpty()

    /**
     * Errors stay hidden until the collector has tried to save once. Marking a
     * field wrong before they have reached it reads as the app arguing with
     * someone who is still typing.
     */
    public val visibleErrors: List<FieldError>
        get() = if (hasAttemptedSave) errors else emptyList()

    public fun errorFor(key: String): FieldError? = visibleErrors.firstOrNull { it.key == key }

    /**
     * A field left empty is absent from the payload, never an empty string.
     * Otherwise `null` and `""` become two spellings of "no answer" and every
     * later reader has to know both.
     */
    public fun setText(key: String, text: String): CaptureState =
        put(key, text.takeIf { it.isNotBlank() }?.let(::JsonPrimitive))

    /**
     * Text that is not a number is stored verbatim rather than dropped, so
     * `268 g` or a comma decimal does not vanish under the cursor while the
     * collector is still typing. Validation then reports it as the wrong type
     * and [save][CaptureRepository.save] refuses it, so no such value reaches
     * the server. Note that a trailing point parses: `26.` is 26.0, not text.
     */
    public fun setNumber(key: String, text: String): CaptureState {
        val trimmed = text.trim()
        return put(
            key,
            when {
                trimmed.isEmpty() -> null
                else -> trimmed.toDoubleOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(trimmed)
            },
        )
    }

    public fun setDate(key: String, date: LocalDate?): CaptureState =
        put(key, date?.let { JsonPrimitive(it.toString()) })

    public fun setChoice(key: String, value: String?): CaptureState =
        put(key, value?.takeIf { it.isNotBlank() }?.let(::JsonPrimitive))

    /**
     * Chosen values are held in the order the schema lists its options, not the
     * order they were tapped, for the same reason [payload] is ordered.
     */
    public fun toggleChoice(key: String, value: String): CaptureState {
        val current = (values[key] as? JsonArray)
            .orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.content }
        val chosen = if (value in current) current - value else current + value
        val ordered = schema.field(key)
            ?.options
            ?.map { it.value }
            ?.filter { it in chosen }
            ?: chosen
        return put(key, ordered.takeIf { it.isNotEmpty() }?.let { JsonArray(it.map(::JsonPrimitive)) })
    }

    public fun clear(key: String): CaptureState = put(key, null)

    public fun attemptSave(): CaptureState = copy(hasAttemptedSave = true)

    private fun put(key: String, element: JsonElement?): CaptureState =
        copy(values = if (element == null) values - key else values + (key to element))
}

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
