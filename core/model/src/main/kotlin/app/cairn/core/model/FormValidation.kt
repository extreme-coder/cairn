package app.cairn.core.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

public sealed interface FieldError {
    public val key: String
    public val label: String

    /** Reader-facing text. Says what is wrong and what the acceptable value is. */
    public fun message(): String
}

public data class MissingValue(
    override val key: String,
    override val label: String,
) : FieldError {
    override fun message(): String = "$label is required."
}

public data class OutOfRange(
    override val key: String,
    override val label: String,
    public val min: Double?,
    public val max: Double?,
    public val unit: String?,
) : FieldError {
    override fun message(): String {
        val tail = unit?.let { " $it" } ?: ""
        return when {
            min != null && max != null ->
                "$label must be between ${format(min)} and ${format(max)}$tail."
            min != null -> "$label must be ${format(min)}$tail or more."
            max != null -> "$label must be ${format(max)}$tail or less."
            else -> "$label is out of range."
        }
    }
}

public data class WrongType(
    override val key: String,
    override val label: String,
    public val expected: String,
) : FieldError {
    override fun message(): String = "$label must be $expected."
}

public data class TooLong(
    override val key: String,
    override val label: String,
    public val maxLength: Int,
) : FieldError {
    override fun message(): String = "$label must be $maxLength characters or fewer."
}

public data class UnknownOption(
    override val key: String,
    override val label: String,
    public val value: String,
) : FieldError {
    override fun message(): String =
        "$label has a value that is not an option in this form version."
}

/**
 * Checks a submission payload against the schema it was collected under.
 *
 * Keys present in [data] but absent from the schema are ignored. That is
 * deliberate: it keeps an older payload readable after the form moves on.
 */
public fun FormSchema.validate(data: JsonObject): List<FieldError> =
    fields.mapNotNull { spec -> spec.validate(data[spec.key]) }

private fun FieldSpec.validate(raw: JsonElement?): FieldError? {
    if (raw == null || raw is JsonNull) {
        return if (required) MissingValue(key, label) else null
    }
    return when (type) {
        FieldType.TEXT, FieldType.LONG_TEXT, FieldType.PHOTO -> validateText(raw)
        FieldType.NUMBER -> validateNumber(raw)
        FieldType.DATE -> validateDate(raw)
        FieldType.SINGLE_SELECT -> validateChoice(raw)
        FieldType.MULTI_SELECT -> validateChoices(raw)
    }
}

private fun FieldSpec.validateText(raw: JsonElement): FieldError? {
    val text = raw.asStringOrNull() ?: return WrongType(key, label, "text")
    if (text.isBlank()) return if (required) MissingValue(key, label) else null
    if (maxLength != null && text.length > maxLength) return TooLong(key, label, maxLength)
    return null
}

private fun FieldSpec.validateNumber(raw: JsonElement): FieldError? {
    val number = raw.asNumberOrNull() ?: return WrongType(key, label, "a number")
    val belowMin = min != null && number < min
    val aboveMax = max != null && number > max
    if (belowMin || aboveMax) return OutOfRange(key, label, min, max, unit)
    return null
}

private fun FieldSpec.validateDate(raw: JsonElement): FieldError? {
    val text = raw.asStringOrNull() ?: return WrongType(key, label, "a date")
    return try {
        LocalDate.parse(text)
        null
    } catch (_: IllegalArgumentException) {
        WrongType(key, label, "a date")
    }
}

private fun FieldSpec.validateChoice(raw: JsonElement): FieldError? {
    val text = raw.asStringOrNull() ?: return WrongType(key, label, "one option")
    if (text.isBlank()) return if (required) MissingValue(key, label) else null
    if (options.none { it.value == text }) return UnknownOption(key, label, text)
    return null
}

private fun FieldSpec.validateChoices(raw: JsonElement): FieldError? {
    val array = raw as? JsonArray ?: return WrongType(key, label, "a list of options")
    if (array.isEmpty()) return if (required) MissingValue(key, label) else null
    val chosen = array.map { it.asStringOrNull() ?: return WrongType(key, label, "a list of options") }
    val stray = chosen.firstOrNull { value -> options.none { it.value == value } }
    return stray?.let { UnknownOption(key, label, it) }
}

private fun JsonElement.asStringOrNull(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonElement.asNumberOrNull(): Double? =
    (this as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()

private fun format(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
