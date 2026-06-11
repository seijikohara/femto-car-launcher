package io.github.seijikohara.femto.ui.theme

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * Render a Composable in both light and dark modes from a single
 * annotation. The single source of truth for screen-level previews —
 * never hand-write the light/dark `@Preview` pair per screen.
 *
 * Additional single-mode geometry previews (`@Preview(name = ...,
 * widthDp = ..., heightDp = ...)`) next to this annotation are
 * sanctioned: annotation classes cannot parameterise dimensions, and
 * those previews are responsive test cases per component, not a
 * light/dark duplication.
 */
@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
annotation class PreviewLightDark
