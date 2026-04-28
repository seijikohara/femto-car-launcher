package io.github.seijikohara.femto.ui.theme

import androidx.compose.ui.text.font.FontFamily

/**
 * Curated typography pairs. Each entry combines a Latin family with a
 * Japanese family that shares the same geometric tone. Future entries
 * (e.g. Outfit + Zen Kaku Gothic New) plug in by adding an enum value
 * and a branch in [fontPairOf].
 */
enum class FontTheme {
    GEIST,
}

internal data class FontPair(
    val latin: FontFamily,
    val jp: FontFamily,
)

internal fun fontPairOf(theme: FontTheme): FontPair = when (theme) {
    FontTheme.GEIST -> FontPair(latin = FemtoFonts.Geist, jp = FemtoFonts.MPlus2)
}
