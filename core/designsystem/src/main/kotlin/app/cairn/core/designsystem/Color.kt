package app.cairn.core.designsystem

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The palette from `DESIGN.md`, which is the source of truth.
 *
 * Stitch stored slightly different values when it generated the screens
 * (`#346f53` for primary, `#fffcf6` for surface). Where the two disagree the
 * design MD wins, so these are its numbers and not what the mockups render.
 */
public object CairnColors {
    public val Primary: Color = Color(0xFF2F6B4F)
    public val PrimaryContainer: Color = Color(0xFFCDE7D6)
    public val Secondary: Color = Color(0xFF5F6F66)
    public val Tertiary: Color = Color(0xFFC8792E)
    public val Surface: Color = Color(0xFFFBF8F2)
    public val SurfaceContainer: Color = Color(0xFFF3EFE5)
    public val Outline: Color = Color(0xFFDED8CA)
    public val OnSurface: Color = Color(0xFF1E211F)
    public val OnSurfaceVariant: Color = Color(0xFF5F6F66)
    public val Error: Color = Color(0xFFA33A2B)
    public val OnPrimary: Color = Color(0xFFFFFFFF)
    public val OnError: Color = Color(0xFFFFFFFF)
}

internal val CairnLightColors = lightColorScheme(
    primary = CairnColors.Primary,
    onPrimary = CairnColors.OnPrimary,
    primaryContainer = CairnColors.PrimaryContainer,
    onPrimaryContainer = CairnColors.OnSurface,
    secondary = CairnColors.Secondary,
    onSecondary = CairnColors.OnPrimary,
    tertiary = CairnColors.Tertiary,
    onTertiary = CairnColors.OnPrimary,
    background = CairnColors.Surface,
    onBackground = CairnColors.OnSurface,
    surface = CairnColors.Surface,
    onSurface = CairnColors.OnSurface,
    surfaceContainer = CairnColors.SurfaceContainer,
    surfaceContainerHigh = CairnColors.SurfaceContainer,
    surfaceContainerLow = CairnColors.Surface,
    surfaceVariant = CairnColors.SurfaceContainer,
    onSurfaceVariant = CairnColors.OnSurfaceVariant,
    outline = CairnColors.Outline,
    outlineVariant = CairnColors.Outline,
    error = CairnColors.Error,
    onError = CairnColors.OnError,
)
