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
import androidx.compose.ui.unit.Dp
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
import io.github.seijikohara.femto.data.display.MapBackend
import io.github.seijikohara.femto.data.display.MapStyleSetting
import io.github.seijikohara.femto.data.display.MapboxStyle
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.LocalFemtoDarkTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import kotlinx.coroutines.delay

/**
 * Live map in a WebView — the only path that renders a smooth, animated map
 * *inside Compose*. The backend chosen in the Settings Map section (OSM / MapLibre,
 * or the BYO-credential Mapbox and Google Maps backends) selects the
 * `index.html?backend=` query parameter; the page's entry module
 * dynamic-imports the matching backend module (`webmap/src/backends/`), and
 * every backend honours the same host-bridge contract, so this one composable
 * drives any of them. A native live
 * `MapView` is grey in a Compose `AndroidView` on the head unit (its GL surface is
 * not composited); a WebView composites inline through HWUI and the GL JS library
 * animates the camera. Each page (TypeScript under `webmap/`, built by Gradle into
 * `assets/web/` — see the node block in app/build.gradle.kts) is served via
 * [WebViewAssetLoader] from the real https origin appassets.androidplatform.net (not
 * a file:// URL) so the map library's Web Worker and cross-origin tile fetches
 * resolve against a real origin. GPS fixes drive a heading-up smooth follow (the JS
 * eases or steps between sparse fixes); a chevron marks the self-location, filled
 * with the Material primary and laid on the tilted ground.
 *
 * There is NO auto-fallback: each page relies on the map library's built-in WebGL
 * context-loss handling (`webglcontextlost`/`webglcontextrestored`) and the host
 * keeps the chosen backend regardless. The WebView renders WebGL on the GPU
 * (hardware-accelerated); a device that cannot hold a WebGL context gets the static
 * notice below rather than a silent blank map.
 *
 * The page reports into the host over a one-method [JavascriptInterface] bridge
 * (`window.femtoBridge.onMapEvent(kind, detail)`). Four kinds exist: `error`
 * for transient resource failures (tile / style / DEM fetch — logged, never UI,
 * because the removed auto-downgrade misfired on exactly such ambiguous signals),
 * `fatal` for definitive never-going-to-render facts (no WebGL context, a missing
 * BYO credential, map construction threw), `follow` for camera-follow state flips,
 * and `bearing` (throttled) for the compass overlay. A `fatal` swaps the
 * permanently-blank WebView for a static notice pointing back at the Settings Map
 * section — same posture as renderer-death containment below: inform, never switch
 * the persisted backend.
 *
 * Renderer-death containment is the one exception to "do nothing": without an
 * [android.webkit.WebViewClient.onRenderProcessGone] override the platform kills
 * the whole launcher process when the WebView renderer dies (WebGL is a classic
 * OOM victim on weak head-unit GPUs). Death is a fact reported by the system, not
 * a heuristic like the removed context-loss / readiness signals, so reacting to
 * it cannot misfire on a healthy map. The reaction stays inside the WebView: the
 * first death rebuilds it in place; repeated deaths within
 * [RENDERER_DEATH_WINDOW_MS] stop the rebuild loop and show a static notice that
 * points back at the Settings Map section. The persisted backend is never rewritten.
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
    // Validated-internet connectivity. A false->true transition reloads the page (see
    // reloadGeneration below); defaults true so previews / callers that never wire it
    // never trigger a reload.
    online: Boolean = true,
    onFollowChange: (Boolean) -> Unit = {},
    onBearingChange: (Float) -> Unit = {},
    attributionBottomInset: Dp = 0.dp,
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

    // Only the active backend's credential can affect the loaded page, so an edit
    // to the inactive backend's stored token/key must not rebuild the WebView — and
    // editing a stored Mapbox token while OSM is active must not reload the OSM page.
    val effectiveMapboxToken = if (mapConfig.backend == MapBackend.MAPBOX) mapConfig.mapboxToken else ""
    val effectiveGoogleKey = if (mapConfig.backend == MapBackend.GOOGLEMAPS) mapConfig.googleMapsApiKey else ""
    val effectiveGoogleMapId = if (mapConfig.backend == MapBackend.GOOGLEMAPS) mapConfig.googleMapsMapId else ""

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

    // Connectivity-recovery reload. The map's style, sprite, glyphs, and tiles are all
    // fetched from the network with nothing bundled offline, so a page opened offline
    // cannot render and cannot recover on its own: the OSM page stays blank-but-live
    // (fetch failures are logged `error` events) while the credentialed backends
    // (Mapbox / Google Maps) report a `fatal`. Bumping this generation on the
    // offline->online edge tears down and reloads the WebView — a key of the WebView,
    // pageReady, AND liveInitFailed remembers below, exactly like rendererGeneration —
    // so the resources re-fetch against the now-live network and a fatal gets a fresh
    // online init.
    //
    // The edge detector sits ABOVE the early-return notice branch on purpose: a `fatal`
    // takes that branch, so an effect placed below it would never compose while the
    // notice shows and the fatal could never clear. [wasOnline] carries the previous
    // value in a plain holder (never read in composition, so it triggers no
    // recomposition — like bearingHolder above); a normal online start, an
    // online->offline drop, or the initial value never bumps.
    var reloadGeneration by remember { mutableIntStateOf(0) }
    val wasOnline = remember { booleanArrayOf(online) }
    LaunchedEffect(online) {
        if (online && !wasOnline[0]) reloadGeneration++
        wasOnline[0] = online
    }

    // Flips true once the page's script has run (onPageFinished) so the JS bridge
    // functions exist. The state-pushing effects below gate + key on it, so the
    // current camera / style / feature state is (re)applied as soon as the page is
    // ready — closing the race where an effect fires before the script registers
    // window.updateCamera / setStyleUrl / setFeatures and is silently dropped.
    // Keyed on the SAME tuple as the WebView below so every rebuild — a backend
    // switch, a corrected BYO credential, or a renderer-death / connectivity-recovery
    // generation bump — resets readiness to false; without that the stale `true` would
    // let an effect fire against a fresh page before its script has registered the bridge.
    val pageReady =
        remember(
            rendererGeneration,
            reloadGeneration,
            mapConfig.backend,
            effectiveMapboxToken,
            effectiveGoogleKey,
            effectiveGoogleMapId,
        ) { mutableStateOf(false) }

    // Set by a `fatal` bridge event (see the KDoc): the page itself determined it
    // can never render, so a blank "working" map would be a lie. Like the
    // renderer-death notice, this only informs — the persisted backend is untouched.
    // Keyed on backend AND the active backend's BYO credentials so a fatal from one
    // backend does not suppress the other's page, and re-entering a corrected
    // token / key / Map ID clears a prior failure. reloadGeneration is a key too, so a
    // connectivity-recovery reload clears an offline-triggered fatal (Mapbox / Google
    // Maps report one when opened offline) and the rebuilt page gets a fresh online init.
    var liveInitFailed by
        remember(reloadGeneration, mapConfig.backend, effectiveMapboxToken, effectiveGoogleKey, effectiveGoogleMapId) {
            mutableStateOf(false)
        }
    var lastFatalDetail by
        remember(reloadGeneration, mapConfig.backend, effectiveMapboxToken, effectiveGoogleKey, effectiveGoogleMapId) {
            mutableStateOf<String?>(null)
        }
    // Bridge callbacks arrive on a WebView-managed background thread; Compose
    // state writes must land on the main thread.
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // A blank token/key with the Mapbox or Google Maps backend is a configuration
    // error that will never self-heal at runtime — show the notice immediately so
    // the map area is not a permanently blank white box.
    val mapboxBackend = mapConfig.backend == MapBackend.MAPBOX
    val mapboxTokenMissing = mapboxBackend && mapConfig.mapboxToken.isBlank()

    val googleMapsBackend = mapConfig.backend == MapBackend.GOOGLEMAPS
    val googleMapsKeyMissing = googleMapsBackend && mapConfig.googleMapsApiKey.isBlank()

    if (rendererGaveUp || liveInitFailed || mapboxTokenMissing || googleMapsKeyMissing) {
        Box(modifier = modifier) {
            LiveMapNotice(
                titleRes =
                    when {
                        rendererGaveUp -> R.string.map_live_renderer_gone

                        // Check missing credential before liveInitFailed: a blank credential
                        // also triggers a fatal from the page, so both can be true at once.
                        mapboxTokenMissing -> R.string.map_mapbox_no_token

                        mapboxBackend && liveInitFailed -> R.string.map_mapbox_failed

                        googleMapsKeyMissing -> R.string.map_googlemaps_no_key

                        googleMapsBackend && liveInitFailed -> R.string.map_googlemaps_failed

                        else -> R.string.map_live_init_failed
                    },
                hintRes =
                    when {
                        rendererGaveUp -> R.string.map_live_renderer_gone_hint
                        mapboxTokenMissing -> R.string.map_mapbox_no_token_hint
                        mapboxBackend && liveInitFailed -> R.string.map_mapbox_failed_hint
                        googleMapsKeyMissing -> R.string.map_googlemaps_no_key_hint
                        googleMapsBackend && liveInitFailed -> R.string.map_googlemaps_failed_hint
                        else -> R.string.map_live_init_failed_hint
                    },
                // Why it failed is debugging detail, not driver-facing content.
                reason = (if (rendererGaveUp) lastRendererDeath else lastFatalDetail).takeIf { BuildConfig.DEBUG },
                modifier = Modifier.align(Alignment.Center),
            )
        }
        return
    }

    val webView =
        // Keyed on the active backend and its BYO credentials so a backend switch or
        // a corrected token / key / Map ID tears down the old WebView and loads a
        // fresh page — without these keys the old page keeps running while the new
        // bridge effects fire against the wrong DOM. The credentials are backend-
        // scoped (see above) so editing the inactive backend's token does not rebuild.
        // rendererGeneration remains a key so renderer-death rebuilds still work;
        // reloadGeneration reloads the page when connectivity returns.
        remember(
            rendererGeneration,
            reloadGeneration,
            mapConfig.backend,
            effectiveMapboxToken,
            effectiveGoogleKey,
            effectiveGoogleMapId,
        ) {
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

                        // Read synchronously by the mapbox backend module before map initialisation
                        // to authenticate the Mapbox GL JS instance. The token comes
                        // from MapConfig (user-supplied at runtime via DisplaySettings).
                        @JavascriptInterface
                        fun mapboxToken(): String = mapConfig.mapboxToken

                        // Read synchronously by the googlemaps backend module before map initialisation
                        // to authenticate the Maps JavaScript API instance.
                        @JavascriptInterface
                        fun googleMapsApiKey(): String = mapConfig.googleMapsApiKey

                        // Read synchronously by the googlemaps backend module to enable vector
                        // rendering; empty string means the default raster map is used.
                        @JavascriptInterface
                        fun googleMapsMapId(): String = mapConfig.googleMapsMapId

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
                loadUrl(mapPageUrl(mapConfig.backend))
            }
        }

    // Material primary as the self-location marker fill, so the WebGL puck tracks
    // the user's accent.
    val markerColor = MaterialTheme.colorScheme.primary.toCssHex()

    // Resolve the colour scheme for the active light/dark context. ACCENT recolours
    // the bundled base with these Material colours (the OSM module's transformStyle);
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
        mapConfig.leftSafeFraction,
        markerColor,
    ) {
        if (!pageReady.value) return@LaunchedEffect
        val bearing = location.carriedBearing(bearingHolder)
        webView.evaluateJavascript(
            "window.updateCamera && updateCamera(" +
                "${location.latitude}, ${location.longitude}, $bearing, ${mapConfig.zoom}, ${mapConfig.tiltDeg}, " +
                "${mapConfig.markerPos}, ${mapConfig.bottomSafeFraction}, ${mapConfig.rightSafeFraction}, " +
                "${mapConfig.leftSafeFraction}, '$markerColor')",
            null,
        )
    }
    // OSM backend: push the MapLibre style URL + optional accent recolor palette.
    // The MAPBOX backend has its own style bridge (setMapboxStyle below) and does
    // not use setStyleUrl or the OSM accent colors.
    if (mapConfig.backend == MapBackend.OSM) {
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
    }
    // Mapbox backend: push style ID, light preset, and traffic toggle via the
    // setMapboxStyle bridge. Light preset is derived from the same isDark resolution
    // the OSM path uses so light/dark tracking is consistent across backends.
    if (mapConfig.backend == MapBackend.MAPBOX) {
        LaunchedEffect(webView, pageReady.value, mapConfig.mapboxStyle, isDark, mapConfig.mapboxTraffic) {
            if (!pageReady.value) return@LaunchedEffect
            // Same debounce as the OSM style push: settle after theme cross-fade.
            delay(STYLE_PUSH_DEBOUNCE_MS)
            val styleId = mapboxStyleId(mapConfig.mapboxStyle)
            val lightPreset = lightPresetFor(isDark)
            webView.evaluateJavascript(
                "window.setMapboxStyle && setMapboxStyle('$styleId', '$lightPreset', ${mapConfig.mapboxTraffic})",
                null,
            )
        }
    }
    // Google Maps backend: push map type and traffic toggle live. Map type/traffic
    // do not churn with the theme cross-fade, so no debounce is needed — but the
    // same pageReady guard as the other pushes applies.
    if (mapConfig.backend == MapBackend.GOOGLEMAPS) {
        LaunchedEffect(webView, pageReady.value, mapConfig.googleMapsMapType, mapConfig.googleMapsTraffic) {
            if (!pageReady.value) return@LaunchedEffect
            webView.evaluateJavascript(
                "window.setGoogleMapsOptions && setGoogleMapsOptions('${mapConfig.googleMapsMapType.name}', ${mapConfig.googleMapsTraffic})",
                null,
            )
        }
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
    // OSM-only feature toggles (3D buildings / terrain) plus the theme-tracked
    // extrusion colour. The Mapbox page exposes buildings3d/terrain via its own
    // style (STANDARD) and does not implement the setFeatures bridge.
    if (mapConfig.backend == MapBackend.OSM) {
        LaunchedEffect(
            webView,
            pageReady.value,
            mapConfig.buildings3d,
            mapConfig.terrain,
            accentColors.building,
        ) {
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
        // Native tile credit only for OSM; Mapbox and Google Maps render their own
        // ToS-mandated attribution inside the WebView (see showsNativeAttribution).
        if (showsNativeAttribution(mapConfig.backend)) {
            Attribution(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = attributionBottomInset),
                showTerrainCredit = mapConfig.terrain,
            )
        }
    }
}

// Terminal LIVE-map state (repeated renderer deaths, or a fatal bridge event):
// a static notice pointing back at the Settings Map section. Deliberately NOT an
// automatic fallback to another backend — an earlier auto-downgrade misfired on
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
    FemtoIcon(
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

// Single origin literal for the WebViewAssetLoader https scheme. Both the map
// page base URL and all asset references derive from this constant so the
// origin string appears in exactly one place.
private const val APPASSETS_ORIGIN = "https://appassets.androidplatform.net"
private const val WEB_BASE = "$APPASSETS_ORIGIN/assets/web/"

// A bundled asset served to the WebView over the WebViewAssetLoader https origin so
// MapLibre's tile Worker can fetch it (and the asset's OpenFreeMap sources) cross-origin.
private fun appAssetsUrl(asset: String): String = "$APPASSETS_ORIGIN/assets/$asset"

// Page URL for the active map backend: one entry page, selected by the
// ?backend= query parameter (the value set mirrors webmap/src/backend-name.ts,
// a compatibility contract). Distinct URLs per backend keep a backend switch a
// full page load.
internal fun mapPageUrl(backend: MapBackend) =
    WEB_BASE + "index.html?backend=" + when (backend) {
        MapBackend.MAPBOX -> "mapbox"
        MapBackend.GOOGLEMAPS -> "googlemaps"
        MapBackend.OSM -> "osm"
    }

// Whether the host draws the native tile-credit overlay ([Attribution]) for this
// backend. Only the OSM backend hides its web-side attribution (index.html's CSS +
// the OSM module's `attributionControl: false`) and leans on the host for the
// OpenStreetMap / OpenMapTiles / OpenFreeMap credit. Mapbox and Google Maps render
// their own ToS-mandated attribution INSIDE the WebView (backends/mapbox.ts keeps
// the Mapbox AttributionControl + logo; backends/googlemaps.ts keeps Google's logo
// + credit), so a native overlay there would both duplicate that credit and — by
// naming OpenMapTiles / OpenFreeMap — misattribute tiles those backends never serve.
internal fun showsNativeAttribution(backend: MapBackend) = backend == MapBackend.OSM

// Mapbox GL JS style identifier for the user-chosen style preset.
internal fun mapboxStyleId(style: MapboxStyle) =
    when (style) {
        MapboxStyle.STANDARD -> "standard"
        MapboxStyle.SATELLITE -> "satellite-streets-v12"
        MapboxStyle.STREETS -> "streets-v12"
    }

internal fun lightPresetFor(dark: Boolean) = if (dark) "night" else "day"

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
