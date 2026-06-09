package io.github.seijikohara.femto.ui.home.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.seijikohara.femto.data.MapColorScheme

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
 * landcover / landuse / parks. Derived from the theme so they track light/dark and
 * the user's accent. The same three layers are recoloured in both backends.
 */
internal data class AccentMapColors(
    val background: String,
    val water: String,
    val land: String,
)

// The accent palette both backends paint onto the ACCENT scheme, mapped from the
// Material roles in one place: background = surface, water = primaryContainer,
// land = surfaceVariant. Derived from the theme, so it tracks light/dark + accent.
@Composable
internal fun accentMapColors(): AccentMapColors =
    AccentMapColors(
        background = MaterialTheme.colorScheme.surface.toCssHex(),
        water = MaterialTheme.colorScheme.primaryContainer.toCssHex(),
        land = MaterialTheme.colorScheme.surfaceVariant.toCssHex(),
    )
