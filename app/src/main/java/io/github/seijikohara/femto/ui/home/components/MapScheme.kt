package io.github.seijikohara.femto.ui.home.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.seijikohara.femto.data.display.MapColorScheme

// "#rrggbb" for a map style / CSS, dropping the alpha (style colours are opaque).
internal fun Color.toCssHex(): String = "#%06X".format(0xFFFFFF and toArgb())

// Hosted OpenFreeMap style base URL and the two bundled base styles. The bundled
// styles are the accent base (recoloured) and DARK_MATTER; everything else is a
// hosted OpenFreeMap style.
internal const val OFM_STYLE_BASE = "https://tiles.openfreemap.org/styles/"
internal const val POSITRON_STYLE_URL = OFM_STYLE_BASE + "positron"
internal const val LIGHT_STYLE_ASSET = "map/light.json"
internal const val DARK_STYLE_ASSET = "map/dark.json"

/**
 * Where a [MapColorScheme] gets its style from, resolved for the active light/dark
 * context. Both backends share this so the scheme list stays a single source of
 * truth; each backend turns the ref into its own style reference (snapshot loads the
 * asset / URL directly, live serves the asset over appassets).
 */
internal sealed interface MapStyleRef {
    data class Hosted(
        val url: String,
    ) : MapStyleRef

    data class Bundled(
        val asset: String,
    ) : MapStyleRef

    // The bundled [baseAsset] recoloured with the Material accent (the ACCENT scheme).
    data class Accent(
        val baseAsset: String,
    ) : MapStyleRef
}

internal fun mapStyleRefFor(
    scheme: MapColorScheme,
    isDark: Boolean,
): MapStyleRef =
    when (scheme) {
        MapColorScheme.ACCENT -> MapStyleRef.Accent(if (isDark) DARK_STYLE_ASSET else LIGHT_STYLE_ASSET)
        MapColorScheme.POSITRON -> MapStyleRef.Hosted(POSITRON_STYLE_URL)
        MapColorScheme.BRIGHT -> MapStyleRef.Hosted(OFM_STYLE_BASE + "bright")
        MapColorScheme.LIBERTY -> MapStyleRef.Hosted(OFM_STYLE_BASE + "liberty")
        MapColorScheme.DARK -> MapStyleRef.Hosted(OFM_STYLE_BASE + "dark")
        MapColorScheme.FIORD -> MapStyleRef.Hosted(OFM_STYLE_BASE + "fiord")
        MapColorScheme.DARK_MATTER -> MapStyleRef.Bundled(DARK_STYLE_ASSET)
    }

/**
 * The Material colours the ACCENT scheme paints onto the map, as "#rrggbb" hex.
 * [background] tints the map background, [water] the water bodies, [land] the
 * landcover / landuse / parks, [roadMajor] / [roadMinor] / [roadCasing] the
 * transportation lines, and [building] the 2D building fills. Derived from the
 * theme so they track light/dark and the user's accent. The same layer groups are
 * recoloured in both backends.
 *
 * [building] doubles as the 3D fill-extrusion colour for EVERY scheme (not just
 * ACCENT): the LIVE backend's injected buildings are theme-tracked so they stay
 * subdued on any base style (see the features push in WebMapView.kt).
 */
internal data class AccentMapColors(
    val background: String,
    val water: String,
    val land: String,
    val roadMajor: String,
    val roadMinor: String,
    val roadCasing: String,
    val building: String,
    // Label text colour ([label]); the halo reuses [background], so labels stay
    // separated from whatever they overlap. The bundled bases' label colours
    // are tuned to their own backgrounds and go illegible on the Material
    // surface (verified on TBox-Mock: dark-base grey-101 text on the near-black
    // dark surface), so ACCENT recolours them like every other layer group.
    val label: String,
)

// The accent palette both backends paint onto the ACCENT scheme, mapped from the
// Material roles in one place. Both modes follow the same readability rule —
// roads float above the ground — but reach it from opposite directions, so the
// role mapping is mode-dependent:
//  - Light: near-white roads on a slightly darker ground (the classic light
//    car-nav look), with a visible casing edge; background = surfaceContainer
//    also matches the map card surface (MapPanel), so the pre-first-frame card
//    and the map read as one plane.
//  - Dark: the ground drops to the darkest surface tone and roads brighten
//    above it (ground < buildings < minor < major), so the road network — not
//    the buildings — is the brightest shape on the panel. The road roles sit
//    deliberately high (outline / outlineVariant): the surfaceContainer* tones
//    used before measured ~2:1 against the surface ground on the head-unit
//    panel and the network disappeared (user report, 2026-06-12).
@Composable
internal fun accentMapColors(isDark: Boolean): AccentMapColors =
    if (isDark) {
        AccentMapColors(
            background = MaterialTheme.colorScheme.surface.toCssHex(),
            water = MaterialTheme.colorScheme.primaryContainer.toCssHex(),
            land = MaterialTheme.colorScheme.surfaceContainerLow.toCssHex(),
            roadMajor = MaterialTheme.colorScheme.onSurfaceVariant.toCssHex(),
            roadMinor = MaterialTheme.colorScheme.outline.toCssHex(),
            roadCasing = MaterialTheme.colorScheme.surfaceContainer.toCssHex(),
            building = MaterialTheme.colorScheme.surfaceContainerHigh.toCssHex(),
            label = MaterialTheme.colorScheme.onSurface.toCssHex(),
        )
    } else {
        AccentMapColors(
            background = MaterialTheme.colorScheme.surfaceContainer.toCssHex(),
            water = MaterialTheme.colorScheme.primaryContainer.toCssHex(),
            land = MaterialTheme.colorScheme.surfaceContainerHigh.toCssHex(),
            roadMajor = MaterialTheme.colorScheme.surface.toCssHex(),
            roadMinor = MaterialTheme.colorScheme.surfaceContainerHighest.toCssHex(),
            roadCasing = MaterialTheme.colorScheme.outline.toCssHex(),
            building = MaterialTheme.colorScheme.surfaceContainerHighest.toCssHex(),
            label = MaterialTheme.colorScheme.onSurface.toCssHex(),
        )
    }
