package app.cairn.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * All three families are variable fonts under the Open Font License, bundled in
 * `res/font` rather than fetched through the downloadable-fonts provider. The
 * provider needs Google Play Services, which would fail the F-Droid build and
 * break the offline-first claim on a device that has never seen a network.
 */
@OptIn(ExperimentalTextApi::class)
private fun variableFont(resId: Int, weight: Int) = Font(
    resId = resId,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Headlines. Serif, academic, warm. */
public val Literata: FontFamily = FontFamily(
    variableFont(R.font.literata, 400),
    variableFont(R.font.literata, 600),
    variableFont(R.font.literata, 700),
)

/** Body and labels. Humanist, open, legible in daylight at small sizes. */
public val HankenGrotesk: FontFamily = FontFamily(
    variableFont(R.font.hanken_grotesk, 400),
    variableFont(R.font.hanken_grotesk, 500),
    variableFont(R.font.hanken_grotesk, 600),
)

/**
 * Participant codes, versions, timestamps, client ids.
 *
 * Functional rather than decorative: `KL-0148` has to be readable character by
 * character, which a proportional face does not guarantee.
 */
public val JetBrainsMono: FontFamily = FontFamily(
    variableFont(R.font.jetbrains_mono, 500),
)

/**
 * `DESIGN.md`'s scale mapped onto Material 3's slots. The mapping is the
 * contract: a screen asks for `MaterialTheme.typography.labelSmall` and gets the
 * running head, so no screen ever names a font, size or weight of its own.
 *
 * | Design MD      | Material 3 slot |
 * |----------------|-----------------|
 * | Stat number    | `displaySmall`  |
 * | Screen title   | `headlineMedium`|
 * | Section heading| `headlineSmall` |
 * | App bar title  | `titleLarge`    |
 * | Body           | `bodyLarge`     |
 * | Metadata       | `bodySmall`     |
 * | Label          | `labelLarge`    |
 * | Code           | `labelMedium`   |
 * | Running head   | `labelSmall`    |
 */
public val CairnTypography: Typography = Typography(
    displaySmall = TextStyle(
        fontFamily = Literata,
        fontWeight = FontWeight(600),
        fontSize = 34.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Literata,
        fontWeight = FontWeight(600),
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Literata,
        fontWeight = FontWeight(600),
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Literata,
        fontWeight = FontWeight(600),
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = HankenGrotesk,
        fontWeight = FontWeight(400),
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = HankenGrotesk,
        fontWeight = FontWeight(400),
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = HankenGrotesk,
        fontWeight = FontWeight(400),
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = HankenGrotesk,
        fontWeight = FontWeight(500),
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight(500),
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.14).sp,
    ),
    labelSmall = TextStyle(
        fontFamily = HankenGrotesk,
        fontWeight = FontWeight(600),
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.1.sp,
    ),
)
