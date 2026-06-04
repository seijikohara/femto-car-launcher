package io.github.seijikohara.femto.ui.home.components

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshot
import org.maplibre.android.snapshotter.MapSnapshotter
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Confirms the dashboard map renders real OpenStreetMap tiles (not a blank
 * surface) using MapLibre's off-screen [MapSnapshotter].
 *
 * Why a snapshotter rather than a screenshot of the live [MapPanel]: on the
 * Android emulator's GLES translator (software SwiftShader and host-GPU alike)
 * MapLibre fetches and rasterises tiles correctly but never *presents* the live
 * TextureView frame to a steady on-screen surface, so `adb screencap` and
 * `TextureView.getBitmap()` capture only the grey card background even while the
 * map renders. [MapSnapshotter] renders into its own framebuffer and reads the
 * pixels back, so it reflects the true render on the emulator and on real head
 * units. This makes "does OSM render?" answerable on an emulator, where a live
 * screenshot cannot.
 *
 * Run on a connected device/emulator: `./gradlew connectedAndroidTest`. A green
 * run is the confirmation that OSM renders; the test skips itself when offline
 * (the OpenFreeMap style and tiles need network). For a visual artefact it also
 * writes the rendered PNG to the app's external files dir
 * (`files/map_render_verification.png`); `connectedAndroidTest` uninstalls the
 * app on cleanup, so pull it from an IDE run or a manual `am instrument` run that
 * leaves the app installed.
 */
class MapSnapshotRenderTest {
    @Test
    fun renders_openstreetmap_tiles_offscreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue("Skipped: OpenFreeMap tiles require network", context.hasInternet())

        val rendered = AtomicReference<Bitmap?>()
        val failure = AtomicReference<String?>()
        val snapshotter = AtomicReference<MapSnapshotter?>()
        val done = CountDownLatch(1)

        // MapSnapshotter posts its callbacks to the thread that starts it, so
        // start it on the main looper and await the result on the test thread.
        // The strong reference in [snapshotter] keeps it alive until the callback.
        instrumentation.runOnMainSync {
            MapLibre.getInstance(context)
            val options =
                MapSnapshotter
                    .Options(SNAPSHOT_WIDTH, SNAPSHOT_HEIGHT)
                    .withStyleBuilder(Style.Builder().fromUri(POSITRON_STYLE_URL))
                    .withCameraPosition(
                        CameraPosition
                            .Builder()
                            .target(LatLng(LONDON_LAT, LONDON_LON))
                            .zoom(MAP_ZOOM)
                            .build(),
                    )
            snapshotter.set(
                MapSnapshotter(context, options).apply {
                    start(
                        { snapshot: MapSnapshot ->
                            rendered.set(snapshot.bitmap)
                            done.countDown()
                        },
                        { error: String ->
                            failure.set(error)
                            done.countDown()
                        },
                    )
                },
            )
        }

        assertTrue(done.await(SNAPSHOT_TIMEOUT_SECONDS, TimeUnit.SECONDS), "Snapshot timed out")
        assertNull(failure.get(), "Snapshot failed: ${failure.get()}")
        val bitmap = rendered.get() ?: error("Snapshot produced no bitmap")
        writeForInspection(context, bitmap)

        // A blank/uniform surface is ~1 colour; real OSM tiles (roads, water,
        // labels) yield hundreds (~2000 observed). The threshold sits far above
        // blank and far below that, so minor style changes never flake it.
        val distinctColors = bitmap.distinctColorCount()
        assertTrue(
            distinctColors > MIN_DISTINCT_COLORS,
            "Expected rendered OSM tiles but the map is effectively blank ($distinctColors distinct colours)",
        )
    }

    private fun Context.hasInternet(): Boolean {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun Bitmap.distinctColorCount(): Int {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels.toHashSet().size
    }

    // Best-effort visual artefact; never fail the render assertion over file I/O.
    private fun writeForInspection(
        context: Context,
        bitmap: Bitmap,
    ) = runCatching {
        File(context.getExternalFilesDir(null), VERIFICATION_FILE_NAME)
            .outputStream()
            .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val SNAPSHOT_WIDTH = 700
        const val SNAPSHOT_HEIGHT = 560
        const val LONDON_LAT = 51.5074
        const val LONDON_LON = -0.1278
        const val SNAPSHOT_TIMEOUT_SECONDS = 45L
        const val MIN_DISTINCT_COLORS = 50
        const val VERIFICATION_FILE_NAME = "map_render_verification.png"
    }
}
