package io.github.seijikohara.femto.ui.home.components

import android.annotation.SuppressLint
import android.location.Location
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
 * keeps the chosen backend regardless. The WebView renders WebGL on the GPU
 * (hardware-accelerated); a device that cannot keep a WebGL context uses the
 * SNAPSHOT backend instead.
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

    // Flips true once the page's script has run (onPageFinished) so the JS bridge
    // functions exist. The state-pushing effects below gate + key on it, so the
    // current camera / style / feature state is (re)applied as soon as the page is
    // ready — closing the race where an effect fires before the script registers
    // window.updateCamera / setStyleUrl / setFeatures and is silently dropped.
    val pageReady = remember { mutableStateOf(false) }

    val webView =
        remember {
            val assetLoader =
                WebViewAssetLoader
                    .Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                    .build()
            WebView(context).apply {
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

                        // The inline page script runs synchronously during parse, so by
                        // onPageFinished the bridge functions are registered.
                        override fun onPageFinished(
                            view: WebView,
                            url: String,
                        ) {
                            pageReady.value = true
                        }
                    }
                loadUrl("https://appassets.androidplatform.net/assets/web/map.html")
            }
        }

    // Material primary as the self-location marker fill, so the WebGL puck matches
    // the SNAPSHOT marker and the user's accent.
    val markerColor = MaterialTheme.colorScheme.primary.toCssHex()

    // Resolve the colour scheme for the active light/dark context. ACCENT recolours
    // the bundled base with these Material colours (in map.html's transformStyle);
    // the others are plain hosted / bundled styles.
    val styleRef = mapStyleRefFor(if (isDark) mapConfig.schemeDark else mapConfig.schemeLight, isDark)
    val accentColors = accentMapColors()

    // Each effect keys on [pageReady] (so it fires once the page is ready), then
    // pushes the current state to the page.
    LaunchedEffect(
        pageReady.value,
        location.latitude,
        location.longitude,
        mapConfig.zoom,
        mapConfig.tiltDeg,
        mapConfig.markerPos,
        markerColor,
    ) {
        if (!pageReady.value) return@LaunchedEffect
        val bearing = location.carriedBearing(bearingHolder)
        webView.evaluateJavascript(
            "window.updateCamera && updateCamera(" +
                "${location.latitude}, ${location.longitude}, $bearing, ${mapConfig.zoom}, ${mapConfig.tiltDeg}, " +
                "${mapConfig.markerPos}, '$markerColor')",
            null,
        )
    }
    LaunchedEffect(pageReady.value, styleRef, accentColors) {
        if (!pageReady.value) return@LaunchedEffect
        // Resolve the scheme to a URL the WebView can load (hosted, or the bundled
        // base served over appassets) plus the accent palette (empty = no recolor).
        val url =
            when (styleRef) {
                is MapStyleRef.Hosted -> styleRef.url
                is MapStyleRef.Bundled -> appAssetsUrl(styleRef.asset)
                is MapStyleRef.Accent -> appAssetsUrl(styleRef.baseAsset)
            }
        val accent = (styleRef as? MapStyleRef.Accent)?.let { accentColors }
        webView.evaluateJavascript(
            "window.setStyleUrl && setStyleUrl('$url', " +
                "'${accent?.background ?: ""}', '${accent?.water ?: ""}', '${accent?.land ?: ""}')",
            null,
        )
    }
    // LIVE-only feature toggles (3D buildings / terrain). The page merges them into
    // the style via MapLibre transformStyle, so this re-applies the style.
    LaunchedEffect(pageReady.value, mapConfig.buildings3d, mapConfig.terrain) {
        if (!pageReady.value) return@LaunchedEffect
        webView.evaluateJavascript(
            "window.setFeatures && setFeatures(${mapConfig.buildings3d}, ${mapConfig.terrain})",
            null,
        )
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

                    else -> {}
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
        Attribution(
            modifier = Modifier.align(Alignment.BottomStart),
            showTerrainCredit = mapConfig.terrain,
        )
    }
}

// A bundled asset served to the WebView over the WebViewAssetLoader https origin so
// MapLibre's tile Worker can fetch it (and the asset's OpenFreeMap sources) cross-origin.
private fun appAssetsUrl(asset: String): String = "https://appassets.androidplatform.net/assets/$asset"
