package app.cairn.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The components `DESIGN.md` names, in the one module every screen already
 * depends on.
 *
 * They live here rather than in a feature because each has more than one
 * consumer: a list row is a study, a form and a queued submission; a status is a
 * dot plus a word on four different screens; and the day one of them is wrong is
 * the day it should be wrong in one place.
 */

/** Surface container, hairline outline, radius 16, no shadow. Shadows are not in the design. */
@Composable
public fun CairnCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                Spacing.Hairline,
                MaterialTheme.colorScheme.outline,
                MaterialTheme.shapes.medium,
            ),
    ) {
        content()
    }
}

/** The running head above a section: 11px, semibold, letterspaced, upper case. */
@Composable
public fun CairnSectionHeading(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * A list row: at least 72dp, a primary line, an optional second line of
 * metadata, and a trailing slot.
 *
 * A row that goes somewhere ends in a chevron, the way `DESIGN.md` draws it.
 * The whole row is still the target — the chevron is the sign, not the button,
 * which keeps the target the large one for someone wearing gloves. A row with
 * no [onClick] gets no chevron, so the queue's rows stay honestly inert.
 */
@Composable
public fun CairnListRow(
    primary: String,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    mono: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = ListRowHeight)
            .padding(horizontal = Spacing.Large, vertical = Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.XSmall)) {
            Text(
                text = primary,
                style = if (mono) {
                    MaterialTheme.typography.labelMedium
                } else {
                    MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight(500))
                },
                color = MaterialTheme.colorScheme.onSurface,
            )
            secondary?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.let {
            Spacer(Modifier.size(Spacing.Medium))
            it()
        }
        if (onClick != null) {
            Spacer(Modifier.size(Spacing.Small))
            Icon(
                imageVector = CairnIcons.ChevronRight,
                // The row already announces itself; a second "go to" would be
                // read out after every study name.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize),
            )
        }
    }
}

/** Hairlines between rows are inset to the text, not full-bleed. */
@Composable
public fun CairnRowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = Spacing.Large),
        thickness = Spacing.Hairline,
        color = MaterialTheme.colorScheme.outline,
    )
}

/**
 * Status is never colour alone. The dot always travels with a word, so it
 * survives being read by someone who cannot tell ochre from moss.
 */
@Composable
public fun CairnStatus(
    word: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        CairnDot(color)
        Spacer(Modifier.size(Spacing.Small))
        Text(
            text = word,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
public fun CairnDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(DotSize)
            .background(color, CircleShape),
    )
}

/** Full radius, hairline outline, 13px label. Role chips are outline-only. */
@Composable
public fun CairnChip(
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .border(Spacing.Hairline, MaterialTheme.colorScheme.outline, CircleShape)
            .padding(horizontal = Spacing.Medium, vertical = 3.dp),
    )
}

/**
 * The connection banner: ochre container, one line, a fact and a consequence.
 *
 * Never an apology, and only present when there is something to state — an
 * always-visible banner is furniture, and furniture stops being read.
 *
 * [icon] leads, the way `DESIGN.md` draws it. It defaults to [CairnIcons.Upload]
 * rather than the wifi-off glyph the design names, because this banner is not
 * an offline banner: it is raised by a non-empty queue whether or not there is
 * a signal, and a struck-through aerial would be a claim about the network the
 * app has not made.
 */
@Composable
public fun CairnBanner(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    icon: ImageVector = CairnIcons.Upload,
) {
    val accent = if (color == Color.Unspecified) MaterialTheme.colorScheme.tertiary else color
    Row(
        modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.12f), MaterialTheme.shapes.small)
            .border(Spacing.Hairline, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .padding(Spacing.Large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            // The sentence beside it is the whole message. Naming the glyph too
            // would have the banner read out twice.
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(IconSize),
        )
        Spacer(Modifier.size(Spacing.Medium))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * One of the three numbers at the top of the Queue: a stat over a status.
 *
 * The number uses the display slot, which `DESIGN.md` reserves for exactly this
 * — a figure meant to be read across a table, not a heading.
 */
@Composable
public fun CairnStat(
    value: Int,
    word: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(vertical = Spacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.XSmall),
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("stat_${word.lowercase()}"),
        )
        CairnStatus(word = word, color = color)
    }
}

/** One destination in [CairnBottomBar]. [badge] is not drawn when it is zero. */
public data class CairnDestination(
    public val label: String,
    public val icon: ImageVector,
    public val badge: Int = 0,
)

/**
 * Bottom navigation: three or four destinations, a Material Symbol over a label.
 *
 * The label stays. `DESIGN.md` requires it — "labels always visible" — and the
 * glyph is what makes the row scannable at arm's length, not what replaces the
 * word. The badge is still a number, because the voice guide says quantify: a
 * dot would say "something" where the collector needs to know "six". It sits on
 * the icon's corner now rather than beside the word, so a two-digit queue stops
 * shoving "Queue" off its centre.
 */
@Composable
public fun CairnBottomBar(
    destinations: List<CairnDestination>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        HorizontalDivider(
            thickness = Spacing.Hairline,
            color = MaterialTheme.colorScheme.outline,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            destinations.forEachIndexed { index, destination ->
                val active = index == selected
                val tint = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondary
                }
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = NavItemHeight)
                        .clickable(onClick = { onSelect(index) })
                        .padding(vertical = Spacing.Small)
                        .testTag("nav_${destination.label.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = destination.icon,
                            // The label is directly underneath and is what a
                            // screen reader should say.
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(IconSize),
                        )
                        if (destination.badge > 0) {
                            Text(
                                text = destination.badge.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .onIconCorner()
                                    .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                                    .padding(horizontal = Spacing.Small, vertical = 1.dp)
                                    .testTag("badge_${destination.label.lowercase()}"),
                            )
                        }
                    }
                    Spacer(Modifier.size(Spacing.XSmall))
                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (active) FontWeight(600) else FontWeight(500),
                        ),
                        color = tint,
                    )
                }
            }
        }
    }
}

/**
 * Hangs the badge off the icon's top-trailing corner without taking part in the
 * measurement.
 *
 * Reporting zero size is the point: a plain offset would still let a two-digit
 * badge widen the box it shares with the icon, which would drag the glyph — and
 * the label under it — off the centre of its column. The parent centres a
 * zero-sized node at the middle of the icon, so the offsets below are measured
 * from there.
 */
private fun Modifier.onIconCorner(): Modifier = layout { measurable, constraints ->
    val badge = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(0, 0) {
        badge.place(x = BadgeInsetX.roundToPx(), y = -(IconSize / 2 + BadgeLiftY).roundToPx())
    }
}

/** `DESIGN.md`: list rows are at least 72dp. */
public val ListRowHeight: Dp = 72.dp

/** Icon over label needs more than a single line of text did. */
private val NavItemHeight = 64.dp

/** From the icon's centre: just past its right edge, just above its top. */
private val BadgeInsetX = 2.dp
private val BadgeLiftY = 6.dp

private val DotSize = 10.dp

/**
 * Every component that carries an icon, in one place.
 *
 * Public because no screen owns the bottom bar and no screen shows a navigable
 * row next to an inert one, so this is the only place the two can be compared —
 * by a reviewer in the preview, and by `IconSheetScreenshotTest` in a PNG.
 */
@Composable
public fun ComponentGallery(modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(Spacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.Large),
    ) {
        CairnBanner("6 queued, uploading when you reconnect.")
        CairnSectionHeading("Forms")
        CairnCard {
            CairnListRow(
                primary = "Baseline intake",
                secondary = "v3 · 12 fields",
                onClick = {},
                trailing = { CairnChip("Collector") },
            )
            CairnRowDivider()
            CairnListRow(
                primary = "KL-0148",
                secondary = "Baseline intake v3 · 09:14",
                mono = true,
                trailing = { CairnStatus("Queued", MaterialTheme.colorScheme.tertiary) },
            )
        }
        CairnBottomBar(
            destinations = listOf(
                CairnDestination("Collect", CairnIcons.Form),
                CairnDestination("Queue", CairnIcons.Upload, badge = 6),
                CairnDestination("Settings", CairnIcons.Settings),
            ),
            selected = 0,
            onSelect = {},
        )
        CairnBottomBar(
            destinations = listOf(
                CairnDestination("Collect", CairnIcons.Form),
                CairnDestination("Queue", CairnIcons.Upload, badge = 128),
                CairnDestination("Settings", CairnIcons.Settings),
            ),
            selected = 1,
            onSelect = {},
        )
    }
}

@Preview(widthDp = 390)
@Composable
private fun ComponentsPreview() {
    CairnTheme { ComponentGallery() }
}
