package io.github.seijikohara.femto.ui.theme

import androidx.compose.ui.graphics.Color
import io.github.seijikohara.femto.data.AccentColor

/**
 * Seed color for each [AccentColor] preset, or null for [AccentColor.DYNAMIC]
 * (which keeps the Material You wallpaper-derived scheme). Each seed is a vivid
 * anchor; the full Material 3 tonal scheme is generated from it at theme time.
 *
 * This mapping is the single bridge between the Compose-free data enum and the
 * theme layer, so the swatch UI and [FemtoTheme] read the same colors.
 */
internal fun AccentColor.accentSeedColor(): Color? =
    when (this) {
        AccentColor.DYNAMIC -> null
        AccentColor.BLUE -> Color(0xFF2962FF)
        AccentColor.TEAL -> Color(0xFF00897B)
        AccentColor.GREEN -> Color(0xFF2E7D32)
        AccentColor.AMBER -> Color(0xFFFFB300)
        AccentColor.ORANGE -> Color(0xFFF4511E)
        AccentColor.RED -> Color(0xFFD32F2F)
        AccentColor.VIOLET -> Color(0xFF7E57C2)
        AccentColor.PINK -> Color(0xFFEC407A)
    }
