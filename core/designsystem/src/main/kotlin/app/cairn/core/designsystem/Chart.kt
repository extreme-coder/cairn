package app.cairn.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One column of [CairnBarChart]. [axisLabel] is drawn only where there is room for it. */
public data class CairnBar(
    public val axisLabel: String,
    public val value: Int,
)

/**
 * A bar chart, to `DESIGN.md`'s chart spec: bars in primary with a 4px top
 * radius, dotted 1px gridlines in the outline colour, 11px axis labels, and a
 * one-line caption naming what is counted and over what period.
 *
 * **Drawn, not plotted by a library.** The design forbids images and asks for
 * flat vector, one series and no legend, which is a `Canvas` and about sixty
 * lines. A charting dependency would arrive with axes, animations and a theme,
 * all of which would then have to be turned off to satisfy the same spec — and
 * it would be one more artefact for the `licensee` gate to justify.
 *
 * It lives here rather than in `:feature:review` so it appears in
 * [ComponentGallery] and therefore in the screenshot sheet. A chart is exactly
 * the kind of drawing that can be subtly wrong in a way no assertion catches and
 * a person spots immediately — which is how the hand-drawn gear that rendered as
 * a sun was found.
 *
 * **Every bar in [bars] is drawn, including the zero ones.** A day with no
 * observations is a fact about the study, and dropping it would silently compress
 * the axis so a fortnight's gap read as continuous work.
 */
@Composable
public fun CairnBarChart(
    bars: List<CairnBar>,
    caption: String,
    modifier: Modifier = Modifier,
    height: Dp = ChartHeight,
) {
    val bar = MaterialTheme.colorScheme.primary
    val gridline = MaterialTheme.colorScheme.outline
    val peak = bars.maxOfOrNull { it.value } ?: 0

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CairnSectionHeading(caption, Modifier.testTag("chart_caption"))
            // The peak is the only number the axis would otherwise carry, and a
            // gridline labelled with it is more useful than four unlabelled ones.
            CairnSectionHeading(peak.toString(), Modifier.testTag("chart_peak"))
        }

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                // A canvas has no text for a screen reader to read, so it says
                // what it shows. Without this the whole chart is a blank node.
                .semantics { contentDescription = chartDescription(bars, caption) }
                .testTag("chart"),
        ) {
            val slot = size.width / bars.size.coerceAtLeast(1)
            val barWidth = (slot - BarGap.toPx()).coerceAtLeast(1f)
            val dots = PathEffect.dashPathEffect(floatArrayOf(2f, 4f), 0f)

            // Gridlines first, so a bar sitting exactly on one covers it rather
            // than being cut by it.
            repeat(GRIDLINES + 1) { line ->
                val y = size.height * line / GRIDLINES
                drawLine(
                    color = gridline,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = Hairline.toPx(),
                    pathEffect = if (line == GRIDLINES) null else dots,
                )
            }

            if (peak == 0) return@Canvas

            bars.forEachIndexed { index, column ->
                if (column.value == 0) return@forEachIndexed
                // At least a hairline of ink for any non-zero day. A bar rounded
                // to nothing says "none" about a day that had one.
                val barHeight = (size.height * column.value / peak).coerceAtLeast(MinBarHeight.toPx())
                drawRoundRect(
                    color = bar,
                    topLeft = Offset(slot * index + BarGap.toPx() / 2, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(BarRadius.toPx(), BarRadius.toPx()),
                )
            }
        }

        AxisLabels(bars)
    }
}

/**
 * The first day, the middle one and the last — start-, centre- and end-aligned.
 *
 * Fourteen dates across a phone overlap into a grey smear. The first attempt
 * labelled every third bar inside a cell one fourteenth of the width, which
 * clipped "31 Jul" to "31" and "3 Aug" to "3" — a smear replaced by three
 * ambiguous numbers, which is worse. Three labels that each have room to be a
 * date say what the axis is, and the caption above already says the period.
 */
@Composable
private fun AxisLabels(bars: List<CairnBar>) {
    val labelled = listOfNotNull(
        bars.firstOrNull(),
        bars.getOrNull(bars.size / 2).takeIf { bars.size >= MIN_BARS_FOR_MIDDLE },
        bars.lastOrNull().takeIf { bars.size > 1 },
    )
    Row(
        Modifier
            .fillMaxWidth()
            .testTag("chart_axis"),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        labelled.forEach { column ->
            Text(
                text = column.axisLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/**
 * What the chart says out loud.
 *
 * The peak and the total, not fourteen numbers. A screen reader reading every
 * bar is a minute of speech that answers nothing; "busiest day" and "total" are
 * what someone is looking at the shape for.
 */
private fun chartDescription(bars: List<CairnBar>, caption: String): String {
    val total = bars.sumOf { it.value }
    val busiest = bars.maxByOrNull { it.value }
    return if (busiest == null || total == 0) {
        "$caption. Nothing collected in this period."
    } else {
        "$caption. $total in total, most on ${busiest.axisLabel} with ${busiest.value}."
    }
}

private val ChartHeight = 140.dp
private val BarGap = 6.dp
private val BarRadius = 4.dp
private val MinBarHeight = 2.dp
private val Hairline = 1.dp

private const val GRIDLINES = 4

/** Below this a middle label sits on top of one of its neighbours. */
private const val MIN_BARS_FOR_MIDDLE = 5

@Preview(widthDp = 350)
@Composable
private fun ChartPreview() {
    CairnTheme {
        Column(Modifier.padding(Spacing.Gutter)) {
            CairnBarChart(bars = sampleBars(), caption = "Submissions per day · last 14 days")
        }
    }
}

/** Shared by the preview and [ComponentGallery], so both show the same chart. */
internal fun sampleBars(): List<CairnBar> = listOf(
    CairnBar("30 Jul", 4), CairnBar("31 Jul", 7), CairnBar("1 Aug", 0),
    CairnBar("2 Aug", 0), CairnBar("3 Aug", 11), CairnBar("4 Aug", 9),
    CairnBar("5 Aug", 12), CairnBar("6 Aug", 3), CairnBar("7 Aug", 0),
    CairnBar("8 Aug", 6), CairnBar("9 Aug", 8), CairnBar("10 Aug", 14),
    CairnBar("11 Aug", 5), CairnBar("12 Aug", 2),
)
