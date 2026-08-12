package app.cairn.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview

/**
 * The empty state from `DESIGN.md`: a heading naming what is absent and one line
 * saying what will appear here or what to do about it. No illustration — there
 * are no images anywhere in this app.
 *
 * [heading] is two to six words, [body] five to twenty. Those are the design's
 * budgets and they are what keeps an empty screen from turning into an apology.
 */
@Composable
public fun CairnEmptyState(
    heading: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = heading,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("empty_heading"),
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("empty_body"),
        )
    }
}

@Preview(widthDp = 390, heightDp = 400)
@Composable
private fun CairnEmptyStatePreview() {
    CairnTheme {
        CairnEmptyState(
            heading = "No studies yet",
            body = "Ask your coordinator to add you to a study.",
        )
    }
}
