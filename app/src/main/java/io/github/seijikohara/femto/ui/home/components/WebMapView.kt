package io.github.seijikohara.femto.ui.home.components

import android.annotation.SuppressLint
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * delivered (the JS eases between sparse fixes).
 *
 * The page reports health over the `AndroidMapBridge` JS interface: [onReady] on
 * the first rendered frame after the style loads (a `render` once `isStyleLoaded()`
 * — not `load`, which can stall in a WebView, nor `idle`, which never fires while
 * the heading-up camera eases), and again after MapLibre auto-restores a lost GL
 * context. [onFail] fires with a short reason only for a PERSISTENT failure: WebGL
 * unavailable, or a `webglcontextlost` that does not restore within the page's
 * grace window (transient losses are left to MapLibre's built-in restore). The
 * caller does not silently fall back; it surfaces a live-map-unavailable message
 * offering a manual switch to the SNAPSHOT backend.
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
    onReady: () -> Unit,
    onFail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val onReadyState = rememberUpdatedState(onReady)
    val onFailState = rememberUpdatedState(onFail)
    val bearingHolder = remember { floatArrayOf(0f) }
    val isDark =
        when (mapConfig.style) {
            MapStyleSetting.AUTO -> isSystemInDarkTheme()
            MapStyleSetting.LIGHT -> false
            MapStyleSetting.DARK -> true
        }

    val webView =
        remember {
            val assetLoader =
                WebViewAssetLoader
                    .Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                    .build()
            val mainHandler = Handler(Looper.getMainLooper())
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // Keep rasterizing while attached but not yet visible (the Compose
                // AndroidView attach window) so the GL surface is not evicted —
                // surface eviction dropped the WebGL context a few seconds in on the
                // head unit. Costs some memory; fine for the single foreground map.
                settings.offscreenPreRaster = true
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onMapReady() = mainHandler.post { onReadyState.value() }

                        // Forward the failure reason verbatim; the caller surfaces it
                        // (debug) and offers a manual switch to the SNAPSHOT backend.
                        @JavascriptInterface
                        fun onWebGlFailed(reason: String) = mainHandler.post { onFailState.value(reason) }
                    },
                    "AndroidMapBridge",
                )
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

    LaunchedEffect(location.latitude, location.longitude, mapConfig.zoom, mapConfig.tiltDeg) {
        val bearing = location.carriedBearing(bearingHolder)
        webView.evaluateJavascript(
            "window.updateCamera && updateCamera(" +
                "${location.latitude}, ${location.longitude}, $bearing, ${mapConfig.zoom}, ${mapConfig.tiltDeg})",
            null,
        )
    }
    LaunchedEffect(isDark) {
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
