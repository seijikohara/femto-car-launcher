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
 * The page reports health over the `AndroidMapBridge` JS interface: [onReady] once
 * the style and first frame have loaded (`map.on("load")` — not `idle`, which never
 * fires while the heading-up camera keeps easing between GPS fixes), and [onFail]
 * when WebGL is unavailable or its GPU context is lost. [onFail]'s `fatal` flag is
 * true only for a lost context (a hard failure that must fall back even after a
 * successful render); the caller drops to SNAPSHOT before the map is ready (any
 * [onFail] or a ready timeout) and, once ready, only on a fatal [onFail] — so a
 * working map is never lost to transient noise such as a tile fetch error.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WebMapView(
    location: Location,
    mapConfig: MapConfig,
    onTap: () -> Unit,
    onReady: () -> Unit,
    onFail: (Boolean) -> Unit,
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
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onMapReady() = mainHandler.post { onReadyState.value() }

                        // fatal = a lost GPU context, which must fall back even after
                        // a successful render; other reasons only matter before ready.
                        @JavascriptInterface
                        fun onWebGlFailed(reason: String) =
                            mainHandler.post { onFailState.value(reason == "context-lost") }
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
                    Lifecycle.Event.ON_RESUME -> webView.onResume()
                    Lifecycle.Event.ON_PAUSE -> webView.onPause()
                    else -> Unit
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
