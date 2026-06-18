package io.github.seijikohara.femto.ui.home.components

import android.annotation.SuppressLint
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPinOff
import io.github.seijikohara.femto.BuildConfig
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.display.MapStyleSetting
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.LocalFemtoDarkTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import kotlinx.coroutines.delay

/**
 * Live map backend: MapLibre GL JS (WebGL) in a WebView — the only path that renders
 * a smooth, animated map *inside Compose*. The native live `MapView` is grey in a
 * Compose `AndroidView` on the head unit (its GL surface is not composited), and the
 * SNAPSHOT backend is a still bitmap; a WebView composites inline through HWUI and
 * MapLibre GL JS animates the camera. The page (TypeScript under `webmap/`, built by
 * Gradle into `assets/web/map.html` — see the node block in app/build.gradle.kts) is
 * served via [WebViewAssetLoader] from the real https origin
 * appassets.androidplatform.net so
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
 * The page reports into the host over a one-method [JavascriptInterface] bridge
 * (`window.femtoBridge.onMapEvent(kind, detail)`). Four kinds exist: `error`
 * for transient resource failures (tile / style / DEM fetch — logged, never UI,
 * because the removed auto-downgrade misfired on exactly such ambiguous signals),
 * `fatal` for definitive never-going-to-render facts (no WebGL context, map
 * construction threw), `follow` for camera-follow state flips, and `bearing`
 * (throttled) for the compass overlay. A `fatal` swaps the permanently-blank
 * WebView for a static notice pointing at the Settings render-mode switch — same
 * posture as renderer-death containment below: inform, never switch the
 * persisted backend.
 *
 * Renderer-death containment is the one exception to "do nothing": without an
 * [android.webkit.WebViewClient.onRenderProcessGone] override the platform kills
 * the whole launcher process when the WebView renderer dies (WebGL is a classic
 * OOM victim on weak head-unit GPUs). Death is a fact reported by the system, not
 * a heuristic like the removed context-loss / readiness signals, so reacting to
 * it cannot misfire on a healthy map. The reaction stays inside the LIVE backend:
 * the first death rebuilds the WebView in place; repeated deaths within
 * [RENDERER_DEATH_WINDOW_MS] stop the rebuild loop and show a static notice that
 * points at the Settings render-mode switch. The persisted render mode is never
 * rewritten.
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
    recenterNonce: Int = 0,
    onFollowChange: (Boolean) -> Unit = {},
    onBearingChange: (Float) -> Unit = {},
) {
    val context = LocalContext.current
    // The bridge object below is registered once per WebView instance and would
    // otherwise capture the first composition's lambdas forever.
    val currentOnFollowChange by rememberUpdatedState(onFollowChange)
    val currentOnBearingChange by rememberUpdatedState(onBearingChange)
    val bearingHolder = remember { floatArrayOf(0f) }
    val isDark =
        when (mapConfig.style) {
            MapStyleSetting.AUTO -> LocalFemtoDarkTheme.current
            MapStyleSetting.LIGHT -> false
            MapStyleSetting.DARK -> true
        }

    // Flips true once the page's script has run (onPageFinished) so the JS bridge
    // functions exist. The state-pushing effects below gate + key on it, so the
    // current camera / style / feature state is (re)applied as soon as the page is
    // ready — closing the race where an effect fires before the script registers
    // window.updateCamera / setStyleUrl / setFeatures and is silently dropped.
    val pageReady = remember { mutableStateOf(false) }

    // Renderer-death containment state (see the KDoc): bumping the generation
    // rebuilds the WebView after the renderer process dies; once deaths repeat
    // inside the window the panel gives up and shows a static notice instead of
    // crash-looping. Views whose renderer died are destroyed in the callback, so
    // the disposal path and lifecycle observer must skip them.
    var rendererGeneration by remember { mutableIntStateOf(0) }
    var rendererGaveUp by remember { mutableStateOf(false) }
    var lastRendererDeath by remember { mutableStateOf<String?>(null) }
    val rendererDeathsMs = remember { mutableListOf<Long>() }
    val crashedViews = remember { mutableSetOf<WebView>() }

    // Set by a `fatal` bridge event (see the KDoc): the page itself determined it
    // can never render, so a blank "working" map would be a lie. Like the
    // renderer-death notice, this only informs — the persisted mode is untouched.
    var liveInitFailed by remember { mutableStateOf(false) }
    var lastFatalDetail by remember { mutableStateOf<String?>(null) }
    // Bridge callbacks arrive on a WebView-managed background thread; Compose
    // state writes must land on the main thread.
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    if (rendererGaveUp || liveInitFailed) {
        Box(modifier = modifier) {
            LiveMapNotice(
                titleRes = if (rendererGaveUp) R.string.map_live_renderer_gone else R.string.map_live_init_failed,
                hintRes =
                    if (rendererGaveUp) {
                        R.string.map_live_renderer_gone_hint
                    } else {
                        R.string.map_live_init_failed_hint
                    },
                // Why it failed is debugging detail, not driver-facing content.
                reason = (if (rendererGaveUp) lastRendererDeath else lastFatalDetail).takeIf { BuildConfig.DEBUG },
                modifier = Modifier.align(Alignment.Center),
            )
        }
        return
    }

    val webView =
        remember(rendererGeneration) {
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

                        // The page script is a module (deferred), but module scripts
                        // execute before the load event, so by onPageFinished the
                        // bridge functions are registered.
                        override fun onPageFinished(
                            view: WebView,
                            url: String,
                        ) {
                            pageReady.value = true
                        }

                        // Returning true claims the renderer death; the default kills
                        // the whole launcher process. The dead view must be detached
                        // and destroyed here — any other call on it can crash.
                        override fun onRenderProcessGone(
                            view: WebView,
                            detail: RenderProcessGoneDetail,
                        ): Boolean {
                            val description =
                                if (detail.didCrash()) "renderer crashed" else "renderer killed by the system"
                            Log.e(TAG, "WebView $description; containing")
                            crashedViews += view
                            (view.parent as? ViewGroup)?.removeView(view)
                            view.destroy()
                            pageReady.value = false
                            lastRendererDeath = description
                            val now = SystemClock.elapsedRealtime()
                            rendererDeathsMs.removeAll { now - it > RENDERER_DEATH_WINDOW_MS }
                            rendererDeathsMs += now
                            if (rendererDeathsMs.size >= MAX_RENDERER_DEATHS) {
                                rendererGaveUp = true
                            } else {
                                rendererGeneration++
                            }
                            return true
                        }
                    }
                // JS -> Kotlin error channel; registered per WebView instance so a
                // post-crash rebuild (rendererGeneration bump) re-registers it on
                // the fresh view. Kept minimal on purpose: one method, primitive
                // params, and the only page that can call it is our bundled asset
                // served through the WebViewAssetLoader above.
                addJavascriptInterface(
                    object {
                        // Block body: a @JavascriptInterface method must not leak
                        // a non-primitive return type to the JS side.
                        @JavascriptInterface
                        fun onMapEvent(
                            kind: String,
                            detail: String,
                        ) {
                            when (kind) {
                                // Transient by definition (tile / style / DEM
                                // fetch): log only. The removed auto-downgrade
                                // misfired on exactly such ambiguous signals —
                                // never UI here.
                                "error" -> {
                                    Log.w(TAG, "LIVE map transient error: $detail")
                                }

                                "fatal" -> {
                                    Log.e(TAG, "LIVE map fatal: $detail")
                                    // Bridge calls arrive on a background thread.
                                    mainHandler.post {
                                        lastFatalDetail = detail
                                        liveInitFailed = true
                                    }
                                }

                                // Camera-follow state flips (a user drag detached
                                // it, the auto-refollow re-attached it) so the
                                // host's locate button can reflect the mode.
                                "follow" -> {
                                    mainHandler.post { currentOnFollowChange(detail.toBoolean()) }
                                }

                                // Throttled camera bearing for the compass overlay.
                                "bearing" -> {
                                    detail.toFloatOrNull()?.let { bearing ->
                                        mainHandler.post { currentOnBearingChange(bearing) }
                                    }
                                }

                                else -> {
                                    Log.w(TAG, "Unknown map event '$kind': $detail")
                                }
                            }
                        }
                    },
                    "femtoBridge",
                )
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
    val accentColors = accentMapColors(isDark)

    // Each effect keys on [pageReady] (so it fires once the page is ready) and on
    // [webView] (so a rebuilt WebView gets the state re-pushed), then pushes the
    // current state to the page.
    LaunchedEffect(
        webView,
        pageReady.value,
        location.latitude,
        location.longitude,
        mapConfig.zoom,
        mapConfig.tiltDeg,
        mapConfig.markerPos,
        mapConfig.bottomSafeFraction,
        mapConfig.rightSafeFraction,
        markerColor,
    ) {
        if (!pageReady.value) return@LaunchedEffect
        val bearing = location.carriedBearing(bearingHolder)
        webView.evaluateJavascript(
            "window.updateCamera && updateCamera(" +
                "${location.latitude}, ${location.longitude}, $bearing, ${mapConfig.zoom}, ${mapConfig.tiltDeg}, " +
                "${mapConfig.markerPos}, ${mapConfig.bottomSafeFraction}, ${mapConfig.rightSafeFraction}, '$markerColor')",
            null,
        )
    }
    LaunchedEffect(webView, pageReady.value, styleRef, accentColors) {
        if (!pageReady.value) return@LaunchedEffect
        // The theme cross-fade animates MaterialTheme colours, so accentColors
        // churns once per frame for the fade duration after a theme change. Each
        // push restarts the page's style swap, so debounce: every churn cancels
        // this effect and only the settled palette reaches the page.
        delay(STYLE_PUSH_DEBOUNCE_MS)
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
                "'${accent?.background ?: ""}', '${accent?.water ?: ""}', '${accent?.land ?: ""}', " +
                "'${accent?.roadMajor ?: ""}', '${accent?.roadMinor ?: ""}', '${accent?.roadCasing ?: ""}', " +
                "'${accent?.building ?: ""}', '${accent?.label ?: ""}')",
            null,
        )
    }
    // Camera-orientation mode, pushed whenever the persisted setting flips (the
    // compass tap or the settings switch) and re-pushed to a rebuilt page.
    LaunchedEffect(webView, pageReady.value, mapConfig.northUp) {
        if (!pageReady.value) return@LaunchedEffect
        webView.evaluateJavascript("window.setNorthUp && setNorthUp(${mapConfig.northUp})", null)
    }
    // One-shot recenter: each locate-button tap bumps the nonce and re-attaches
    // the follow camera. Nonce 0 (no tap yet) is skipped — a fresh page already
    // starts attached; on a page (re)load the host's notion resets to match.
    LaunchedEffect(webView, pageReady.value, recenterNonce) {
        if (!pageReady.value) return@LaunchedEffect
        currentOnFollowChange(true)
        if (recenterNonce > 0) {
            webView.evaluateJavascript("window.setFollow && setFollow(true)", null)
        }
    }
    // LIVE-only feature toggles (3D buildings / terrain) plus the theme-tracked
    // extrusion colour, which applies to EVERY scheme. The page merges them into
    // the style via MapLibre transformStyle, so this re-applies the style.
    LaunchedEffect(webView, pageReady.value, mapConfig.buildings3d, mapConfig.terrain, accentColors.building) {
        if (!pageReady.value) return@LaunchedEffect
        // Same debounce as the style push above: the theme cross-fade churns the
        // building colour once per frame after a theme change.
        delay(STYLE_PUSH_DEBOUNCE_MS)
        webView.evaluateJavascript(
            "window.setFeatures && setFeatures(" +
                "${mapConfig.buildings3d}, ${mapConfig.terrain}, '${accentColors.building}')",
            null,
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, webView) {
        val observer =
            LifecycleEventObserver { _, event ->
                // A view whose renderer died is already destroyed; touching it crashes.
                if (webView in crashedViews) return@LifecycleEventObserver
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
            // onRenderProcessGone already destroyed crashed views; destroying twice
            // is undefined, so disposal only destroys views that died gracefully.
            if (!crashedViews.remove(webView)) webView.destroy()
        }
    }

    Box(modifier = modifier) {
        // Keyed on the WebView so a post-crash rebuild swaps in the fresh view
        // (AndroidView's factory runs once per node otherwise).
        key(webView) {
            AndroidView(
                modifier = Modifier.fillMaxSize().clickable { onTap() },
                factory = { webView },
            )
        }
        Attribution(
            modifier = Modifier.align(Alignment.BottomStart),
            showTerrainCredit = mapConfig.terrain,
        )
    }
}

// Terminal LIVE-map state (repeated renderer deaths, or a fatal bridge event):
// a static notice pointing at the Settings render-mode switch. Deliberately NOT
// an automatic fallback to SNAPSHOT — an earlier auto-downgrade misfired on
// healthy devices and silently overrode the user's chosen backend, so the user
// stays in control here.
@Composable
private fun LiveMapNotice(
    @StringRes titleRes: Int,
    @StringRes hintRes: Int,
    reason: String?,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier.padding(32.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Icon(
        imageVector = Lucide.MapPinOff,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(40.dp),
    )
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 8.dp),
    )
    Text(
        text = stringResource(hintRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
    if (reason != null) {
        Text(
            text = reason,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// A bundled asset served to the WebView over the WebViewAssetLoader https origin so
// MapLibre's tile Worker can fetch it (and the asset's OpenFreeMap sources) cross-origin.
private fun appAssetsUrl(asset: String): String = "https://appassets.androidplatform.net/assets/$asset"

@PreviewLightDark
@Composable
private fun LiveMapNoticePreview() {
    FemtoTheme {
        LiveMapNotice(
            titleRes = R.string.map_live_renderer_gone,
            hintRes = R.string.map_live_renderer_gone_hint,
            reason = "renderer crashed",
        )
    }
}

private const val TAG = "WebMapView"

// Settle window for style pushes: longer than one animation frame (so a churning
// theme fade keeps cancelling the push) but short enough to feel immediate once
// the colours stop moving.
private const val STYLE_PUSH_DEBOUNCE_MS = 150L

// One renderer death rebuilds the WebView silently (a lone death is usually the
// system reclaiming memory, not a fault in the map); a second death inside this
// window means a crash loop, so the rebuild stops and the notice shows instead.
private const val RENDERER_DEATH_WINDOW_MS = 5 * 60_000L
private const val MAX_RENDERER_DEATHS = 2
