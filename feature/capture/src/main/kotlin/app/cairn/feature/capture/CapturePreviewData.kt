package app.cairn.feature.capture

import app.cairn.core.model.FieldOption
import app.cairn.core.model.FieldSpec
import app.cairn.core.model.FieldType
import app.cairn.core.model.FormSchema
import kotlin.time.Instant

/**
 * Fixture for `@Preview` and screenshot tests only.
 *
 * It is a form definition of the shape the server publishes, not test data about
 * kestrels that the screen knows anything about.
 */
internal val previewSchema = FormSchema(
    fields = listOf(
        FieldSpec(
            key = "body_mass",
            type = FieldType.NUMBER,
            label = "Body mass",
            unit = "g",
            min = 90.0,
            max = 400.0,
            required = true,
        ),
        FieldSpec(
            key = "ring",
            type = FieldType.TEXT,
            label = "Ring code",
            help = "Study code only. Do not enter names.",
            maxLength = 8,
        ),
        FieldSpec(
            key = "sex",
            type = FieldType.SINGLE_SELECT,
            label = "Sex",
            required = true,
            options = listOf(
                FieldOption("female", "Female"),
                FieldOption("male", "Male"),
                FieldOption("unknown", "Not recorded"),
            ),
        ),
        FieldSpec(
            key = "behaviours",
            type = FieldType.MULTI_SELECT,
            label = "Behaviours",
            options = listOf(
                FieldOption("foraging", "Foraging"),
                FieldOption("preening", "Preening"),
                FieldOption("flight", "In flight"),
            ),
        ),
        FieldSpec(
            key = "observed_on",
            type = FieldType.DATE,
            label = "Observed on",
        ),
        FieldSpec(
            key = "notes",
            type = FieldType.LONG_TEXT,
            label = "Notes",
        ),
    ),
)

internal fun previewCaptureState(): CaptureState = CaptureState(
    clientId = "cccccccc-0000-0000-0000-000000000001",
    collectedBy = "55555555-5555-5555-5555-555555555551",
    studyId = "11111111-1111-1111-1111-111111111111",
    formVersionId = "33333333-3333-3333-3333-333333333332",
    schema = previewSchema,
    openedAt = Instant.parse("2026-07-30T09:14:00Z"),
)

internal fun previewState(
    showErrors: Boolean = false,
    queuedCount: Int = 0,
    savedClientId: String? = null,
): CaptureUiState.Editing {
    val capture = previewCaptureState()
        .setChoice("sex", "female")
        .toggleChoice("behaviours", "foraging")
        .setText("ring", "EX-4471")
        .let { if (showErrors) it.setNumber("body_mass", "412").attemptSave() else it }
    return CaptureUiState.Editing(
        form = FormHeader(code = "baseline_intake", version = 3),
        capture = capture,
        queuedCount = queuedCount,
        savedClientId = savedClientId,
    )
}
