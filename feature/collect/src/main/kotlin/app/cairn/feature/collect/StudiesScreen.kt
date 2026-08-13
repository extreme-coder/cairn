package app.cairn.feature.collect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import app.cairn.core.designsystem.CairnCard
import app.cairn.core.designsystem.CairnChip
import app.cairn.core.designsystem.CairnEmptyState
import app.cairn.core.designsystem.CairnListRow
import app.cairn.core.designsystem.CairnRowDivider
import app.cairn.core.designsystem.CairnTheme
import app.cairn.core.designsystem.Spacing

/**
 * The Studies screen: every study this device holds.
 *
 * The top of the collector's stack. It exists rather than opening straight into
 * a form because which study you are recording into is the one thing a wrong
 * guess makes unrecoverable — a submission carries its study, and there is no
 * moving it afterwards.
 */
@Composable
public fun StudiesScreen(
    state: StudiesUiState,
    onStudy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { StudiesAppBar(state) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            when (state) {
                is StudiesUiState.Loading -> Unit
                is StudiesUiState.Empty -> Empty(state)
                is StudiesUiState.Ready -> Ready(state, onStudy)
            }
        }
    }
}

@Composable
private fun StudiesAppBar(state: StudiesUiState) {
    val count = (state as? StudiesUiState.Ready)?.studies?.size
    Column(
        Modifier
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Gutter, vertical = Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(Spacing.XSmall),
        ) {
            Text(
                text = "Studies",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.testTag("screen_title"),
            )
            if (count != null) {
                Text(
                    text = if (count == 1) "1 study" else "$count studies",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("study_count"),
                )
            }
        }
        HorizontalDivider(thickness = Spacing.Hairline, color = MaterialTheme.colorScheme.outline)
    }
}

/**
 * Two sentences for one absence.
 *
 * A device that has not finished its first sync is downloading; one that has is
 * telling the collector something only their coordinator can fix. Showing the
 * second while the first is true would send someone to make a phone call they
 * did not need to make.
 */
@Composable
private fun Empty(state: StudiesUiState.Empty) {
    CairnEmptyState(
        heading = "No studies yet",
        body = if (state.synced) {
            "A PI adds you to a study. Ask them for access."
        } else {
            "Downloading your studies from the server."
        },
    )
}

@Composable
private fun Ready(state: StudiesUiState.Ready, onStudy: (String) -> Unit) {
    Column(Modifier.padding(Spacing.Gutter)) {
        CairnCard(Modifier.testTag("studies")) {
            state.studies.forEachIndexed { index, study ->
                if (index > 0) CairnRowDivider()
                CairnListRow(
                    primary = study.name,
                    secondary = study.detail,
                    onClick = { onStudy(study.id) },
                    modifier = Modifier.testTag("study_${study.id}"),
                    trailing = {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(Spacing.XSmall),
                        ) {
                            study.role?.let { CairnChip(it.label) }
                            Text(
                                text = study.status,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (study.pendingCount > 0) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.testTag("status_${study.id}"),
                            )
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(Spacing.XLarge))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
            Text("Not listed here", style = MaterialTheme.typography.labelLarge)
            Text(
                text = "A PI adds you to a study. Ask them for access.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.XLarge))
    }
}

@Preview(name = "Studies", widthDp = 390, heightDp = 844)
@Composable
private fun StudiesPreview() {
    CairnTheme {
        StudiesScreen(state = previewStudies(), onStudy = {})
    }
}

@Preview(name = "Studies empty", widthDp = 390, heightDp = 844)
@Composable
private fun StudiesEmptyPreview() {
    CairnTheme {
        StudiesScreen(state = StudiesUiState.Empty(synced = true), onStudy = {})
    }
}
