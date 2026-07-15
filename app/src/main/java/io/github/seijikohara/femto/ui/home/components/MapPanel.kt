package io.github.seijikohara.femto.ui.home.components

import android.location.Location
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPinOff
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.location.isFresh
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.attributionCredit
import kotlinx.coroutines.delay

/**
 * Map tile surface + permission fallback. Renders via [WebMapView], a
 * hardware-accelerated WebView hosting whichever of the three backends
 * [MapConfig.backend] selects (see `MapBackend` for what each one means).
 * Clock and speed overlays are placed by the parent on top of this surface.
 */
@Composable
internal fun MapPanel(
    location: Location?,
    mapConfig: MapConfig,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    recenterNonce: Int = 0,
    // Validated-internet connectivity, forwarded to WebMapView so it can reload the
    // otherwise-blank offline map when connectivity returns.
    online: Boolean = true,
    onFollowChange: (Boolean) -> Unit = {},
    onBearingChange: (Float) -> Unit = {},
    // Extra bottom padding for the bottom-start attribution credit, so it clears a
    // bottom-hosted dock instead of sitting under its nav buttons. 0 when the dock
    // hosts another edge (see DashboardScaffold's attributionBottomInset).
    attributionBottomInset: Dp = 0.dp,
) = Surface(
    modifier = modifier,
    // Full-bleed: the map fills the dashboard to the screen edges, so it keeps
    // square corners rather than the rounded card shape the floating overlays use.
    shape = RectangleShape,
    color = MaterialTheme.colorScheme.surfaceContainer,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // A location fix is the only gate: with it we have permission and a
        // centre point; without it the map has nothing to show, so fall back.
        if (location != null) {
            WebMapView(
                location = location,
                mapConfig = mapConfig,
                onTap = onTap,
                modifier = Modifier.fillMaxSize(),
                recenterNonce = recenterNonce,
                online = online,
                onFollowChange = onFollowChange,
                onBearingChange = onBearingChange,
                attributionBottomInset = attributionBottomInset,
            )
        } else {
            // Centre the placeholder in the exposed map region — clear of the side
            // cards (left OR right, whichever the driver-side reserve occupies) and
            // above the bottom overlays (the same safe fractions the marker honours) —
            // instead of the full screen, so its text does not slide under the
            // floating cards and read as off-centre. Only one horizontal reserve is
            // ever non-zero; a left reserve pins the exposed region to the end side.
            Box(
                modifier =
                    Modifier
                        .align(if (mapConfig.leftSafeFraction > 0f) Alignment.TopEnd else Alignment.TopStart)
                        .fillMaxWidth(1f - mapConfig.rightSafeFraction - mapConfig.leftSafeFraction)
                        .fillMaxHeight(1f - mapConfig.bottomSafeFraction),
                contentAlignment = Alignment.Center,
            ) {
                Fallback()
            }
        }
    }
}

internal fun Location.carriedBearing(holder: FloatArray): Float =
    if (hasBearing() && bearing != 0f) {
        holder[0] = bearing
        bearing
    } else {
        holder[0]
    }

@Composable
internal fun Attribution(
    modifier: Modifier = Modifier,
    showTerrainCredit: Boolean = false,
) {
    // The OSM tile credit (OpenStreetMap / OpenMapTiles / OpenFreeMap). The host
    // renders this overlay only for the OSM backend, whose web page hides its own
    // attribution control (see showsNativeAttribution in WebMapView.kt); Mapbox and
    // Google Maps carry their own in-WebView attribution instead. Append the terrain
    // provider's required credit when that LIVE layer is active (its licence mandates
    // attribution).
    val base = stringResource(R.string.map_attribution)
    val terrain = stringResource(R.string.map_attribution_terrain)
    val text = base + (if (showTerrainCredit) " · $terrain" else "")
    Text(
        text = text,
        // See Typography.attributionCredit for the sub-floor rationale; this
        // Text additionally carries full-strength onSurfaceVariant over a
        // faint scrim below for contrast at that size.
        style = MaterialTheme.typography.attributionCredit(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            modifier
                .padding(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

/**
 * Re-evaluate [location] freshness on a 1 s tick so the marker can grey out when
 * fixes stop arriving (a tunnel): the location flow forwards each fix verbatim
 * and never emits null on signal loss, so a stale fix would otherwise read as
 * live forever. A new fix restarts the tick (the key changes) and re-greens the
 * marker. Once stale, the loop stops — nothing changes until the next fix.
 */
@Composable
internal fun rememberLocationFresh(location: Location): Boolean =
    produceState(initialValue = true, location) {
        while (true) {
            value = location.isFresh(SystemClock.elapsedRealtimeNanos())
            if (!value) break
            delay(1_000L)
        }
    }.value

@Composable
internal fun Fallback(modifier: Modifier = Modifier) =
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FemtoIcon(
            imageVector = Lucide.MapPinOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = stringResource(R.string.map_unavailable),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = stringResource(R.string.map_permission_cta),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
