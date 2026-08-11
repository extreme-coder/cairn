package app.cairn.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Cards and sheets 16, inputs and buttons 12, chips full. */
internal val CairnShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Light only, deliberately.
 *
 * The app is read outdoors in direct sunlight, and the palette is a warm paper
 * one whose contrast ratios were chosen for that. A dark scheme is a separate
 * design exercise, not an inversion of this one, so it is absent rather than
 * wrong.
 */
@Composable
public fun CairnTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CairnLightColors,
        typography = CairnTypography,
        shapes = CairnShapes,
        content = content,
    )
}

/** Spacing scale from `DESIGN.md`. Screen gutter is [Gutter]. */
public object Spacing {
    public val Hairline: Dp = 1.dp
    public val XSmall: Dp = 4.dp
    public val Small: Dp = 8.dp
    public val Medium: Dp = 12.dp
    public val Large: Dp = 16.dp
    public val XLarge: Dp = 24.dp
    public val XXLarge: Dp = 32.dp
    public val Gutter: Dp = 20.dp

    /** The collector may be wearing gloves. */
    public val CaptureControl: Dp = 56.dp
}
