@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package io.github.seijikohara.femto.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.seijikohara.femto.data.fonts.CachedFont

// The weights the M3 type scale renders at, requested from every variable font
// so the wght axis is exercised across the whole scale rather than synthesised.
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
