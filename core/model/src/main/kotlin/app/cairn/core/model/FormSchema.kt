package app.cairn.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public enum class FieldType {
    @SerialName("text")
    TEXT,

    @SerialName("long_text")
    LONG_TEXT,

    @SerialName("number")
    NUMBER,

    @SerialName("date")
    DATE,

    @SerialName("single_select")
    SINGLE_SELECT,

    @SerialName("multi_select")
    MULTI_SELECT,

    @SerialName("photo")
    PHOTO,
}

@Serializable
public data class FieldOption(
    public val value: String,
    public val label: String,
)

@Serializable
public data class FieldSpec(
    public val key: String,
    public val type: FieldType,
    public val label: String,
    public val help: String? = null,
    public val required: Boolean = false,
    public val unit: String? = null,
    public val min: Double? = null,
    public val max: Double? = null,
    public val maxLength: Int? = null,
    public val options: List<FieldOption> = emptyList(),
)

/**
 * The `schema` payload of a `form_versions` row.
 *
 * A submission pins the form version it was collected against, so a schema is
 * read back exactly as it was when the data was entered. Fields absent from the
 * schema are ignored during validation rather than rejected, which is what lets
 * a payload collected on v2 survive the publication of v3.
 */
@Serializable
public data class FormSchema(
    public val fields: List<FieldSpec> = emptyList(),
) {
    public fun field(key: String): FieldSpec? = fields.firstOrNull { it.key == key }
}
