package app.cairn.feature.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import app.cairn.core.designsystem.CairnBar
import app.cairn.core.designsystem.CairnBarChart
import app.cairn.core.designsystem.CairnCard
import app.cairn.core.designsystem.CairnEmptyState
import app.cairn.core.designsystem.CairnIcons
import app.cairn.core.designsystem.CairnSectionHeading
import app.cairn.core.designsystem.CairnStat
import app.cairn.core.designsystem.CairnTheme
import app.cairn.core.designsystem.Spacing
import app.cairn.core.model.ReviewState
import app.cairn.core.model.plural

/**
 * How much has been collected in one study, and how much of it has been looked
 * at.
 *
 * Three numbers and one chart. The numbers are what a PI asks for in a status
 * meeting; the chart is what tells them whether a transect stopped being walked
 * three days ago — a fact no total can show.
 */
@Composable
public fun ProgressScreen(
    state: ProgressUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { ProgressAppBar(state, onBack) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            when {
                // Not asked yet is not the same as asked and answered zero, and
                // drawing three zeroes for a frame to someone with a season's
                // work behind them is the difference.
                !state.loaded -> Unit
                state.isEmpty -> CairnEmptyState(
                    heading = "Nothing collected yet",
                    body = "Progress appears here once collectors upload their first submissions.",
                )
                else -> Body(state)
            }
        }
    }
}

@Composable
private fun ProgressAppBar(state: ProgressUiState, onBack: () -> Unit) {
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
                    contentDescription = state.studyName.ifBlank { "Back" },
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
                CairnSectionHeading(state.studyName)
                Text(
                    text = "Progress",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.testTag("screen_title"),
                )
            }
        }
        HorizontalDivider(thickness = Spacing.Hairline, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun Body(state: ProgressUiState) {
    Column(
        Modifier.padding(Spacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.Large),
    ) {
        Summary(state)

        CairnCard(Modifier.testTag("chart_card")) {
            Column(Modifier.padding(Spacing.Large)) {
                CairnBarChart(
                    bars = state.bars.map { CairnBar(axisLabel = it.axisLabel, value = it.count) },
                    caption = state.caption,
                )
            }
        }

        // Participants is a fourth number and does not belong in a row of three,
        // but it is the one a PI checks against their recruitment target. It is
        // a sentence rather than a stat so the row above stays scannable.
        Text(
            text = participantLine(state),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("participants"),
        )
        Spacer(Modifier.height(Spacing.Large))
    }
}

/** The same three-stat card the Queue uses, so two summaries read the same way. */
@Composable
private fun Summary(state: ProgressUiState) {
    CairnCard(Modifier.testTag("summary")) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            CairnStat(
                value = state.counts.collected,
                word = "Collected",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            HairlineColumn()
            CairnStat(
                value = state.counts.locked,
                word = ReviewState.LOCKED.label,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            HairlineColumn()
            CairnStat(
                value = state.counts.voided,
                word = ReviewState.VOIDED.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/**
 * Participants, and how many submissions are still open.
 *
 * Zero participants is the honest answer today — capture attaches none yet — and
 * saying "No participants recorded" is better than hiding the line, because the
 * line is what will make it obvious the moment the picker lands.
 */
internal fun participantLine(state: ProgressUiState): String {
    val counts = state.counts
    val participants = if (counts.participants == 0) {
        "No participants recorded"
    } else {
        "${counts.participants} ${plural(counts.participants, "participant")}"
    }
    return "$participants · ${counts.unlocked} still open"
}

@Preview(name = "Progress", widthDp = 390, heightDp = 900)
@Composable
private fun ProgressPreview() {
    CairnTheme { ProgressScreen(state = previewProgress(), onBack = {}) }
}

@Preview(name = "Progress empty", widthDp = 390, heightDp = 844)
@Composable
private fun ProgressEmptyPreview() {
    CairnTheme {
        ProgressScreen(
            state = ProgressUiState(
                studyName = "Kluane ground squirrel survey",
                caption = studyProgressCaption(14),
                loaded = true,
            ),
            onBack = {},
        )
    }
}
