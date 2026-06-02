package io.github.seijikohara.femto.ui.theme

import androidx.compose.ui.text.font.FontFamily

/**
 * Curated typography pairs. Each entry combines a Latin family with a
 * Japanese family that shares the same neutral, modern tone. Future entries
 * (e.g. Outfit + Zen Kaku Gothic New) plug in by adding an enum value
 * and a branch in [fontPairOf].
 */
enum class FontTheme {
    INTER,
}

internal data class FontPair(
    val latin: FontFamily,
    val jp: FontFamily,
)

internal fun fontPairOf(theme: FontTheme): FontPair =
    when (theme) {
        FontTheme.INTER -> FontPair(latin = FemtoFonts.Inter, jp = FemtoFonts.NotoSansJp)
    }
