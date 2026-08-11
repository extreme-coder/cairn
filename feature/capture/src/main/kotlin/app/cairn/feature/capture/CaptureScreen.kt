package app.cairn.feature.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cairn.core.designsystem.CairnTheme
import app.cairn.core.designsystem.Spacing
import app.cairn.core.model.FieldSpec
import app.cairn.core.model.FieldType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * The New submission screen.
 *
 * Renders whatever fields the form version declares. It knows nothing about
 * kestrels or body mass: every label, unit, bound and option comes off the
 * schema, and every error string is derived by `:core:model`. Supporting a new
 * field type means adding it to `FieldType`, not writing another screen.
 */
@Composable
public fun CaptureScreen(
    state: CaptureUiState,
    onEdit: ((CaptureState) -> CaptureState) -> Unit,
    onSave: () -> Unit,
    onStartAnother: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is CaptureUiState.Loading -> Centred("Opening the form.", modifier)
        is CaptureUiState.Unopenable -> Centred(state.reason.message(), modifier)
        is CaptureUiState.Editing -> Editing(state, onEdit, onSave, onStartAnother, modifier)
    }
}

@Composable
private fun Centred(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(Spacing.XXLarge),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("message"),
        )
    }
}

@Composable
private fun Editing(
    state: CaptureUiState.Editing,
    onEdit: ((CaptureState) -> CaptureState) -> Unit,
    onSave: () -> Unit,
    onStartAnother: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val saved = state.savedClientId != null
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        // The bars pad themselves for the system insets, so the content area must
        // not be padded for them a second time.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { AppBar(state.form, showDiscard = !saved, onDiscard = onStartAnother) },
        bottomBar = {
            BottomActions(
                saved = saved,
                queuedCount = state.queuedCount,
                onSave = onSave,
                onStartAnother = onStartAnother,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.Gutter)
                .padding(top = Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(Spacing.Gutter),
        ) {
            if (state.queuedCount > 0) {
                QueueBanner(state.queuedCount)
            }
            Text(
                text = "OBSERVATION",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            state.capture.schema.fields.forEach { spec ->
                Field(spec, state.capture, onEdit)
            }
            Spacer(Modifier.height(Spacing.Large))
        }
    }
}

@Composable
private fun AppBar(form: FormHeader, showDiscard: Boolean, onDiscard: () -> Unit) {
    Column(
        Modifier
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Gutter, vertical = Spacing.Large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = form.title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.testTag("form_title"),
            )
            Spacer(Modifier.size(Spacing.Small))
            VersionPill(form.versionLabel)
            Spacer(Modifier.weight(1f))
            if (showDiscard) {
                TextButton(onClick = onDiscard, modifier = Modifier.testTag("discard")) {
                    Text(
                        text = "Discard",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
        HorizontalDivider(
            thickness = Spacing.Hairline,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun VersionPill(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .border(Spacing.Hairline, MaterialTheme.colorScheme.outline, CircleShape)
            .padding(horizontal = Spacing.Small, vertical = 2.dp)
            .testTag("form_version"),
    )
}

/**
 * Ochre and quantified. Shown only when there is something in the queue to state
 * a fact about.
 *
 * It says where the submissions are and stops there. The design calls for a
 * consequence too ("they upload when you reconnect"), but there is no network
 * code in this build, so that sentence would be a promise the app cannot keep.
 * Restore it with `:core:sync`, not before.
 */
@Composable
private fun QueueBanner(count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                MaterialTheme.shapes.small,
            )
            .padding(Spacing.Large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.size(Spacing.Medium))
        Text(
            text = "$count queued on this device.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("queue_banner"),
        )
    }
}

/** Status is never colour alone. The dot always travels with a word. */
@Composable
private fun StatusDot(color: Color) {
    Box(
        Modifier
            .size(Spacing.Small)
            .background(color, CircleShape),
    )
}

@Composable
private fun Field(
    spec: FieldSpec,
    capture: CaptureState,
    onEdit: ((CaptureState) -> CaptureState) -> Unit,
) {
    val error = capture.errorFor(spec.key)
    Column(
        Modifier.testTag("field_${spec.key}"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        FieldLabel(spec)
        when (spec.type) {
            FieldType.TEXT, FieldType.DATE ->
                CairnTextField(spec, capture, error != null, onEdit)
            FieldType.LONG_TEXT ->
                CairnTextField(spec, capture, error != null, onEdit, minHeight = 96.dp, singleLine = false)
            FieldType.NUMBER ->
                CairnTextField(spec, capture, error != null, onEdit, numeric = true)
            FieldType.SINGLE_SELECT ->
                ChoiceList(spec, capture, onEdit)
            FieldType.MULTI_SELECT ->
                Checklist(spec, capture, onEdit)
            FieldType.PHOTO ->
                Text(
                    text = "Attachments are not available in this build.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
        FieldFooter(spec, error?.message())
    }
}

/** The label is always visible above the control, never a placeholder. */
@Composable
private fun FieldLabel(spec: FieldSpec) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = spec.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.size(Spacing.XSmall))
        Text(
            text = if (spec.required) "Required" else "Optional",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** An error replaces the helper text rather than stacking under it. */
@Composable
private fun FieldFooter(spec: FieldSpec, error: String?) {
    val help = spec.help
    when {
        error != null -> Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 5.dp)) { StatusDot(MaterialTheme.colorScheme.error) }
            Spacer(Modifier.size(Spacing.Small))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("error_${spec.key}"),
            )
        }
        help != null -> Text(
            text = help,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        spec.type == FieldType.DATE -> Text(
            text = "YYYY-MM-DD",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Keeps the typed text locally and pushes the interpreted value into
 * [CaptureState].
 *
 * The two are not the same string: `268` becomes the number 268.0, and echoing
 * that back into the box would rewrite what the collector typed under their
 * cursor. Re-keyed on `clientId`, so opening a fresh submission clears the boxes.
 */
@Composable
private fun CairnTextField(
    spec: FieldSpec,
    capture: CaptureState,
    hasError: Boolean,
    onEdit: ((CaptureState) -> CaptureState) -> Unit,
    numeric: Boolean = false,
    singleLine: Boolean = true,
    minHeight: Dp = Spacing.CaptureControl,
) {
    var text by remember(capture.clientId, spec.key) {
        mutableStateOf(capture.rawTextOf(spec.key))
    }
    TextField(
        value = text,
        onValueChange = { typed ->
            text = typed
            onEdit { current ->
                if (numeric) current.setNumber(spec.key, typed) else current.setText(spec.key, typed)
            }
        },
        singleLine = singleLine,
        textStyle = if (numeric) {
            MaterialTheme.typography.labelMedium
        } else {
            MaterialTheme.typography.bodyLarge
        },
        suffix = spec.unit?.let { unit ->
            { Text(unit, style = MaterialTheme.typography.bodyLarge) }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
        ),
        shape = MaterialTheme.shapes.small,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .then(
                if (hasError) {
                    Modifier.border(
                        Spacing.Hairline,
                        MaterialTheme.colorScheme.error,
                        MaterialTheme.shapes.small,
                    )
                } else {
                    Modifier
                },
            )
            .testTag("input_${spec.key}"),
    )
}

@Composable
private fun ChoiceList(
    spec: FieldSpec,
    capture: CaptureState,
    onEdit: ((CaptureState) -> CaptureState) -> Unit,
) {
    val selected = (capture.values[spec.key] as? JsonPrimitive)?.content
    OptionContainer {
        spec.options.forEachIndexed { index, option ->
            if (index > 0) InsetDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(Spacing.CaptureControl)
                    .selectable(
                        selected = option.value == selected,
                        role = Role.RadioButton,
                        onClick = { onEdit { it.setChoice(spec.key, option.value) } },
                    )
                    .padding(horizontal = Spacing.Large)
                    .testTag("option_${spec.key}_${option.value}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = option.value == selected,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.primary,
                        unselectedColor = MaterialTheme.colorScheme.outline,
                    ),
                )
                Spacer(Modifier.size(Spacing.Medium))
                Text(option.label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun Checklist(
    spec: FieldSpec,
    capture: CaptureState,
    onEdit: ((CaptureState) -> CaptureState) -> Unit,
) {
    val chosen = (capture.values[spec.key] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.content }
        .orEmpty()
    OptionContainer {
        spec.options.forEachIndexed { index, option ->
            if (index > 0) InsetDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(Spacing.CaptureControl)
                    .toggleable(
                        value = option.value in chosen,
                        role = Role.Checkbox,
                        onValueChange = { onEdit { it.toggleChoice(spec.key, option.value) } },
                    )
                    .padding(horizontal = Spacing.Large)
                    .testTag("option_${spec.key}_${option.value}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = option.value in chosen,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.outline,
                    ),
                )
                Spacer(Modifier.size(Spacing.Medium))
                Text(option.label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun OptionContainer(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(
                Spacing.Hairline,
                MaterialTheme.colorScheme.outline,
                MaterialTheme.shapes.small,
            ),
    ) {
        content()
    }
}

/** Hairlines are inset to the text, not full-bleed. */
@Composable
private fun InsetDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = Spacing.Large),
        thickness = Spacing.Hairline,
        color = MaterialTheme.colorScheme.outline,
    )
}

@Composable
private fun BottomActions(
    saved: Boolean,
    queuedCount: Int,
    onSave: () -> Unit,
    onStartAnother: () -> Unit,
) {
    Column(
        Modifier
            .background(MaterialTheme.colorScheme.surface)
            .imePadding()
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        HorizontalDivider(
            thickness = Spacing.Hairline,
            color = MaterialTheme.colorScheme.outline,
        )
        Column(
            Modifier
                .padding(horizontal = Spacing.Gutter)
                .padding(top = Spacing.Large, bottom = Spacing.XLarge),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Text(
                text = if (saved) {
                    "Saved on this device. $queuedCount queued."
                } else {
                    "Saved on this device."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_note"),
            )
            Button(
                onClick = if (saved) onStartAnother else onSave,
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Spacing.CaptureControl)
                    .testTag("primary_action"),
            ) {
                Text(
                    text = if (saved) "New submission" else "Save submission",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight(500)),
                )
            }
        }
    }
}

/** What the box shows for a field the collector has not retyped. */
private fun CaptureState.rawTextOf(key: String): String =
    when (val value = values[key]) {
        is JsonPrimitive -> if (value.isString) value.content else value.content.removeSuffix(".0")
        else -> ""
    }

@Preview(name = "Capture", widthDp = 390, heightDp = 900)
@Composable
private fun CaptureScreenPreview() {
    CairnTheme {
        CaptureScreen(previewState(), onEdit = {}, onSave = {}, onStartAnother = {})
    }
}

@Preview(name = "Capture with errors", widthDp = 390, heightDp = 900)
@Composable
private fun CaptureScreenErrorPreview() {
    CairnTheme {
        CaptureScreen(
            previewState(showErrors = true, queuedCount = 3),
            onEdit = {},
            onSave = {},
            onStartAnother = {},
        )
    }
}
