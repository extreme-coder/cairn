package app.cairn.feature.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import app.cairn.core.designsystem.CairnBanner
import app.cairn.core.designsystem.CairnCard
import app.cairn.core.designsystem.CairnChip
import app.cairn.core.designsystem.CairnEmptyState
import app.cairn.core.designsystem.CairnIcons
import app.cairn.core.designsystem.CairnRowDivider
import app.cairn.core.designsystem.CairnSectionHeading
import app.cairn.core.designsystem.CairnStatus
import app.cairn.core.designsystem.CairnTheme
import app.cairn.core.designsystem.IconSize
import app.cairn.core.designsystem.Spacing

/**
 * One submission, read-only, with the two actions a coordinator has.
 *
 * Read-only is the whole point and is not a shortcut: amending is a different
 * job with a different screen, and a review pass that could edit a value while
 * scanning is how a study loses the distinction between what was observed and
 * what was later decided. Everything here is what the collector recorded, laid
 * out against the schema version the row pins.
 */
@Composable
public fun SubmissionDetailScreen(
    state: DetailUiState,
    onAsk: (ReviewAction) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { DetailAppBar(state, onBack) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            when (state) {
                is DetailUiState.Loading -> Unit
                is DetailUiState.Gone -> CairnEmptyState(
                    heading = "This submission is gone",
                    body = "It was removed from this device. Go back to the list.",
                )
                is DetailUiState.Unreadable -> Column(
                    Modifier.padding(Spacing.Gutter),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Large),
                ) {
                    Provenance(state.header)
                    CairnBanner(
                        text = "This form needs a newer version of Cairn. " +
                            "The submission is safe; update the app to read it.",
                        color = MaterialTheme.colorScheme.error,
                        icon = CairnIcons.Alert,
                        modifier = Modifier.testTag("unreadable"),
                    )
                }
                is DetailUiState.Ready -> Ready(state, onAsk)
            }
        }
    }

    val ready = state as? DetailUiState.Ready
    ready?.confirming?.let { action ->
        ConfirmDialog(action = action, onConfirm = onConfirm, onDismiss = onDismiss)
    }
}

@Composable
private fun DetailAppBar(state: DetailUiState, onBack: () -> Unit) {
    val header = when (state) {
        is DetailUiState.Ready -> state.header
        is DetailUiState.Unreadable -> state.header
        else -> null
    }
    Column(
        Modifier
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    start = Spacing.XSmall,
                    end = Spacing.Gutter,
                    top = Spacing.Medium,
                    bottom = Spacing.Medium,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
                Icon(
                    imageVector = CairnIcons.Back,
                    contentDescription = "Submissions",
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
                CairnSectionHeading("Submission")
                Text(
                    // The participant code, in mono, because it is a code and is
                    // read character by character against a field notebook.
                    text = header?.label.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.testTag("submission_label"),
                )
            }
            header?.let {
                CairnStatus(
                    word = it.state.label,
                    color = it.state.colour(),
                    modifier = Modifier.testTag("state"),
                )
            }
        }
        HorizontalDivider(thickness = Spacing.Hairline, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun Ready(state: DetailUiState.Ready, onAsk: (ReviewAction) -> Unit) {
    Column(
        Modifier.padding(Spacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.Large),
    ) {
        Provenance(state.header)

        // Both facts, where the chip on the list could only carry one. A voided
        // row that is also locked is rare and confusing, and this is the screen
        // that has room to be exact about it.
        if (state.header.locked && state.header.voided) {
            CairnBanner(
                text = "This submission is voided and locked. Neither can be changed.",
                modifier = Modifier.testTag("both_states"),
            )
        }

        CairnSectionHeading("Answers")
        if (state.fields.isEmpty()) {
            Text(
                text = "This form version declares no fields.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("no_fields"),
            )
        } else {
            CairnCard(Modifier.testTag("answers")) {
                state.fields.forEachIndexed { index, field ->
                    if (index > 0) CairnRowDivider()
                    AnswerRow(field)
                }
            }
        }

        if (state.extras.isNotEmpty()) {
            CairnSectionHeading("Not in this form version")
            CairnCard(Modifier.testTag("extras")) {
                state.extras.forEachIndexed { index, field ->
                    if (index > 0) CairnRowDivider()
                    AnswerRow(field)
                }
            }
        }

        state.problem?.let {
            CairnBanner(
                text = it,
                color = MaterialTheme.colorScheme.error,
                icon = CairnIcons.Alert,
                modifier = Modifier.testTag("problem"),
            )
        }

        state.note?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("note"),
            )
        }

        Actions(state, onAsk)
        Spacer(Modifier.height(Spacing.Large))
    }
}

/** What this submission is: which study, which form, which version, when. */
@Composable
private fun Provenance(header: DetailHeader) {
    CairnCard(Modifier.testTag("provenance")) {
        Column(
            Modifier.padding(Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = header.formTitle,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight(500)),
                    modifier = Modifier.weight(1f),
                )
                // The version is not decoration. It is the only thing that says
                // which questions produced these answers.
                CairnChip(header.versionLabel, Modifier.testTag("version"))
            }
            Text(
                text = header.studyName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = header.collected,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("collected_at"),
            )
        }
    }
}

/**
 * Label above, answer below — the same arrangement as the capture screen's
 * fields, so a coordinator reading a submission is looking at the shape the
 * collector filled in.
 */
@Composable
private fun AnswerRow(field: AnsweredField) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Large, vertical = Spacing.Medium)
            .testTag("answer_${field.label}"),
        verticalArrangement = Arrangement.spacedBy(Spacing.XSmall),
    ) {
        // `labelLarge`, sentence case, exactly as the capture screen sets a
        // field label. A running head would uppercase it, and a coordinator
        // checking an answer should be reading the words the collector read.
        Text(
            text = field.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = field.value,
            style = if (field.mono) {
                MaterialTheme.typography.labelMedium
            } else {
                MaterialTheme.typography.bodyLarge
            },
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Lock is primary and filled; void and restore are outlined in the error colour.
 *
 * `DESIGN.md`: one primary action per screen, and destructive actions outlined
 * rather than filled red. Both are words — an icon never replaces a text label,
 * and neither of these has a glyph anyone would read the same way twice.
 */
@Composable
private fun Actions(state: DetailUiState.Ready, onAsk: (ReviewAction) -> Unit) {
    if (state.actions.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
        state.actions.forEach { action ->
            if (action == ReviewAction.LOCK) {
                Button(
                    onClick = { onAsk(action) },
                    enabled = !state.working,
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Spacing.CaptureControl)
                        .testTag("action_${action.name.lowercase()}"),
                ) {
                    Text(action.label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight(500)))
                }
            } else {
                OutlinedButton(
                    onClick = { onAsk(action) },
                    enabled = !state.working,
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (action == ReviewAction.VOID) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Spacing.CaptureControl)
                        .testTag("action_${action.name.lowercase()}"),
                ) {
                    Text(action.label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight(500)))
                }
            }
        }
        if (state.working) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = CairnIcons.Upload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize),
                )
                Spacer(Modifier.size(Spacing.Small))
                Text(
                    text = "Asking the server…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("working"),
                )
            }
        }
    }
}

/**
 * `DESIGN.md`'s dialog: a Literata heading ending in `?`, one body line stating
 * the consequence, two parallel actions right-aligned with the destructive one
 * last — and the confirming action carries **exactly the words** of the button
 * that opened it.
 */
@Composable
private fun ConfirmDialog(
    action: ReviewAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        title = {
            Text(
                text = action.question,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.testTag("dialog_title"),
            )
        },
        text = {
            Text(
                text = action.consequence,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("dialog_body"),
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("dialog_cancel")) {
                Text("Cancel", color = MaterialTheme.colorScheme.secondary)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("dialog_confirm")) {
                Text(
                    text = action.label,
                    color = if (action == ReviewAction.VOID) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
    )
}

@Preview(name = "Submission", widthDp = 390, heightDp = 900)
@Composable
private fun DetailPreview() {
    CairnTheme {
        SubmissionDetailScreen(
            state = previewDetail(),
            onAsk = {},
            onConfirm = {},
            onDismiss = {},
            onBack = {},
        )
    }
}

@Preview(name = "Submission with lock dialog", widthDp = 390, heightDp = 900)
@Composable
private fun DetailDialogPreview() {
    CairnTheme {
        SubmissionDetailScreen(
            state = previewDetail().copy(confirming = ReviewAction.LOCK),
            onAsk = {},
            onConfirm = {},
            onDismiss = {},
            onBack = {},
        )
    }
}

@Preview(name = "Submission locked", widthDp = 390, heightDp = 900)
@Composable
private fun DetailLockedPreview() {
    CairnTheme {
        SubmissionDetailScreen(
            state = previewLockedDetail(),
            onAsk = {},
            onConfirm = {},
            onDismiss = {},
            onBack = {},
        )
    }
}
