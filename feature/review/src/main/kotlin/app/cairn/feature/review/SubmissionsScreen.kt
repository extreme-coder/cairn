package app.cairn.feature.review

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import app.cairn.core.designsystem.CairnIcons
import app.cairn.core.designsystem.CairnListRow
import app.cairn.core.designsystem.CairnRowDivider
import app.cairn.core.designsystem.CairnSectionHeading
import app.cairn.core.designsystem.CairnStatus
import app.cairn.core.designsystem.CairnTheme
import app.cairn.core.designsystem.Spacing
import app.cairn.core.model.ReviewState
import app.cairn.core.model.plural

/**
 * The Submissions screen: everything collected in one study, by anyone.
 *
 * A collector's Queue answers "is my morning's work safe". This answers a
 * different question — "what have I not looked at yet" — which is why the chips
 * are the first control on the screen and why [ReviewFilter.OPEN] is the one
 * that gets used. The list is otherwise deliberately the same rows in the same
 * order as everywhere else: participant code in mono, then form, version and
 * time, then a status word.
 */
@Composable
public fun SubmissionsScreen(
    state: SubmissionsUiState,
    onSubmission: (String, String) -> Unit,
    onFilter: (ReviewFilter) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { SubmissionsAppBar(state, onBack) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            when (state) {
                is SubmissionsUiState.Loading -> Unit
                is SubmissionsUiState.Gone -> CairnEmptyState(
                    heading = "This study is gone",
                    body = "It was removed from this device. Go back to your studies.",
                )
                is SubmissionsUiState.Ready -> Ready(state, onSubmission, onFilter)
            }
        }
    }
}

@Composable
private fun SubmissionsAppBar(state: SubmissionsUiState, onBack: () -> Unit) {
    val ready = state as? SubmissionsUiState.Ready
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
                    contentDescription = ready?.studyName ?: "Back",
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
                CairnSectionHeading(ready?.studyName.orEmpty())
                Text(
                    text = "Submissions",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.testTag("screen_title"),
                )
            }
        }
        HorizontalDivider(thickness = Spacing.Hairline, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun Ready(
    state: SubmissionsUiState.Ready,
    onSubmission: (String, String) -> Unit,
    onFilter: (ReviewFilter) -> Unit,
) {
    Column(
        Modifier.padding(Spacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.Large),
    ) {
        Filters(state.filter, onFilter)

        // The count is under the chips because it is a description of what is
        // on screen, not a heading for it — and it changes when a chip does.
        Text(
            text = countLine(state),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("count_line"),
        )

        when {
            state.emptyBecauseOfFilter -> CairnEmptyState(
                heading = "Nothing ${state.filter.label.lowercase()} here",
                body = "Choose a different filter to see the rest of this study.",
            )
            state.visible.isEmpty() -> CairnEmptyState(
                heading = "Nothing collected yet",
                body = "Submissions appear here as collectors upload them.",
            )
            else -> CairnCard(Modifier.testTag("submissions")) {
                state.visible.forEachIndexed { index, row ->
                    if (index > 0) CairnRowDivider()
                    CairnListRow(
                        primary = row.label,
                        secondary = row.detail,
                        mono = true,
                        onClick = { onSubmission(row.collectedBy, row.clientId) },
                        modifier = Modifier.testTag("row_${row.clientId}"),
                        trailing = {
                            CairnStatus(word = row.state.label, color = row.state.colour())
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.Large))
    }
}

/**
 * Horizontally scrollable, because four chips plus their padding do not fit
 * across a narrow phone in every language and a wrapped chip row reads as two
 * groups of filters rather than one.
 */
@Composable
private fun Filters(selected: ReviewFilter, onFilter: (ReviewFilter) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag("filters"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        ReviewFilter.entries.forEach { filter ->
            CairnChip(
                label = filter.label,
                selected = filter == selected,
                onClick = { onFilter(filter) },
                modifier = Modifier.testTag("filter_${filter.name.lowercase()}"),
            )
        }
    }
}

/**
 * How much this study holds, or how much of what is loaded a chip is showing.
 *
 * Unfiltered, the numbers come from the **database**, not from the rows on
 * screen: the list is capped, so a season's study shows two hundred rows out of
 * a thousand, and "200 submissions" would be a claim about the study that is
 * simply false. Counting is what the query is for.
 *
 * Filtered, the sentence is about what is on screen and says so — "2 of 5
 * shown". A filtered list that stated only its own length would invite the
 * reading that the study holds two.
 */
internal fun countLine(state: SubmissionsUiState.Ready): String {
    val counts = state.counts
    if (state.filter != ReviewFilter.ALL) {
        return "${state.visible.size} of ${state.rows.size} shown"
    }
    val total = counts.collected + counts.voided
    return "$total ${plural(total, "submission")} · ${counts.locked} locked"
}

/**
 * `DESIGN.md`'s colour roles, not a new palette: moss for what is settled, ochre
 * for what still needs someone, muted for what has left the analysis. The word
 * always travels with the dot, so none of this is load-bearing.
 */
@Composable
internal fun ReviewState.colour(): androidx.compose.ui.graphics.Color = when (this) {
    ReviewState.OPEN -> MaterialTheme.colorScheme.tertiary
    ReviewState.LOCKED -> MaterialTheme.colorScheme.primary
    ReviewState.VOIDED -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Preview(name = "Submissions", widthDp = 390, heightDp = 900)
@Composable
private fun SubmissionsPreview() {
    CairnTheme {
        SubmissionsScreen(
            state = previewSubmissions(),
            onSubmission = { _, _ -> },
            onFilter = {},
            onBack = {},
        )
    }
}

@Preview(name = "Submissions filtered to open", widthDp = 390, heightDp = 900)
@Composable
private fun SubmissionsFilteredPreview() {
    CairnTheme {
        SubmissionsScreen(
            state = previewSubmissions().copy(filter = ReviewFilter.OPEN),
            onSubmission = { _, _ -> },
            onFilter = {},
            onBack = {},
        )
    }
}

@Preview(name = "Submissions empty", widthDp = 390, heightDp = 844)
@Composable
private fun SubmissionsEmptyPreview() {
    CairnTheme {
        SubmissionsScreen(
            state = previewSubmissions().copy(
                rows = emptyList(),
                counts = app.cairn.core.database.dao.ReviewCounts(0, 0, 0, 0),
            ),
            onSubmission = { _, _ -> },
            onFilter = {},
            onBack = {},
        )
    }
}
