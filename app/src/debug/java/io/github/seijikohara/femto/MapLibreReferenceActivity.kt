package io.github.seijikohara.femto

import android.os.Bundle
import androidx.activity.ComponentActivity
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView

/**
 * Debug-only minimal MapLibre reference activity.
 *
 * A stock [MapView] with default options — no `textureMode` override, no
 * SurfaceView z-order / pixel-format tweaks, no Compose / AndroidView — loading
 * the same OpenFreeMap style the dashboard uses. It isolates whether a vanilla
 * MapLibre live map presents on a given device or emulator, versus the
 * launcher's customised LiveMap (SurfaceView + opaque holder). If this renders
 * but LiveMap does not, the launcher's GL-surface customisation is the fault; if
 * both stay grey, the live GL path does not present on that hardware.
 *
 * Launch:
 *   adb shell am start -n io.github.seijikohara.femto/.MapLibreReferenceActivity
 *
 * Not registered in the release manifest (`src/debug/AndroidManifest.xml` only).
 */
class MapLibreReferenceActivity : ComponentActivity() {
    private lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        mapView = MapView(this)
        mapView.onCreate(savedInstanceState)
        setContentView(mapView)
        mapView.getMapAsync { map ->
            map.cameraPosition =
                CameraPosition
                    .Builder()
                    .target(LatLng(LONDON_LAT, LONDON_LON))
                    .zoom(MAP_ZOOM)
                    .build()
            map.setStyle(POSITRON_STYLE_URL)
        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        mapView.onDestroy()
        super.onDestroy()
    }

    private companion object {
        const val LONDON_LAT = 51.5074
        const val LONDON_LON = -0.1278
        const val MAP_ZOOM = 12.0
        const val POSITRON_STYLE_URL = "https://tiles.openfreemap.org/styles/positron"
    }
}
