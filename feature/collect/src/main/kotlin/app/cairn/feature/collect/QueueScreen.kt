package app.cairn.feature.collect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import app.cairn.core.designsystem.CairnCard
import app.cairn.core.designsystem.CairnEmptyState
import app.cairn.core.designsystem.CairnListRow
import app.cairn.core.designsystem.CairnRowDivider
import app.cairn.core.designsystem.CairnSectionHeading
import app.cairn.core.designsystem.CairnStat
import app.cairn.core.designsystem.CairnStatus
import app.cairn.core.designsystem.CairnTheme
import app.cairn.core.designsystem.Spacing
import app.cairn.core.model.SyncState

/**
 * The Queue: everything this collector has recorded that the server has not
 * acknowledged.
 *
 * The screen that answers "is my morning's work safe", so it is the one screen
 * that must never round up. Every row is shown in the state the database holds
 * it in, and the only action is the one that actually helps.
 */
@Composable
public fun QueueScreen(
    state: QueueUiState,
    onUploadNow: () -> Unit,
    onToggleUploaded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { QueueAppBar() },
        bottomBar = { UploadBar(state, onUploadNow) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.isEmpty) {
                CairnEmptyState(
                    heading = "Nothing collected yet",
                    body = "Submissions you save appear here until they upload.",
                )
            } else {
                Body(state, onToggleUploaded)
            }
        }
    }
}

@Composable
private fun QueueAppBar() {
    Column(
        Modifier
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Text(
            text = "Queue",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .padding(horizontal = Spacing.Gutter, vertical = Spacing.Large)
                .testTag("screen_title"),
        )
        HorizontalDivider(thickness = Spacing.Hairline, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun Body(state: QueueUiState, onToggleUploaded: () -> Unit) {
    Column(
        Modifier.padding(Spacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.Large),
    ) {
        Summary(state)

        if (state.queued.isNotEmpty()) {
            Section("Waiting to upload", state.queued, "queued_rows")
        }
        if (state.failed.isNotEmpty()) {
            Section("Failed", state.failed, "failed_rows")
            Text(
                text = "These did not upload last time. Upload now tries them again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("failed_note"),
            )
        }

        if (state.counts.uploaded > 0) {
            TextButton(
                onClick = onToggleUploaded,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("toggle_uploaded"),
            ) {
                Text(
                    text = if (state.showingUploaded) {
                        "Hide uploaded"
                    } else {
                        "Show all ${state.counts.uploaded} uploaded"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (state.showingUploaded && state.uploaded.isNotEmpty()) {
                Section("Uploaded", state.uploaded, "uploaded_rows")
            }
        }
        Spacer(Modifier.height(Spacing.Large))
    }
}

/** The three numbers, divided by hairlines rather than boxed separately. */
@Composable
private fun Summary(state: QueueUiState) {
    CairnCard(Modifier.testTag("summary")) {
        // Intrinsic min height, so the two hairlines are exactly as tall as the
        // tallest stat rather than a guessed number of dp.
        Row(Modifier.height(IntrinsicSize.Min)) {
            CairnStat(
                value = state.counts.queued,
                word = SyncState.QUEUED.label,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
            )
            HairlineColumn()
            CairnStat(
                value = state.counts.failed,
                word = SyncState.FAILED.label,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            HairlineColumn()
            CairnStat(
                value = state.counts.uploaded,
                word = SyncState.UPLOADED.label,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HairlineColumn() {
    VerticalDivider(
        modifier = Modifier.fillMaxHeight(),
        thickness = Spacing.Hairline,
        color = MaterialTheme.colorScheme.outline,
    )
}

@Composable
private fun Section(heading: String, rows: List<SubmissionRow>, tag: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        CairnSectionHeading(heading)
        CairnCard(Modifier.testTag(tag)) {
            rows.forEachIndexed { index, row ->
                if (index > 0) CairnRowDivider()
                SubmissionListRow(row)
            }
        }
    }
}

/**
 * A submission, wherever it is shown: participant code in mono, then the form,
 * version and time, then the status as a dot plus its word.
 */
@Composable
internal fun SubmissionListRow(row: SubmissionRow) {
    CairnListRow(
        primary = row.label,
        secondary = row.detail,
        mono = true,
        modifier = Modifier.testTag("row_${row.clientId}"),
        trailing = { CairnStatus(word = row.state.label, color = row.state.colour()) },
    )
}

@Composable
internal fun SyncState.colour(): Color = when (this) {
    SyncState.QUEUED -> MaterialTheme.colorScheme.tertiary
    SyncState.FAILED -> MaterialTheme.colorScheme.error
    SyncState.UPLOADED -> MaterialTheme.colorScheme.primary
}

/**
 * "Uploads retry automatically. Retrying never creates a duplicate."
 *
 * The second sentence is the one that matters, and it is true because of the
 * `(collected_by, client_id)` primary key rather than because it is reassuring
 * to say. A collector who does not believe it will press the button repeatedly
 * and then worry about what they have created.
 */
@Composable
private fun UploadBar(state: QueueUiState, onUploadNow: () -> Unit) {
    /*
     * No navigation-bar inset here: the app's bottom navigation sits below this
     * bar and is what pads for the system. Adding it in both places leaves a
     * gap the width of the gesture bar in the middle of the screen.
     */
    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider(thickness = Spacing.Hairline, color = MaterialTheme.colorScheme.outline)
        Column(
            Modifier
                .padding(horizontal = Spacing.Gutter)
                .padding(top = Spacing.Large, bottom = Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Button(
                onClick = onUploadNow,
                enabled = state.canUpload,
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Spacing.CaptureControl)
                    .testTag("upload_now"),
            ) {
                Text(
                    text = "Upload now",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight(500)),
                )
            }
            Text(
                text = "Uploads retry automatically. Retrying never creates a duplicate.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("upload_note"),
            )
        }
    }
}

@Preview(name = "Queue", widthDp = 390, heightDp = 900)
@Composable
private fun QueuePreview() {
    CairnTheme {
        QueueScreen(state = previewQueue(), onUploadNow = {}, onToggleUploaded = {})
    }
}

@Preview(name = "Queue empty", widthDp = 390, heightDp = 844)
@Composable
private fun QueueEmptyPreview() {
    CairnTheme {
        QueueScreen(state = QueueUiState(), onUploadNow = {}, onToggleUploaded = {})
    }
}
