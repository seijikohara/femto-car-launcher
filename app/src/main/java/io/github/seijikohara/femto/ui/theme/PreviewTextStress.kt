package io.github.seijikohara.femto.ui.theme

import androidx.compose.ui.tooling.preview.Preview

/**
 * Text-fit stress previews: pseudolocale `en-XA` (accented, ~30 % longer Latin
 * text), `ar-XB` (right-to-left mirror), and the two accessibility font scales.
 * Apply alongside [PreviewLightDark] to the text-heavy components so truncation /
 * overflow / RTL regressions surface in the preview pane, not on a device. Kept
 * separate from [PreviewLightDark] so only the exposed components opt in rather
 * than every preview multiplying.
 */
@Preview(name = "en-XA long", locale = "en-XA", showBackground = true)
@Preview(name = "ar-XB RTL", locale = "ar-XB", showBackground = true)
@Preview(name = "fontScale 1.3", fontScale = 1.3f, showBackground = true)
@Preview(name = "fontScale 2.0", fontScale = 2.0f, showBackground = true)
annotation class PreviewTextStress
