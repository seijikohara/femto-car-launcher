package io.github.seijikohara.femto.ui.theme

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * Render a Composable in both light and dark modes from a single
 * annotation. The single source of truth for screen-level previews —
 * never hand-write the two `@Preview` blocks per screen.
 */
@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
annotation class PreviewLightDark
