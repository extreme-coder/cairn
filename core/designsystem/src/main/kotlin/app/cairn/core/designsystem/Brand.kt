package app.cairn.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * The Cairn mark: four stacked rounded rectangles, widest at the bottom.
 *
 * Drawn rather than drawn *from* an asset. The design forbids images outright,
 * and four rectangles are cheaper to render, cheaper to review and impossible to
 * ship at the wrong density. The widths come from `DESIGN.md` and are the mark —
 * do not round them to a nicer scale.
 */
@Composable
public fun CairnMark(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Column(
        modifier
            .semantics { contentDescription = "Cairn" }
            .testTag("cairn_mark"),
        verticalArrangement = Arrangement.spacedBy(StoneGap),
    ) {
        StoneWidths.forEach { width ->
            Box(
                Modifier
                    .width(width.dp)
                    .height(StoneHeight)
                    .background(color, RoundedCornerShape(2.dp)),
            )
        }
    }
}

private val StoneWidths = listOf(13, 20, 27, 34)
private val StoneHeight = 7.dp
private val StoneGap = 4.dp

@Preview
@Composable
private fun CairnMarkPreview() {
    CairnTheme { CairnMark() }
}
