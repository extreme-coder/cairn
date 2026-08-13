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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's icons: **Material Symbols Outlined**, weight 400, exactly as
 * `DESIGN.md` asks for.
 *
 * The path data below is copied verbatim from Google's `material-design-icons`
 * repository, Apache License 2.0 — see `THIRD-PARTY.md`. Verbatim is the point.
 * These were hand-drawn approximations first, and the trouble with a hand-drawn
 * glyph is not that it looks wrong, it is that nobody can tell whether it is
 * *the* glyph. A string that diffs clean against upstream can be checked by
 * anyone in one command:
 *
 * ```
 * curl -s https://raw.githubusercontent.com/google/material-design-icons/master/\
 * symbols/web/settings/materialsymbolsoutlined/settings_24px.svg
 * ```
 *
 * **These are filled paths, not strokes.** Material Symbols "Outlined" means
 * the glyph *looks* hollow; it is drawn as a single filled path tracing that
 * outline, with no stroke anywhere. `DESIGN.md` used to ask for "stroke only",
 * which was never compatible with the Material Symbols it asks for in the same
 * sentence. The design page now says filled — see [[design]] in the wiki.
 *
 * Material Symbols are drawn on a 960 grid with the baseline at y=0, so the
 * viewBox is `0 -960 960 960`. `ImageVector` has no viewport *origin*, only a
 * size, so [icon] shifts the group down by 960 instead. That is the whole
 * adaptation — no coordinate is rewritten.
 *
 * These are affordances, not decoration. An icon goes in where it replaces a
 * word that was standing in for a control — back, close, reveal — or where it
 * rides alongside a word that stays. It never replaces a label that carries
 * meaning: "Sign out", "Upload now" and "Discard" are words on purpose.
 */
public object CairnIcons {

    /** Back. Leading, which is the only place an arrow can point from. */
    public val Back: ImageVector = icon("Back", ARROW_BACK)

    /** Close. Dismisses without deciding anything — never a destructive action. */
    public val Close: ImageVector = icon("Close", CLOSE)

    /** The row goes somewhere. Trailing, after any chip or status. */
    public val ChevronRight: ImageVector = icon("ChevronRight", CHEVRON_RIGHT)

    /** Expands a section that is currently collapsed. */
    public val ChevronDown: ImageVector = icon("ChevronDown", EXPAND_MORE)

    /** Collapses a section that is currently open. */
    public val ChevronUp: ImageVector = icon("ChevronUp", EXPAND_LESS)

    /** The password is masked; tapping shows it. */
    public val Eye: ImageVector = icon("Eye", VISIBILITY)

    /** The password is showing; tapping masks it. */
    public val EyeOff: ImageVector = icon("EyeOff", VISIBILITY_OFF)

    /**
     * Work on this device the server has not taken yet.
     *
     * One glyph per concept, the way the voice guide keeps one word per
     * concept: this is the Queue tab, the queue banner, and nothing else.
     */
    public val Upload: ImageVector = icon("Upload", UPLOAD)

    /** A form to record into: the Collect tab. */
    public val Form: ImageVector = icon("Form", DESCRIPTION)

    /** The Settings tab. */
    public val Settings: ImageVector = icon("Settings", SETTINGS)

    /**
     * Something is wrong and the sentence beside it says what.
     *
     * Always tinted with the error colour and always carrying its sentence,
     * so it still satisfies the rule that status is never colour alone.
     */
    public val Alert: ImageVector = icon("Alert", ERROR)
}

/** `DESIGN.md`: icons are 24dp. */
public val IconSize: Dp = 24.dp

/**
 * One Material Symbol, adapted to `ImageVector` without touching a coordinate.
 *
 * The group translation is what reconciles the `0 -960 960 960` viewBox with a
 * viewport that can only be a size. Black is not the colour these render in —
 * `Icon` tints the whole vector, so the brush only has to be opaque.
 */
private fun icon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = IconSize,
        defaultHeight = IconSize,
        viewportWidth = GridSize,
        viewportHeight = GridSize,
    )
        .addGroup(name = name, translationY = GridSize)
        .addPath(addPathNodes(pathData), fill = SolidColor(Color.Black))
        .clearGroup()
        .build()

/** Material Symbols are drawn on a 960-unit grid, not a 24-unit one. */
private const val GridSize = 960f

/*
 * Verbatim from google/material-design-icons, Apache License 2.0.
 * symbols/web/<name>/materialsymbolsoutlined/<name>_24px.svg
 * Do not reformat, re-space or "tidy" these — the value is that they diff
 * clean against upstream.
 */
private const val ARROW_BACK =
    "m313-440 224 224-57 56-320-320 320-320 57 56-224 224h487v80H313Z"

private const val CLOSE =
    "m256-200-56-56 224-224-224-224 56-56 224 224 224-224 56 56-224 224 224 224-56 56-224-224-224 224Z"

private const val CHEVRON_RIGHT =
    "M504-480 320-664l56-56 240 240-240 240-56-56 184-184Z"

private const val EXPAND_MORE =
    "M480-345 240-585l56-56 184 184 184-184 56 56-240 240Z"

private const val EXPAND_LESS =
    "m296-345-56-56 240-240 240 240-56 56-184-184-184 184Z"

private const val VISIBILITY =
    "M480-320q75 0 127.5-52.5T660-500q0-75-52.5-127.5T480-680q-75 0-127.5 52.5T300-500q0 75 52.5 127.5T480-320Zm0-72q-45 0-76.5-31.5T372-500q0-45 31.5-76.5T480-608q45 0 76.5 31.5T588-500q0 45-31.5 76.5T480-392Zm0 192q-146 0-266-81.5T40-500q54-137 174-218.5T480-800q146 0 266 81.5T920-500q-54 137-174 218.5T480-200Zm0-300Zm0 220q113 0 207.5-59.5T832-500q-50-101-144.5-160.5T480-720q-113 0-207.5 59.5T128-500q50 101 144.5 160.5T480-280Z"

private const val VISIBILITY_OFF =
    "m644-428-58-58q9-47-27-88t-93-32l-58-58q17-8 34.5-12t37.5-4q75 0 127.5 52.5T660-500q0 20-4 37.5T644-428Zm128 126-58-56q38-29 67.5-63.5T832-500q-50-101-143.5-160.5T480-720q-29 0-57 4t-55 12l-62-62q41-17 84-25.5t90-8.5q151 0 269 83.5T920-500q-23 59-60.5 109.5T772-302Zm20 246L624-222q-35 11-70.5 16.5T480-200q-151 0-269-83.5T40-500q21-53 53-98.5t73-81.5L56-792l56-56 736 736-56 56ZM222-624q-29 26-53 57t-41 67q50 101 143.5 160.5T480-280q20 0 39-2.5t39-5.5l-36-38q-11 3-21 4.5t-21 1.5q-75 0-127.5-52.5T300-500q0-11 1.5-21t4.5-21l-84-82Zm319 93Zm-151 75Z"

private const val UPLOAD =
    "M440-320v-326L336-542l-56-58 200-200 200 200-56 58-104-104v326h-80ZM240-160q-33 0-56.5-23.5T160-240v-120h80v120h480v-120h80v120q0 33-23.5 56.5T720-160H240Z"

private const val DESCRIPTION =
    "M320-240h320v-80H320v80Zm0-160h320v-80H320v80ZM240-80q-33 0-56.5-23.5T160-160v-640q0-33 23.5-56.5T240-880h320l240 240v480q0 33-23.5 56.5T720-80H240Zm280-520v-200H240v640h480v-440H520ZM240-800v200-200 640-640Z"

private const val SETTINGS =
    "m370-80-16-128q-13-5-24.5-12T307-235l-119 50L78-375l103-78q-1-7-1-13.5v-27q0-6.5 1-13.5L78-585l110-190 119 50q11-8 23-15t24-12l16-128h220l16 128q13 5 24.5 12t22.5 15l119-50 110 190-103 78q1 7 1 13.5v27q0 6.5-2 13.5l103 78-110 190-118-50q-11 8-23 15t-24 12L590-80H370Zm70-80h79l14-106q31-8 57.5-23.5T639-327l99 41 39-68-86-65q5-14 7-29.5t2-31.5q0-16-2-31.5t-7-29.5l86-65-39-68-99 42q-22-23-48.5-38.5T533-694l-13-106h-79l-14 106q-31 8-57.5 23.5T321-633l-99-41-39 68 86 64q-5 15-7 30t-2 32q0 16 2 31t7 30l-86 65 39 68 99-42q22 23 48.5 38.5T427-266l13 106Zm42-180q58 0 99-41t41-99q0-58-41-99t-99-41q-59 0-99.5 41T342-480q0 58 40.5 99t99.5 41Zm-2-140Z"

private const val ERROR =
    "M480-280q17 0 28.5-11.5T520-320q0-17-11.5-28.5T480-360q-17 0-28.5 11.5T440-320q0 17 11.5 28.5T480-280Zm-40-160h80v-240h-80v240Zm40 360q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Zm0-80q134 0 227-93t93-227q0-134-93-227t-227-93q-134 0-227 93t-93 227q0 134 93 227t227 93Zm0-320Z"

/**
 * Every icon at once, at the size they ship and again at four times it.
 *
 * The small rows are what a collector sees; the large ones are how a reviewer
 * checks the geometry. `IconSheetScreenshotTest` renders exactly this.
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
