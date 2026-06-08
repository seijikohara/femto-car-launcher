package io.github.seijikohara.femto.ui.home.components

import android.annotation.SuppressLint
import android.location.Location
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import io.github.seijikohara.femto.data.MapStyleSetting

/**
 * Live map backend: MapLibre GL JS (WebGL) in a WebView — the only path that renders
 * a smooth, animated map *inside Compose*. The native live `MapView` is grey in a
 * Compose `AndroidView` on the head unit (its GL surface is not composited), and the
 * SNAPSHOT backend is a still bitmap; a WebView composites inline through HWUI and
 * MapLibre GL JS animates the camera. The page ([assets/web/map.html]) is served via
 * [WebViewAssetLoader] from the real https origin appassets.androidplatform.net so
 * MapLibre's tile-processing Web Worker can fetch tiles. GPS fixes drive
 * `map.easeTo()` for heading-up smooth follow — this is how #2 smooth movement is
 * delivered (the JS eases between sparse fixes). A `maplibregl.Marker` chevron rides
 * the same fixes as the self-location puck (filled with the Material primary), laid
 * on the tilted ground so it matches the SNAPSHOT backend's marker.
 *
 * There is NO auto-fallback: the page relies on MapLibre's built-in WebGL
 * context-loss restore (`webglcontextlost`/`webglcontextrestored`) and the host
 * keeps the chosen backend regardless. [softwareRendering] forces the WebView onto
 * the software layer (`setLayerType(LAYER_TYPE_SOFTWARE)`) so Chromium renders
 * WebGL via SwiftShader — the LIVE_SOFTWARE backend for GPUs that cannot keep a
 * hardware WebGL context. Switching the flag rebuilds the WebView (it keys the
 * `remember`).
 *
 * [ON_START][androidx.lifecycle.Lifecycle.Event.ON_START] resumes the WebView and
 * nudges the map to re-measure/repaint; the WebView is paused only on ON_STOP (a
 * visible WebView paused on ON_PAUSE can drop its GL context).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WebMapView(
    location: Location,
    mapConfig: MapConfig,
    onTap: () -> Unit,
    softwareRendering: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bearingHolder = remember { floatArrayOf(0f) }
    val isDark =
        when (mapConfig.style) {
            MapStyleSetting.AUTO -> isSystemInDarkTheme()
            MapStyleSetting.LIGHT -> false
            MapStyleSetting.DARK -> true
        }

    val webView =
        remember(softwareRendering) {
            val assetLoader =
                WebViewAssetLoader
                    .Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                    .build()
            WebView(context).apply {
                // Hardware layer renders WebGL on the GPU; the software layer routes
                // Chromium through SwiftShader (the LIVE_SOFTWARE backend) for devices
                // whose GPU cannot keep a live WebGL context.
                setLayerType(if (softwareRendering) View.LAYER_TYPE_SOFTWARE else View.LAYER_TYPE_HARDWARE, null)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // Keep rasterizing while attached but not yet visible (the Compose
                // AndroidView attach window) so the GL surface is not evicted —
                // surface eviction dropped the WebGL context a few seconds in on the
                // head unit. Costs some memory; fine for the single foreground map.
                settings.offscreenPreRaster = true
                webViewClient =
                    object : WebViewClientCompat() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
                    }
                loadUrl("https://appassets.androidplatform.net/assets/web/map.html")
            }
        }

    // Material primary as the self-location marker fill, so the WebGL puck matches
    // the SNAPSHOT marker and the user's accent. Passed on every camera update so it
    // self-heals if the first push raced the page load (the next GPS fix re-applies).
    val markerColor = MaterialTheme.colorScheme.primary.toCssHex()

    // Key on [webView] too: a render-mode switch rebuilds the WebView, and the new
    // instance must receive the current camera and style immediately rather than
    // waiting for the next GPS fix / theme change (it would otherwise show the
    // default [0,0] world view in the meantime).
    LaunchedEffect(webView, location.latitude, location.longitude, mapConfig.zoom, mapConfig.tiltDeg, markerColor) {
        val bearing = location.carriedBearing(bearingHolder)
        webView.evaluateJavascript(
            "window.updateCamera && updateCamera(" +
                "${location.latitude}, ${location.longitude}, $bearing, ${mapConfig.zoom}, ${mapConfig.tiltDeg}, " +
                "'$markerColor')",
            null,
        )
    }
    LaunchedEffect(webView, isDark) {
        val style = if (isDark) DARK_STYLE_URL else POSITRON_STYLE_URL
        webView.evaluateJavascript("window.setStyleUrl && setStyleUrl('$style')", null)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, webView) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    // Pause only when truly backgrounded (ON_STOP), not ON_PAUSE: the
                    // launcher map stays visible behind transient lifecycle dips, and
                    // pausing a visible WebView can drop its GL context (the "few
                    // seconds then grey" cause). Resume on ON_START and nudge the map
                    // to re-measure / repaint after a possible surface loss.
                    Lifecycle.Event.ON_START -> {
                        webView.onResume()
                        webView.evaluateJavascript("window.onHostResume && onHostResume()", null)
                    }

                    Lifecycle.Event.ON_STOP -> {
                        webView.onPause()
                    }

                    else -> {
                        Unit
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView.destroy()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize().clickable { onTap() },
            factory = { webView },
        )
        Attribution(modifier = Modifier.align(Alignment.BottomStart))
    }
}

// Dark map served via WebViewAssetLoader from the bundled dark style (shares the
// OpenFreeMap sources); light uses the hosted positron style ([POSITRON_STYLE_URL]).
private const val DARK_STYLE_URL = "https://appassets.androidplatform.net/assets/map/dark.json"

// "#rrggbb" for CSS, dropping the alpha (the marker SVG fill is opaque).
private fun Color.toCssHex(): String = "#%06X".format(0xFFFFFF and toArgb())
