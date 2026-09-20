package io.github.seijikohara.femto.testfixtures

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import io.github.seijikohara.femto.ui.home.HomeUiState
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The dashboard render inputs shared by the goldens (`DashboardScreenshotTest`)
 * and the screenshot catalog (`DashboardCatalogTest`), so both draw the same
 * state over the same still map.
 */
internal object DashboardFixtures {
    /** Fixed so the dashboard clock is deterministic across CI record/verify runs. */
    val fixedClock: Clock = Clock.fixed(Instant.parse("2026-05-01T10:08:00Z"), ZoneOffset.UTC)

    val state: HomeUiState = fakeHomeUiState()

    // Decoded once per JVM. The captures are smaller than the widest geometry
    // (2000x1200), so Crop upscales there — acceptable because the backdrop is
    // scenery behind the UI, not a subject under test, and the renders exist to
    // show what the app draws over it. Enlarging the asset would add megabytes
    // to every golden re-record for pixels nothing asserts on.
    val backdrop: ImageBitmap = backdrop("/map-backdrop-osm.png")

    val backdropDark: ImageBitmap = backdrop("/map-backdrop-osm-dark.png")

    private fun backdrop(resource: String): ImageBitmap =
        checkNotNull(DashboardFixtures::class.java.getResourceAsStream(resource)) {
            "$resource missing from test resources"
        }.use { stream ->
            // decodeStream returns null on a corrupt or unreadable file; without
            // this the failure surfaces as an NPE inside asImageBitmap with no
            // clue which resource was at fault.
            checkNotNull(BitmapFactory.decodeStream(stream)) { "$resource could not be decoded" }
        }.asImageBitmap()
}

/**
 * Still OSM capture standing in for the live map.
 *
 * Robolectric's WebView is a shadow with no Chromium behind it, so the real
 * [io.github.seijikohara.femto.ui.home.components.WebMapView] can only ever
 * paint an empty region here — and a render that fetched live tiles would stop
 * being deterministic. This keeps every pixel the app itself draws (cards,
 * dock, overlays, marker, controls) generated from the current code, and pins
 * only the map imagery, which changes just when the map style does.
 *
 * The source is a device capture of this app rendering OpenFreeMap tiles
 * (OpenStreetMap data, ODbL) — see app/src/test/resources/README.md.
 */
@Composable
internal fun MapBackdrop(darkTheme: Boolean) {
    Image(
        // The app swaps the map style with the theme (Positron / Dark Matter),
        // so the still has to swap too — a light map under dark glass chrome
        // would misrepresent the product, not just look odd.
        bitmap = if (darkTheme) DashboardFixtures.backdropDark else DashboardFixtures.backdrop,
        contentDescription = null,
        // Crop, not Fit: the renders span landscape and portrait geometries, and
        // a letterboxed backdrop would show bars the running app never has.
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
}
