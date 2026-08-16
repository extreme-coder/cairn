package app.cairn.feature.collect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import app.cairn.core.designsystem.CairnBanner
import app.cairn.core.designsystem.CairnCard
import app.cairn.core.designsystem.CairnChip
import app.cairn.core.designsystem.CairnEmptyState
import app.cairn.core.designsystem.CairnIcons
import app.cairn.core.designsystem.CairnListRow
import app.cairn.core.designsystem.CairnRowDivider
import app.cairn.core.designsystem.CairnSectionHeading
import app.cairn.core.designsystem.CairnTheme
import app.cairn.core.designsystem.Spacing

/**
 * The Collect screen: one study's forms, and what has recently been recorded in
 * it.
 *
 * There is no floating action button. `DESIGN.md` draws one, but with a list of
 * forms on screen a single "New submission" button would have to pick a form on
 * the collector's behalf, and picking the wrong one is not recoverable — the
 * form version is stamped into the submission. Each form row is the action for
 * its own form instead.
 */
@Composable
public fun CollectScreen(
    state: CollectUiState,
    onForm: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The coordinator's two screens, reached from here rather than from a bottom
     * bar of their own. Defaulted so a preview or a test that is not about review
     * does not have to name them; `:app` wires both and `CairnNavHostTest`
     * asserts that it did.
     */
    onSubmissions: () -> Unit = {},
    onProgress: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { CollectAppBar(state, onBack, actions) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            when (state) {
                is CollectUiState.Loading -> Unit
                is CollectUiState.Gone -> CairnEmptyState(
                    heading = "This study is gone",
                    body = "It was removed from this device. Go back to your studies.",
                )
                is CollectUiState.Ready -> Ready(state, onForm, onSubmissions, onProgress)
            }
        }
    }
}

@Composable
private fun CollectAppBar(
    state: CollectUiState,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    val ready = state as? CollectUiState.Ready
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
            // Leading, which is the only place an arrow can point from. It used
            // to be the word "Studies" sitting at the trailing edge — where a
            // left-pointing arrow would have been pointing at the study name.
            // The destination is not lost: it is what the arrow announces.
            IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
                Icon(
                    imageVector = CairnIcons.Back,
                    contentDescription = "Studies",
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
                CairnSectionHeading("Study")
                Text(
                    text = ready?.studyName.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.testTag("study_name"),
                )
            }
            ready?.role?.let {
                Spacer(Modifier.size(Spacing.Small))
                CairnChip(it.label, Modifier.testTag("role"))
            }
            actions()
        }
        HorizontalDivider(thickness = Spacing.Hairline, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun Ready(
    state: CollectUiState.Ready,
    onForm: (String) -> Unit,
    onSubmissions: () -> Unit,
    onProgress: () -> Unit,
) {
    Column(
        Modifier.padding(Spacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.Large),
    ) {
        if (state.pendingCount > 0) {
            // The same sentence the capture screen uses. One phrasing per
            // concept, so a collector reads the queue the same way everywhere.
            CairnBanner(
                text = "${state.pendingCount} queued, uploading when you reconnect.",
                modifier = Modifier.testTag("queue_banner"),
            )
        }

        CairnSectionHeading("Forms")
        if (state.forms.isEmpty()) {
            Text(
                text = "Forms appear here once a coordinator publishes one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("no_forms"),
            )
        } else {
            CairnCard(Modifier.testTag("forms")) {
                state.forms.forEachIndexed { index, form ->
                    if (index > 0) CairnRowDivider()
                    CairnListRow(
                        primary = form.title,
                        secondary = form.detail,
                        onClick = if (form.openable) {
                            { onForm(form.id) }
                        } else {
                            null
                        },
                        modifier = Modifier.testTag("form_${form.id}"),
                        trailing = form.versionLabel?.let { label ->
                            { CairnChip(label) }
                        },
                    )
                }
            }
        }

        /*
         * Review lives inside the study, not in the bottom bar.
         *
         * `DESIGN.md` draws a coordinator's own bar — Submissions · Progress ·
         * Forms · Settings — and that would be right if a person had one role.
         * A role is a row in `study_members`, so the same person can coordinate
         * one study and collect in another, and a coordinator collects too:
         * insert is allowed for pi, coordinator and collector alike. Swapping
         * the bar on entering a study would take Collect and Queue away from the
         * person most likely to be standing in a field with them. Reviewing is
         * something done *to* a study, so it hangs off the study.
         */
        if (state.canReview) {
            Spacer(Modifier.height(Spacing.Small))
            CairnSectionHeading("Review")
            CairnCard(Modifier.testTag("review")) {
                CairnListRow(
                    primary = "Submissions",
                    secondary = "Everything collected in this study",
                    onClick = onSubmissions,
                    modifier = Modifier.testTag("open_submissions"),
                )
                CairnRowDivider()
                CairnListRow(
                    primary = "Progress",
                    secondary = "How much has been collected, by day",
                    onClick = onProgress,
                    modifier = Modifier.testTag("open_progress"),
                )
            }
        }

        if (state.recent.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.Small))
            CairnSectionHeading("Recent")
            CairnCard(Modifier.testTag("recent")) {
                state.recent.forEachIndexed { index, row ->
                    if (index > 0) CairnRowDivider()
                    SubmissionListRow(row)
                }
            }
        }
        Spacer(Modifier.height(Spacing.Large))
    }
}

@Preview(name = "Collect", widthDp = 390, heightDp = 844)
@Composable
private fun CollectPreview() {
    CairnTheme {
        CollectScreen(state = previewCollect(), onForm = {}, onBack = {})
    }
}

@Preview(name = "Collect as coordinator", widthDp = 390, heightDp = 900)
@Composable
private fun CollectCoordinatorPreview() {
    CairnTheme {
        CollectScreen(state = previewCoordinatorCollect(), onForm = {}, onBack = {})
    }
}

@Preview(name = "Collect with no forms", widthDp = 390, heightDp = 844)
@Composable
private fun CollectNoFormsPreview() {
    CairnTheme {
        CollectScreen(
            state = previewCollect().copy(forms = emptyList(), recent = emptyList(), pendingCount = 0),
            onForm = {},
            onBack = {},
        )
    }
}
