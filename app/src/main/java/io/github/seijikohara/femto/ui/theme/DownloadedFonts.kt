@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package io.github.seijikohara.femto.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.seijikohara.femto.data.fonts.CachedFont

// The full wght axis (Thin..Black), requested from every variable font so any
// weight a caller asks for resolves to a real axis instance rather than a
// synthesised (faux-bold) approximation. The Femto type scale itself renders at
// only three of these tiers (ExtraLight / Normal / SemiBold), but Material
// components may request others, so the whole axis stays provisioned.
private val WeightAxis =
    listOf(
        FontWeight.Thin,
        FontWeight.ExtraLight,
        FontWeight.Light,
        FontWeight.Normal,
        FontWeight.Medium,
        FontWeight.SemiBold,
        FontWeight.Bold,
        FontWeight.ExtraBold,
        FontWeight.Black,
    )

/**
 * Build the combined [FontFamily] from the user's downloaded faces. The Latin
 * fonts come first so they win for shared glyphs; the CJK fonts follow as the
 * multibyte fallback. When both slots are system (null), the platform default
 * is returned so nothing downloads.
 */
internal fun buildFontFamily(
    latin: CachedFont?,
    cjk: CachedFont?,
): FontFamily {
    val fonts = latin.toFonts() + cjk.toFonts()
    return if (fonts.isEmpty()) FontFamily.Default else FontFamily(fonts)
}

private fun CachedFont?.toFonts(): List<Font> =
    when (this) {
        null -> {
            emptyList()
        }

        is CachedFont.Variable -> {
            WeightAxis.map { weight ->
                Font(
                    file = file,
                    weight = weight,
                    style = FontStyle.Normal,
                    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
                )
            }
        }

        is CachedFont.Static -> {
            fileByWeight.map { (weight, file) -> Font(file = file, weight = FontWeight(weight)) }
        }
    }
