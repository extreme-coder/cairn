package app.cairn.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * The app's icons, drawn rather than depended on.
 *
 * `DESIGN.md` asks for Material Symbols Outlined at 24dp, stroke only.
 * `androidx.compose.material:material-icons-extended` is the obvious way to get
 * them and is the wrong one: it stopped shipping at 1.7.8 in February 2025, so
 * it is not in the Compose BOM this project builds against, and pinning it
 * would put a frozen 2025 artifact under a 2026 runtime for the sake of nine
 * glyphs.
 *
 * So they are paths here, on a 24-unit grid, stroke-only at [StrokeWidth] with
 * round caps and joins. That is the same call [CairnMark] made and for the same
 * reasons — no asset, no density to get wrong, and nothing in the tree that the
 * "no images" constraint would have to make an exception for. The objection to
 * hand-written path data is that nobody can check it by reading it, which is
 * true, so it is checked by looking instead: every icon is in the preview below
 * and in `IconSheetScreenshotTest`.
 *
 * These are affordances, not decoration. An icon goes in where it replaces a
 * word that was standing in for a control — back, close, reveal — or where it
 * rides alongside a word that stays. It never replaces a label that carries
 * meaning: "Sign out", "Upload now" and "Discard" are words on purpose.
 */
public object CairnIcons {

    /** Back. Points where it goes, and the app bar keeps the destination's name off the row. */
    public val Back: ImageVector = icon("Back") {
        stroke {
            moveTo(19f, 12f)
            lineTo(5f, 12f)
            moveTo(11f, 6f)
            lineTo(5f, 12f)
            lineTo(11f, 18f)
        }
    }

    /** Close. Dismisses without deciding anything — never used for a destructive action. */
    public val Close: ImageVector = icon("Close") {
        stroke {
            moveTo(6f, 6f)
            lineTo(18f, 18f)
            moveTo(18f, 6f)
            lineTo(6f, 18f)
        }
    }

    /** The row goes somewhere. Trailing, after any chip or status. */
    public val ChevronRight: ImageVector = icon("ChevronRight") {
        stroke {
            moveTo(10f, 6f)
            lineTo(16f, 12f)
            lineTo(10f, 18f)
        }
    }

    /** Expands a section that is currently collapsed. */
    public val ChevronDown: ImageVector = icon("ChevronDown") {
        stroke {
            moveTo(6f, 10f)
            lineTo(12f, 16f)
            lineTo(18f, 10f)
        }
    }

    /** Collapses a section that is currently open. */
    public val ChevronUp: ImageVector = icon("ChevronUp") {
        stroke {
            moveTo(6f, 14f)
            lineTo(12f, 8f)
            lineTo(18f, 14f)
        }
    }

    /** The password is masked; tapping shows it. */
    public val Eye: ImageVector = icon("Eye") {
        stroke {
            eye()
            circle(12f, 12f, 3f)
        }
    }

    /** The password is showing; tapping masks it. */
    public val EyeOff: ImageVector = icon("EyeOff") {
        stroke {
            eye()
            circle(12f, 12f, 3f)
            moveTo(3.5f, 3.5f)
            lineTo(20.5f, 20.5f)
        }
    }

    /**
     * Work on this device the server has not taken yet.
     *
     * One glyph per concept, the way the voice guide keeps one word per concept:
     * this is the Queue tab, the queue banner and nothing else.
     */
    public val Upload: ImageVector = icon("Upload") {
        stroke {
            moveTo(12f, 15f)
            lineTo(12f, 4f)
            moveTo(7f, 9f)
            lineTo(12f, 4f)
            lineTo(17f, 9f)
            moveTo(4f, 15f)
            lineTo(4f, 20f)
            lineTo(20f, 20f)
            lineTo(20f, 15f)
        }
    }

    /** A form to record into: the Collect tab. */
    public val Form: ImageVector = icon("Form") {
        stroke {
            moveTo(7f, 3f)
            lineTo(17f, 3f)
            arcTo(2f, 2f, 0f, false, true, 19f, 5f)
            lineTo(19f, 19f)
            arcTo(2f, 2f, 0f, false, true, 17f, 21f)
            lineTo(7f, 21f)
            arcTo(2f, 2f, 0f, false, true, 5f, 19f)
            lineTo(5f, 5f)
            arcTo(2f, 2f, 0f, false, true, 7f, 3f)
            close()
            moveTo(8.5f, 8f)
            lineTo(15.5f, 8f)
            moveTo(8.5f, 12f)
            lineTo(15.5f, 12f)
            moveTo(8.5f, 16f)
            lineTo(12.5f, 16f)
        }
    }

    /**
     * The Settings tab: a gear.
     *
     * The teeth are part of the outline, not spokes stuck on a ring. The first
     * attempt drew them as eight radial strokes and rendered as a sun — see the
     * note on [CairnIconSheet] about why that was caught before it shipped.
     */
    public val Settings: ImageVector = icon("Settings") {
        stroke {
            gear(outer = 10.2f, inner = 7.2f, teeth = 8)
            circle(12f, 12f, 3f)
        }
    }

    /**
     * Something is wrong and the sentence beside it says what.
     *
     * `DESIGN.md` asks for an error icon where the helper text would be. It is
     * always tinted with the error colour and always carries its sentence, so it
     * still satisfies the rule that status is never colour alone.
     */
    public val Alert: ImageVector = icon("Alert") {
        stroke {
            circle(12f, 12f, 9f)
            moveTo(12f, 7.2f)
            lineTo(12f, 13f)
        }
        fill {
            circle(12f, 16.3f, 1.15f)
        }
    }
}

/** `DESIGN.md`: icons are 24dp. */
public val IconSize: Dp = 24.dp

/** Stroke only, on the 24-unit grid the icons are drawn on. */
private const val StrokeWidth = 2f

private fun icon(name: String, build: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = IconSize,
        defaultHeight = IconSize,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(build).build()

/**
 * Black is not the colour these render in. `Icon` tints the whole vector, so the
 * brush here only has to be opaque — the call site's tint is what is seen.
 */
private fun ImageVector.Builder.stroke(body: PathBuilder.() -> Unit) {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = StrokeWidth,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = body,
    )
}

private fun ImageVector.Builder.fill(body: PathBuilder.() -> Unit) {
    path(fill = SolidColor(Color.Black), pathBuilder = body)
}

/** Two half arcs, because one 360-degree arc has no end point to give. */
private fun PathBuilder.circle(centreX: Float, centreY: Float, radius: Float) {
    moveTo(centreX + radius, centreY)
    arcTo(radius, radius, 0f, false, true, centreX - radius, centreY)
    arcTo(radius, radius, 0f, false, true, centreX + radius, centreY)
    close()
}

/**
 * A gear silhouette centred on the grid: for each tooth, out to the tip, across
 * it, back down the flank, and along the valley to the next one.
 *
 * [outer] is the tip radius and [inner] the valley radius. The two fractions are
 * of one tooth's angular period — a tooth occupies a little under half of its
 * period at the tip, and the flank takes the rest.
 */
private fun PathBuilder.gear(outer: Float, inner: Float, teeth: Int) {
    val step = 360f / teeth
    val tip = step * 0.22f
    val flank = step * 0.36f
    var started = false
    repeat(teeth) { index ->
        val at = index * step
        listOf(
            outer to at - tip,
            outer to at + tip,
            inner to at + flank,
            inner to at + step - flank,
        ).forEach { (radius, degrees) ->
            val radians = Math.toRadians(degrees.toDouble())
            val x = 12f + radius * cos(radians).toFloat()
            val y = 12f + radius * sin(radians).toFloat()
            if (started) lineTo(x, y) else moveTo(x, y).also { started = true }
        }
    }
    close()
}

/** The lens, symmetric about both axes so the pupil sits in the middle of it. */
private fun PathBuilder.eye() {
    moveTo(2f, 12f)
    curveTo(5.2f, 7.4f, 8.4f, 5.8f, 12f, 5.8f)
    curveTo(15.6f, 5.8f, 18.8f, 7.4f, 22f, 12f)
    curveTo(18.8f, 16.6f, 15.6f, 18.2f, 12f, 18.2f)
    curveTo(8.4f, 18.2f, 5.2f, 16.6f, 2f, 12f)
    close()
}

/**
 * Every icon at once, at the size they ship and again at four times it.
 *
 * The small row is what a collector sees. The large row is the only way to tell
 * whether a curve is right, which is what makes drawn path data reviewable at
 * all — see `IconSheetScreenshotTest`, which renders exactly this.
 */
@Composable
public fun CairnIconSheet(modifier: Modifier = Modifier) {
    val icons = listOf(
        "Back" to CairnIcons.Back,
        "Close" to CairnIcons.Close,
        "ChevronRight" to CairnIcons.ChevronRight,
        "ChevronDown" to CairnIcons.ChevronDown,
        "ChevronUp" to CairnIcons.ChevronUp,
        "Eye" to CairnIcons.Eye,
        "EyeOff" to CairnIcons.EyeOff,
        "Upload" to CairnIcons.Upload,
        "Form" to CairnIcons.Form,
        "Settings" to CairnIcons.Settings,
        "Alert" to CairnIcons.Alert,
    )
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(Spacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.Large),
    ) {
        // Chunked to what the narrowest phone fits. A row that overflows does
        // not clip here, it squeezes the last icon, which is a lie about the
        // geometry and is exactly what this sheet exists to rule out.
        icons.chunked(6).forEach { group ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                group.forEach { (name, image) ->
                    Icon(
                        imageVector = image,
                        contentDescription = name,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(IconSize),
                    )
                }
            }
        }
        icons.chunked(3).forEach { group ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                group.forEach { (name, image) ->
                    Icon(
                        imageVector = image,
                        contentDescription = name,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(IconSize * 4),
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 480)
@Composable
private fun CairnIconSheetPreview() {
    CairnTheme { CairnIconSheet() }
}
