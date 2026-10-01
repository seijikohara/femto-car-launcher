package io.github.seijikohara.femto.ui.home.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPinOff
import io.github.seijikohara.femto.BuildConfig
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.common.femtoUserAgent
import io.github.seijikohara.femto.data.display.GoogleMapsRendering
import io.github.seijikohara.femto.data.display.MapBackend
import io.github.seijikohara.femto.data.display.MapStyleSetting
import io.github.seijikohara.femto.data.map.MapRuntimeSignals
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.LocalFemtoDarkTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import kotlinx.coroutines.delay

/**
 * Live map in a WebView — the only path that renders a smooth, animated map
 * *inside Compose*. The backend chosen in the Settings Map section (OSM / MapLibre,
 * or the BYO-key Google Maps backend) selects the
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
 * (`window.femtoBridge.onMapEvent(kind, detail)`). The kinds: `ready` for
 * the first frame the backend actually painted (recorded in
 * [MapRuntimeSignals] for the MAP diagnostics section, which otherwise could not
 * tell a working map from one that failed silently), `tile` for the OSM page's
 * first tile of its current style (its success signal), `error`
 * for transient resource failures (tile / style / DEM fetch — logged, never UI,
 * because the removed auto-downgrade misfired on exactly such ambiguous signals),
 * `fatal` for definitive never-going-to-render facts about the page (no WebGL
 * context, a missing BYO credential, map construction threw, or the page's map
 * data never arrived), `follow` for camera-follow state flips, `frames` for the
 * page's own frame cadence (diagnostics), and `bearing` (throttled) for the
 * compass overlay. A `fatal` swaps the
 * permanently-blank WebView for a static notice (centred in the exposed map
 * region, clear of the floating cards) that names the cause, pointing back at
 * the Settings Map section where a setting can fix it — same posture as
 * renderer-death containment below: inform, never switch the persisted
 * backend. A fatal is additionally retried with a capped exponential-backoff
 * page reload ([liveReloadRetryDelayMsOrNull]), but only while the launcher is
 * visible ([liveReloadStep]); a failure whose map data could not be reached
 * retries until the page renders, whether or not the network reports itself
 * validated, a configuration failure (a refused request, an exception once
 * the map library loaded) retries within a small budget, and the
 * non-self-healing notices (missing credential, renderer give-up) never retry.
 * The OSM page's `tile` restarts the backoff, so the failure after an outage
 * starts at the short first step again.
 *
 * Renderer-death containment is the one exception to "do nothing": without an
 * [android.webkit.WebViewClient.onRenderProcessGone] override the platform kills
 * the whole launcher process when the WebView renderer dies (WebGL is a classic
 * OOM victim on weak head-unit GPUs). Death is a fact reported by the system, not
 * a heuristic like the removed context-loss / readiness signals, so reacting to
 * it cannot misfire on a healthy map. The reaction stays inside the WebView: the
 * first death rebuilds it in place; repeated deaths on screen within
 * [RENDERER_DEATH_WINDOW_MS] stop the rebuild loop and show a static notice that
 * points back at the Settings Map section. The persisted backend is never rewritten.
 * Like the reloads, a rebuild waits for the launcher to be on screen, and a return
 * to the launcher at least [RENDERER_GIVE_UP_SETTLE_MS] after the give-up lifts it.
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
    onOpenLicenses: () -> Unit = {},
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

    // Resolve the colour scheme for the active light/dark context. ACCENT recolours
    // the bundled base with these Material colours (the OSM module's transformStyle);
    // the others are plain hosted / bundled styles.
    val styleRef =
        mapStyleRefFor(
            if (isDark) mapConfig.schemeDark else mapConfig.schemeLight,
            isDark,
            mapConfig.customStyleUrl,
        )
    // The page reads its initial style URL from the bridge at load time, and a
    // scheme change pushes into the live page instead of rebuilding the WebView —
    // so the getter must see the current scheme, not the one captured when the
    // bridge object was created.
    val currentStyleUrl by rememberUpdatedState(styleUrl(styleRef))

    // Only the active backend's credential can affect the loaded page, so an edit
    // to the inactive backend's stored key must not rebuild the WebView — editing a
    // stored Google Maps key while OSM is active must not reload the OSM page.
    val effectiveGoogleKey = if (mapConfig.backend == MapBackend.GOOGLEMAPS) mapConfig.googleMapsApiKey else ""
    val effectiveGoogleMapId = if (mapConfig.backend == MapBackend.GOOGLEMAPS) mapConfig.googleMapsMapId else ""
    // The Maps JS API fixes raster/vector at construction, so a change has to
    // rebuild the WebView rather than push into the live page.
    val effectiveGoogleRendering =
        if (mapConfig.backend == MapBackend.GOOGLEMAPS) mapConfig.googleMapsRendering else GoogleMapsRendering.AUTO
    // Whether the launcher is on screen (the host lifecycle at STARTED or above).
    // The reloads and rebuilds below wait for it: a reload behind another app
    // rebuilds a WebView no one sees, and on Google every reload after the map
    // object exists is a billed map load.
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState = lifecycleOwner.lifecycle.currentStateAsState()
    val started by remember(lifecycleState) {
        derivedStateOf { lifecycleState.value.isAtLeast(Lifecycle.State.STARTED) }
    }
    // Google's colour scheme is likewise fixed at construction (MapOptions
    // .colorScheme), so the map follows the light/dark context the way the OSM
    // backend's style push does only by rebuilding the WebView on a flip. OSM
    // is excluded here so a theme change never reloads the OSM page. The
    // rebuild is a billed map load, so behind another app it waits: the page
    // keeps the context it was built for, and the return rebuilds it once,
    // like the reloads do (liveReloadStep). The holders record what each
    // applied composition used, so the built value is the page's own while
    // hidden (see the SideEffect beside the reload effect).
    val effectiveGoogleDark = mapConfig.backend == MapBackend.GOOGLEMAPS && isDark
    val builtGoogleDark = remember { booleanArrayOf(effectiveGoogleDark) }
    val googleFlipHeld = remember { booleanArrayOf(false) }
    val googleDark = if (started) effectiveGoogleDark else builtGoogleDark[0]
    // The tile host is OSM-only state by the same logic: an override typed while
    // Google Maps is active must not reload the Google page.
    val effectiveTileHostOverride = if (mapConfig.backend == MapBackend.OSM) mapConfig.tileHostOverride else ""
    // A dead custom style is corrected in Settings (a new URL, or leaving CUSTOM),
    // so the failure state below keys on the URL the CUSTOM scheme is actually
    // loading, exactly like the tile-host override: the correction gets a fresh
    // page at once instead of waiting out the retry ladder. Blank whenever no
    // custom style is active, so nothing else is disturbed, and OSM-only like
    // the override: on Google, a flip onto or off a scheme saved as CUSTOM would
    // otherwise clear a failure and load a fresh, billed page.
    val effectiveCustomStyleUrl =
        if (mapConfig.backend == MapBackend.OSM) {
            (styleRef as? MapStyleRef.Hosted)?.takeIf { it.custom }?.url.orEmpty()
        } else {
            ""
        }
    // Keyed on the override alone: the build default cannot change at runtime, and
    // the bridge getter reads the list from a background thread, so it must not be
    // re-allocated on every recomposition (one per location fix).
    val tileHosts =
        remember(effectiveTileHostOverride) { mapTileHosts(effectiveTileHostOverride, BuildConfig.MAP_TILE_HOST) }

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
    // A death's rebuild, due until the launcher is on screen (the renderer
    // effect beside the reload effect), and whether the death came while the
    // launcher was hidden, so that the rebuild runs at the return.
    var rendererRebuildDue by remember { mutableStateOf(false) }
    val rendererRebuildOnReturn = remember { booleanArrayOf(false) }

    // Connectivity-recovery reload. The map's data — the OSM tiles and their TileJSON,
    // sprite, glyphs and hosted styles, or the Google Maps script — comes from the
    // network, so a page opened without data cannot render and cannot recover on its
    // own (a failed fetch is never repeated): both backends report a `fatal`, the OSM
    // page once no tile has arrived within its grace. Bumping this generation tears
    // down and reloads the WebView — a key of the WebView, pageReady, AND
    // liveInitFailed remembers below, exactly like rendererGeneration — so the
    // resources re-fetch and a fatal gets a fresh init. Only the reload effect below
    // bumps it: on the retry backoff, and at once for an offline->online edge, the
    // fast path — both only while the launcher is visible.
    //
    // The edge detector sits ABOVE the early-return notice branch on purpose: a `fatal`
    // takes that branch, so an effect placed below it would never compose while the
    // notice shows and the fatal could never clear. [wasOnline] carries the previous
    // value in a plain holder (never read in composition, so it triggers no
    // recomposition — like bearingHolder above); a normal online start, an
    // online->offline drop, or the initial value never asks for a reload.
    var reloadGeneration by remember { mutableIntStateOf(0) }
    // Auto-retry attempt count (see the reload effect below the failure
    // remembers): it steps the backoff, and spends the bounded budget of the
    // failures that have one. Deliberately NOT keyed on reloadGeneration — each
    // retry bumps that — so the count survives its own reloads; a
    // backend/credential change, a connectivity edge, or the page's first tile
    // (its success signal, see onPageData) resets it.
    val retryAttempts =
        remember(
            mapConfig.backend,
            effectiveGoogleKey,
            effectiveGoogleMapId,
            effectiveGoogleRendering,
            googleDark,
            effectiveTileHostOverride,
            effectiveCustomStyleUrl,
        ) {
            mutableIntStateOf(0)
        }
    // Which of [tileHosts] the next page loads from: `tileHost()` walks the
    // list by it. Its own count, apart from the backoff step, because the two
    // restart at different times: a success signal or a reconnect the return
    // reload covered restarts the step, yet the page on screen still holds the
    // host it loaded with, so the next reload must move on from there — or a
    // two-host setup would load the same host twice in a row. A new host list
    // starts over at its first host (the override).
    //
    // The page reads the host once, at load, so every write here must be
    // paired with a reloadGeneration bump in the same non-suspending block —
    // otherwise the page keeps the host it loaded with and the rotation never
    // reaches the next one.
    val tileHostRotation = remember(tileHosts) { mutableIntStateOf(0) }
    // An offline->online edge asks the reload effect for one reload, which
    // waits while the launcher is hidden. A drop back offline withdraws it: a
    // reload then would only fail.
    var reconnectPending by remember { mutableStateOf(false) }
    // Whether the page on screen was built at the launcher's return, by the
    // return reload or by a rebuild held while hidden (see liveReloadStep). A
    // drop offline ends it: an edge after that is new. So does the page's first
    // tile (onPageData): a page that has drawn is like any other.
    val pageFromReturnReload = remember { booleanArrayOf(false) }
    val wasOnline = remember { booleanArrayOf(online) }
    LaunchedEffect(online) {
        if (online && !wasOnline[0]) reconnectPending = true
        if (!online) {
            reconnectPending = false
            pageFromReturnReload[0] = false
        }
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
            effectiveGoogleKey,
            effectiveGoogleMapId,
            effectiveGoogleRendering,
            googleDark,
            effectiveTileHostOverride,
        ) { mutableStateOf(false) }

    // Set by a `fatal` bridge event (see the KDoc), through onPageFatal: the page
    // itself determined it can never render, so a blank "working" map would be a
    // lie. Like the renderer-death notice, this only informs — the persisted
    // backend is untouched.
    // Keyed on backend AND the active backend's BYO credentials so a fatal from one
    // backend does not suppress the other's page, and re-entering a corrected
    // key / Map ID clears a prior failure. reloadGeneration is a key too, so a
    // connectivity-recovery reload or a retry clears the fatal of a page opened
    // without data (both backends report one) and the rebuilt page gets a fresh init.
    var liveInitFailed by
        remember(
            reloadGeneration,
            mapConfig.backend,
            effectiveGoogleKey,
            effectiveGoogleMapId,
            effectiveGoogleRendering,
            googleDark,
            effectiveTileHostOverride,
            effectiveCustomStyleUrl,
        ) {
            mutableStateOf(false)
        }
    var lastFatalDetail by
        remember(
            reloadGeneration,
            mapConfig.backend,
            effectiveGoogleKey,
            effectiveGoogleMapId,
            effectiveGoogleRendering,
            googleDark,
            effectiveTileHostOverride,
            effectiveCustomStyleUrl,
        ) {
            mutableStateOf<String?>(null)
        }
    // Bridge callbacks arrive on a WebView-managed background thread; Compose
    // state writes must land on the main thread.
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    // The WebView whose page is on screen, or null while a notice shows (set
    // by the effect beside the WebView below). A page a reload or rebuild
    // replaced can still deliver a late bridge event, and it must never act on
    // its successor's state; a bridge handler posted to the main thread checks
    // its own view against this first.
    val livePage = remember { arrayOfNulls<WebView>(1) }

    // A blank key with the Google Maps backend is a configuration error that will
    // never self-heal at runtime — show the notice immediately so the map area is
    // not a permanently blank white box.
    val googleMapsBackend = mapConfig.backend == MapBackend.GOOGLEMAPS
    val googleMapsKeyMissing = googleMapsBackend && mapConfig.googleMapsApiKey.isBlank()

    // Failure auto-retry: reload a page that reported a fatal after a capped
    // exponential backoff, by the policy in [liveReloadRetryDelayMsOrNull]. A
    // network failure keeps retrying whatever `online` says, because the
    // offline->online edge above never fires when data returns under a network
    // that stayed validated; the edge only makes recovery faster. The
    // non-self-healing notices (missing credential, renderer give-up) never
    // retry. Each retry also advances the OSM tile host the rebuilt page reads
    // (tileHostRotation), so an unreachable override falls back to the default
    // host on the next attempt instead of being reloaded forever.
    val retryDelayMs =
        lastFatalDetail
            ?.takeIf { liveInitFailed && !rendererGaveUp && !googleMapsKeyMissing }
            ?.let { liveReloadRetryDelayMsOrNull(it, retryAttempts.intValue, online) }
    // The one place that reloads the page, by [liveReloadStep]: nothing while
    // the launcher is hidden, and the reload that came due meanwhile once, at
    // once, on its return. [reloadHeld] remembers that something came due while
    // hidden (a plain holder, like wasOnline: only this effect reads it).
    val reloadHeld = remember { booleanArrayOf(false) }
    LaunchedEffect(started, reconnectPending, retryDelayMs, retryAttempts.intValue) {
        val step =
            liveReloadStep(
                started = started,
                reconnectPending = reconnectPending,
                retryDelayMs = retryDelayMs,
                heldWhileHidden = reloadHeld[0],
                pageFromReturnReload = pageFromReturnReload[0] && !liveInitFailed,
            )
        when (step) {
            LiveReloadStep.None -> {
                reloadHeld[0] = false
            }

            LiveReloadStep.Held -> {
                reloadHeld[0] = true
            }

            LiveReloadStep.Reconnect, LiveReloadStep.CoveredReconnect -> {
                reloadHeld[0] = false
                reconnectPending = false
                pageFromReturnReload[0] = false
                // A connectivity edge is a new world: restart the backoff short,
                // and give a bounded failure that spent its budget fresh attempts.
                retryAttempts.intValue = 0
                // Only a reload starts the host rotation over. The page a
                // covered reconnect leaves on screen keeps the host it loaded
                // with, so the rotation stays where that page left it.
                if (step == LiveReloadStep.Reconnect) {
                    Log.i(TAG, "LIVE map reload: back online")
                    tileHostRotation.intValue = 0
                    reloadGeneration++
                }
            }

            is LiveReloadStep.Retry -> {
                delay(step.delayMs)
                // The delay is not frame-bound, so it can run out after the
                // launcher has gone behind another app but before a composition
                // has seen it: hold the reload for the return instead.
                if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    reloadHeld[0] = true
                    return@LaunchedEffect
                }
                reloadHeld[0] = false
                pageFromReturnReload[0] = step.onReturn
                retryAttempts.intValue++
                tileHostRotation.intValue++
                val cause = if (step.onReturn) "on return" else "backoff"
                Log.i(TAG, "LIVE map reload: retry ${retryAttempts.intValue} ($cause)")
                reloadGeneration++
            }
        }
    }
    // Renderer containment waits for the launcher like the reloads do
    // (liveReloadStep): a rebuild behind another app loads a page no one sees
    // (on Google, a billed map load), and there the system is likely to kill
    // it again to reclaim memory. So a death's rebuild runs while the launcher
    // is on screen, and at once on its return; a page rebuilt at the return
    // claims the late reconnect edge exactly like the return reload's page
    // (pageFromReturnReload). A return also lifts a give-up that tripped at
    // least RENDERER_GIVE_UP_SETTLE_MS ago, so one bad stretch does not leave
    // the map stopped until the app restarts; staying on screen never lifts
    // it, because this effect restarts only on a lifecycle change or a
    // death's rebuild.
    LaunchedEffect(started, rendererRebuildDue) {
        when {
            !started -> {}

            rendererRebuildDue -> {
                rendererRebuildDue = false
                if (rendererRebuildOnReturn[0]) pageFromReturnReload[0] = true
                rendererRebuildOnReturn[0] = false
                rendererGeneration++
            }

            // The last recorded death is the one that tripped the give-up.
            rendererGaveUp &&
                SystemClock.elapsedRealtime() - rendererDeathsMs.last() >= RENDERER_GIVE_UP_SETTLE_MS -> {
                Log.i(TAG, "LIVE map renderer give-up lifted")
                rendererGaveUp = false
                rendererDeathsMs.clear()
                rendererGeneration++
            }
        }
    }
    // Records the Google light/dark context each applied composition used
    // (googleDark). A flip held while hidden rebuilds the page at the return,
    // and that page claims the late reconnect edge exactly like the return
    // reload's page (pageFromReturnReload).
    SideEffect {
        if (googleFlipHeld[0] && googleDark != builtGoogleDark[0]) pageFromReturnReload[0] = true
        googleFlipHeld[0] = googleDark != effectiveGoogleDark
        builtGoogleDark[0] = googleDark
    }
    // The page's success signal (its first tile, see the `tile` bridge event):
    // the map has its data, so the next failure is a new one. It restarts the
    // backoff, which an outage leaves at its cap, and ends the return reload's
    // claim on a later reconnect (pageFromReturnReload), which would otherwise
    // absorb an edge long after the page it describes has drawn. The host
    // rotation stays: the page on screen still holds its host. Re-bound on
    // every composition so it acts on the current retry state: a live page
    // outlives some of it (a new custom style URL re-keys retryAttempts
    // without rebuilding the page), and state captured when the page was
    // built would be written where no one reads it. A tile right behind the
    // page's own fatal (its no-tile grace ran out a moment before) changes
    // nothing: the notice already stands, and the retry keeps its step and
    // the diagnostics their failure.
    val onPageData by rememberUpdatedState {
        if (!liveInitFailed) {
            Log.i(TAG, "LIVE map data arrived")
            retryAttempts.intValue = 0
            pageFromReturnReload[0] = false
            MapRuntimeSignals.recordDataArrived()
        }
    }
    // A page's `fatal`: the notice, and the retry the reload effect derives
    // from it. Re-bound on every composition like onPageData, and for the same
    // reason: the failure state re-keys on the URL a custom style loads, which
    // a live page changes in place (a new URL, or a light/dark flip onto or
    // off a custom scheme), so state captured when the page was built would
    // take the fatal where no one reads it.
    val onPageFatal by rememberUpdatedState { detail: String ->
        lastFatalDetail = detail
        liveInitFailed = true
    }

    // A page that cannot render gives way to the notice. A dead page whose
    // rebuild waits for the launcher (the renderer effect) leaves an empty map
    // region instead: it must leave the composition at once, since any call on
    // it can crash.
    val noticeShown = rendererGaveUp || liveInitFailed || googleMapsKeyMissing
    if (noticeShown || rendererRebuildDue) {
        Box(modifier = modifier) {
            if (noticeShown) {
                val notice =
                    liveMapNoticeText(
                        rendererGaveUp = rendererGaveUp,
                        googleMapsKeyMissing = googleMapsKeyMissing,
                        googleMapsBackend = googleMapsBackend,
                        customStyleActive = effectiveCustomStyleUrl.isNotBlank(),
                        fatalDetail = lastFatalDetail.takeIf { liveInitFailed },
                    )
                ExposedMapRegion(mapConfig = mapConfig) {
                    LiveMapNotice(
                        titleRes = notice.title,
                        hintRes = notice.hint,
                        // Why it failed is debugging detail, not driver-facing content.
                        reason = (if (rendererGaveUp) lastRendererDeath else lastFatalDetail).takeIf {
                            BuildConfig.DEBUG
                        },
                    )
                }
            }
        }
        return
    }

    val webView =
        // Keyed on the active backend and its BYO credentials so a backend switch or
        // a corrected key / Map ID tears down the old WebView and loads a
        // fresh page — without these keys the old page keeps running while the new
        // bridge effects fire against the wrong DOM. The credentials are backend-
        // scoped (see above) so editing the inactive backend's key does not rebuild.
        // rendererGeneration remains a key so renderer-death rebuilds still work;
        // reloadGeneration reloads the page when connectivity returns.
        remember(
            rendererGeneration,
            reloadGeneration,
            mapConfig.backend,
            effectiveGoogleKey,
            effectiveGoogleMapId,
            effectiveGoogleRendering,
            googleDark,
            effectiveTileHostOverride,
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
                // Identify the launcher to the tile hosts the way the weather and
                // geocoding clients already do — appended to the stock WebView
                // agent, so hosts that sniff for Chrome keep serving.
                settings.userAgentString = settings.userAgentString + " " + femtoUserAgent
                webViewClient =
                    LiveMapWebViewClient(
                        context = context,
                        assetLoader = assetLoader,
                        onPageLoaded = { pageReady.value = true },
                        onRendererDeath = { view, crashed ->
                            val description = if (crashed) "renderer crashed" else "renderer killed by the system"
                            Log.e(TAG, "WebView $description; contained")
                            crashedViews += view
                            lastRendererDeath = description
                            // A kill behind another app is the system reclaiming
                            // memory, and nothing rebuilds while hidden, so it
                            // cannot loop: only deaths on screen feed the
                            // crash-loop count.
                            val onScreen = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                            if (onScreen) {
                                val now = SystemClock.elapsedRealtime()
                                rendererDeathsMs.removeAll { now - it > RENDERER_DEATH_WINDOW_MS }
                                rendererDeathsMs += now
                            }
                            if (onScreen && rendererDeathsMs.size >= MAX_RENDERER_DEATHS) {
                                rendererGaveUp = true
                            } else {
                                rendererRebuildDue = true
                                rendererRebuildOnReturn[0] = !onScreen
                            }
                        },
                    )
                // JS -> Kotlin error channel; registered per WebView instance so a
                // post-crash rebuild (rendererGeneration bump) re-registers it on
                // the fresh view. Kept minimal on purpose: one method, primitive
                // params, and the only page that can call it is our bundled asset
                // served through the WebViewAssetLoader above.
                addJavascriptInterface(
                    object {
                        // Block body: a @JavascriptInterface method must not leak
                        // a non-primitive return type to the JS side.

                        // Read synchronously by the osm backend module before map
                        // initialisation: the origin that serves tiles, styles, sprites
                        // and glyphs — the user's override when set, else the build
                        // default — walked by the host rotation so a dead host gives
                        // way to the next on the following reload (tileHostAt).
                        @JavascriptInterface
                        fun tileHost(): String = tileHostAt(tileHosts, tileHostRotation.intValue)

                        // The raster-DEM TileJSON the page injects while the Terrain
                        // switch is on; a build-time endpoint (MAP_TERRAIN_TILEJSON_URL).
                        @JavascriptInterface
                        fun terrainTileJsonUrl(): String = BuildConfig.MAP_TERRAIN_TILEJSON_URL

                        // The style the page constructs the map with. Without it the
                        // page would fetch a hosted style on every cold start only to
                        // replace it with the bundled one the pushed scheme selects.
                        @JavascriptInterface
                        fun initialStyleUrl(): String = currentStyleUrl

                        // Read synchronously by the googlemaps backend module before map initialisation
                        // to authenticate the Maps JavaScript API instance. The key comes
                        // from MapConfig (user-supplied at runtime via DisplaySettings).
                        @JavascriptInterface
                        fun googleMapsApiKey(): String = mapConfig.googleMapsApiKey

                        // Read synchronously by the googlemaps backend module to enable vector
                        // rendering; empty string means the default raster map is used.
                        @JavascriptInterface
                        fun googleMapsMapId(): String = mapConfig.googleMapsMapId

                        // Read synchronously by the googlemaps backend module before map
                        // initialisation: raster/vector is a construction-time option, and an
                        // explicit value overrides the Map ID's cloud configuration. AUTO sends
                        // nothing and leaves that configuration in charge.
                        @JavascriptInterface
                        fun googleMapsRendering(): String = mapConfig.googleMapsRendering.name

                        // Read synchronously by the googlemaps backend module before map
                        // initialisation: the light/dark context as a ColorScheme name.
                        // Construction-time only, hence googleDark among the WebView
                        // keys, and the page reads the same held value its key names.
                        @JavascriptInterface
                        fun googleMapsColorScheme(): String = if (googleDark) "DARK" else "LIGHT"

                        @JavascriptInterface
                        fun onMapEvent(
                            kind: String,
                            detail: String,
                        ) {
                            when (kind) {
                                // The backend painted its first frame. Recorded for
                                // the MAP diagnostics section, which otherwise cannot
                                // tell a working map from one that failed silently —
                                // onPageFinished only proves the script ran. Google's
                                // `ready` is its first tilesloaded, so its data is in;
                                // the OSM page sends `ready` once its style has
                                // loaded, which a page without data does too (a
                                // failed TileJSON counts as loaded), so its data is
                                // `tile`'s to report.
                                "ready" -> {
                                    MapRuntimeSignals.recordRendered(detail)
                                    if (googleMapsBackend) MapRuntimeSignals.recordDataArrived()
                                }

                                // The OSM page's success signal: the first tile of
                                // its current style arrived (webmap/src/load-outcome.ts).
                                // Never `ready`, for the reason above. Honoured from
                                // the OSM page only: a Google page's tile events can
                                // fire on a page that then reports a rejected key,
                                // and a reset there would turn its bounded retries,
                                // each one a billed map load, into an endless billed
                                // loop. A replaced page's late signal is dropped
                                // (livePage).
                                "tile" -> {
                                    if (!googleMapsBackend) {
                                        mainHandler.post {
                                            if (livePage[0] === this@apply) onPageData()
                                        }
                                    }
                                }

                                // Transient by definition (tile / style / DEM
                                // fetch): log only. The removed auto-downgrade
                                // misfired on exactly such ambiguous signals —
                                // never UI here.
                                "error" -> {
                                    Log.w(TAG, "LIVE map transient error: $detail")
                                }

                                "fatal" -> {
                                    Log.e(TAG, "LIVE map fatal: $detail")
                                    // Kept beyond this composable's lifetime so the
                                    // diagnostics report can state why the map is
                                    // blank; the log tail alone is bounded and drops
                                    // the line once enough logging follows it.
                                    MapRuntimeSignals.recordFailure(detail, SystemClock.elapsedRealtime())
                                    // Bridge calls arrive on a background thread. A
                                    // replaced page's late fatal never reaches its
                                    // successor (livePage).
                                    mainHandler.post {
                                        if (livePage[0] === this@apply) onPageFatal(detail)
                                    }
                                }

                                // Camera-follow state flips (a user drag detached
                                // it, the auto-refollow re-attached it) so the
                                // host's locate button can reflect the mode.
                                "follow" -> {
                                    mainHandler.post { currentOnFollowChange(detail.toBoolean()) }
                                }

                                // A burst of the page's own frame intervals, for the
                                // MAP diagnostics section (see bridge.ts
                                // startFrameSampler).
                                "frames" -> {
                                    MapRuntimeSignals.recordPageFrames(detail, SystemClock.elapsedRealtime())
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
    // The Google Maps backend picks its style through setGoogleMapsOptions below
    // and does not use setStyleUrl or the OSM accent colors.
    if (mapConfig.backend == MapBackend.OSM) {
        LaunchedEffect(webView, pageReady.value, styleRef, accentColors) {
            if (!pageReady.value) return@LaunchedEffect
            // The theme cross-fade animates MaterialTheme colours, so accentColors
            // churns once per frame for the fade duration after a theme change. Each
            // push restarts the page's style swap, so debounce: every churn cancels
            // this effect and only the settled palette reaches the page.
            delay(STYLE_PUSH_DEBOUNCE_MS)
            webView.evaluateJavascript(
                setStyleUrlScript(
                    url = styleUrl(styleRef),
                    accent = (styleRef as? MapStyleRef.Accent)?.let { accentColors },
                    // The page shows MapLibre's own credits exactly when the host
                    // hides its overlay — one decision, made once here.
                    pageAttribution = !showsNativeAttribution(mapConfig.backend, styleRef),
                ),
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
    // extrusion colour. The Google Maps page does not implement the setFeatures
    // bridge.
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

    // Marks this page as the one on screen for the bridge's staleness check
    // (livePage); a replaced page's disposal runs before its successor's start.
    DisposableEffect(webView) {
        livePage[0] = webView
        onDispose { livePage[0] = null }
    }

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
        // Native tile credit only for OSM on the default provider; Google Maps and
        // a custom style render their own credits inside the WebView (see
        // showsNativeAttribution).
        if (showsNativeAttribution(mapConfig.backend, styleRef)) {
            Attribution(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = attributionBottomInset),
                showTerrainCredit = mapConfig.terrain,
                onClick = onOpenLicenses,
            )
        }
    }
}

// The live map page's WebView client. [onPageLoaded] runs once the page script
// has run (module scripts execute before the load event, so the bridge
// functions are registered by then); [onRendererDeath] receives a renderer
// death after the dead view has been detached and destroyed.
internal class LiveMapWebViewClient(
    private val context: Context,
    private val assetLoader: WebViewAssetLoader,
    private val onPageLoaded: () -> Unit,
    private val onRendererDeath: (view: WebView, crashed: Boolean) -> Unit,
) : WebViewClientCompat() {
    // A tap on an attribution link (MapLibre's control under a custom style,
    // or Google's in-page credits) must not navigate this WebView off the map
    // page — it would sit on a web page until the next reload. Anything that
    // is not the page's own appassets origin goes to the system browser; a
    // device without one simply ignores the tap.
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        if (request.url.host == APPASSETS_HOST) return false
        // Only a person's tap on a web link in the main frame reaches the
        // browser; a navigation a page script starts on its own, a subframe,
        // or any other scheme is dropped.
        if (request.isForMainFrame && request.hasGesture() && request.url.scheme in BROWSER_SCHEMES) {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { Log.w(TAG, "No activity for ${request.url.scheme}: link") }
        }
        return true
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

    override fun onPageFinished(
        view: WebView,
        url: String,
    ) = onPageLoaded()

    // Returning true claims the renderer death; the default kills the whole
    // launcher process.
    override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail,
    ): Boolean = onRendererGone(view, crashed = detail.didCrash())

    // The containment itself, apart from the platform's detail object (which
    // apps may not construct), so tests can report a death. The dead view must
    // be detached and destroyed here — any other call on it can crash.
    internal fun onRendererGone(
        view: WebView,
        crashed: Boolean,
    ): Boolean {
        (view.parent as? ViewGroup)?.removeView(view)
        view.destroy()
        onRendererDeath(view, crashed)
        return true
    }
}

// A failed LIVE-map state (repeated renderer deaths on screen, or a fatal bridge
// event): a static notice pointing back at the Settings Map section until the
// map comes back — through a retry, a corrected setting, or, after a renderer
// give-up, a return to the launcher once its settle period has passed.
// Deliberately NOT an automatic fallback to another backend — an earlier
// auto-downgrade misfired on healthy devices and silently overrode the user's
// chosen backend, so the user stays in control here.
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
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp),
    )
    Text(
        text = stringResource(hintRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 4.dp),
    )
    if (reason != null) {
        Text(
            text = reason,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// Single origin literal for the WebViewAssetLoader https scheme. Both the map
// page base URL and all asset references derive from this constant so the
// origin string appears in exactly one place.
private const val APPASSETS_HOST = "appassets.androidplatform.net"
private val BROWSER_SCHEMES = setOf("http", "https")
private const val APPASSETS_ORIGIN = "https://$APPASSETS_HOST"
private const val WEB_BASE = "$APPASSETS_ORIGIN/assets/web/"

// A bundled asset served to the WebView over the WebViewAssetLoader https origin so
// MapLibre's tile Worker can fetch it (and the asset's OpenFreeMap sources) cross-origin.
private fun appAssetsUrl(asset: String): String = "$APPASSETS_ORIGIN/assets/$asset"

// The setStyleUrl bridge call for a resolved style: the URL the page loads
// plus the accent palette (empty = no recolour) and the attribution flag. The
// URL is quoted as a JS string literal: a custom style URL is user input, and
// a stray quote in it must not become code.
internal fun setStyleUrlScript(
    url: String,
    accent: AccentMapColors?,
    pageAttribution: Boolean,
): String =
    "window.setStyleUrl && setStyleUrl(${url.toJsStringLiteral()}, " +
        "'${accent?.background ?: ""}', '${accent?.water ?: ""}', '${accent?.land ?: ""}', " +
        "'${accent?.roadMajor ?: ""}', '${accent?.roadMinor ?: ""}', '${accent?.roadCasing ?: ""}', " +
        "'${accent?.building ?: ""}', '${accent?.label ?: ""}', $pageAttribution)"

// [this] as a double-quoted JS string literal. Only the URL needs it (the
// palette values are the theme's own hex), so this covers exactly what a URL
// can carry: the backslash, the quote, control characters, and the two Unicode
// line terminators a JS source may not contain unescaped on the WebView floor.
// Hand-rolled rather than org.json: the JVM unit tests run against the Android
// stubs, where JSONObject.quote is a no-op returning null.
internal fun String.toJsStringLiteral(): String =
    map { ch ->
        when {
            ch == '\\' -> "\\\\"
            ch == '"' -> "\\\""
            ch < ' ' || ch == '\u2028' || ch == '\u2029' -> "\\u%04x".format(ch.code)
            else -> ch.toString()
        }
    }.joinToString(separator = "", prefix = "\"", postfix = "\"")

// A scheme's style ref as a URL the page can load: hosted directly, or the
// bundled base served over appassets. The page re-points the upstream tile
// origin inside either at the configured host (see tileHost()).
private fun styleUrl(styleRef: MapStyleRef): String =
    when (styleRef) {
        is MapStyleRef.Hosted -> styleRef.url
        is MapStyleRef.Bundled -> appAssetsUrl(styleRef.asset)
        is MapStyleRef.Accent -> appAssetsUrl(styleRef.baseAsset)
    }

// The tile hosts the OSM page may load from, in preference order: the user's
// override (Settings → Map → Tile host) when set, then the build-time default.
// Trailing slashes are dropped so the page's prefix rewrite lines up, and an
// override that merely repeats the default collapses to one entry — otherwise
// half the retry budget would reload the same dead host.
internal fun mapTileHosts(
    override: String,
    default: String,
): List<String> =
    listOf(override.trim().trimEnd('/'), default.trimEnd('/'))
        .filter { it.isNotBlank() }
        .distinct()

// The host the page loads with at a given point of WebMapView's host rotation:
// the list is walked round-robin, so an unreachable override gives way to the
// default on the next reload and a reconnect's reload starts over at the
// override. Empty (a build that blanked MAP_TILE_HOST with no override set)
// yields no host, and the page falls back to the upstream origin its styles are
// written against.
internal fun tileHostAt(
    hosts: List<String>,
    rotation: Int,
): String = if (hosts.isEmpty()) "" else hosts[rotation % hosts.size]

// Page URL for the active map backend: one entry page, selected by the
// ?backend= query parameter (the value set mirrors webmap/src/backend-name.ts,
// a compatibility contract). Distinct URLs per backend keep a backend switch a
// full page load.
internal fun mapPageUrl(backend: MapBackend) =
    WEB_BASE + "index.html?backend=" + when (backend) {
        MapBackend.GOOGLEMAPS -> "googlemaps"
        MapBackend.OSM -> "osm"
    }

// Whether the host draws the native tile-credit overlay ([Attribution]) for this
// backend and style. Only the OSM backend on the default provider's styles hides
// its web-side attribution (index.html's CSS + the OSM module's
// `attributionControl: false`) and leans on the host for the OpenStreetMap /
// OpenMapTiles / OpenFreeMap credit. Google Maps renders its own ToS-mandated
// attribution INSIDE the WebView (backends/googlemaps.ts keeps Google's logo +
// credit), so a native overlay there would both duplicate that credit and — by
// naming OpenMapTiles / OpenFreeMap — misattribute tiles that backend never
// serves. A user-supplied custom style is the same case in the OSM backend: the
// host cannot know what it draws on, so the page shows MapLibre's attribution
// control, which reads the credits the style's sources declare.
internal fun showsNativeAttribution(
    backend: MapBackend,
    styleRef: MapStyleRef,
) = backend == MapBackend.OSM && !(styleRef is MapStyleRef.Hosted && styleRef.custom)

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

// Auto-retry backoff for failed live pages: 5 s, 10 s, 20 s, 40 s, then the
// cap for every later attempt. The cap bounds how long a driver waits for the
// map once data is back but no offline->online edge reloads it at once.
internal fun liveReloadRetryDelayMs(attempt: Int): Long =
    (LIVE_RELOAD_RETRY_BASE_MS shl attempt.coerceAtMost(LIVE_RELOAD_RETRY_MAX_SHIFT))
        .coerceAtMost(LIVE_RELOAD_RETRY_MAX_DELAY_MS)

// The delay before reloading a page that reported [failureDetail] after
// [attempt] earlier retries, or null to stop retrying. A network failure
// retries for as long as it lasts, online or not: data can die and return
// while the network stays VALIDATED (a hotspot that lost its upstream, a unit
// with validation disabled), so no offline->online edge ever reloads the page,
// and a reload costs nothing while no data flows. Every other failure keeps
// the bounded budget and waits for a validated network: a rejected BYO key
// must not hammer the provider (repeated reloads have tripped gm_authFailure
// before), a URL the server refused does not heal by itself, and on Google
// every reload after the map object exists is a billed map load.
internal fun liveReloadRetryDelayMsOrNull(
    failureDetail: String,
    attempt: Int,
    online: Boolean,
): Long? =
    liveReloadRetryDelayMs(attempt).takeIf {
        isNetworkFailure(failureDetail) || (online && attempt < MAX_LIVE_RELOAD_RETRIES)
    }

// What WebMapView's reload effect does next.
internal sealed interface LiveReloadStep {
    // Nothing is due.
    data object None : LiveReloadStep

    // A reload came due while the launcher is hidden; it waits for the return.
    data object Held : LiveReloadStep

    // Reload for an offline->online edge, at once, restarting the backoff.
    data object Reconnect : LiveReloadStep

    // An offline->online edge the reload at the launcher's return already
    // covered: restart the backoff, reload nothing.
    data object CoveredReconnect : LiveReloadStep

    // Retry the failed page after [delayMs]; [onReturn] for the reload that
    // runs at once when the launcher comes back on screen.
    data class Retry(
        val delayMs: Long,
        val onReturn: Boolean,
    ) : LiveReloadStep
}

// The next reload step, given what is due: a reconnect ([reconnectPending],
// an offline->online edge) or a retry of a failed page ([retryDelayMs], from
// [liveReloadRetryDelayMsOrNull]; null when none is due). Nothing reloads
// while the launcher is hidden ([started] false); what came due meanwhile is
// held, and the return reloads once, at once ([heldWhileHidden]), after which
// the backoff resumes from the next attempt. A reconnect wins over a retry:
// it reloads just the same and also restarts the backoff.
//
// The dashboard collects its state with the lifecycle, so an edge that came
// behind another app reaches this composable only once the launcher is back —
// seconds after the return reload rebuilt the page on the restored network.
// While the page on screen was built at the return (by that reload, or by a
// rebuild held while hidden) and has neither failed nor drawn its first tile
// ([pageFromReturnReload]), such an edge reloads nothing more.
internal fun liveReloadStep(
    started: Boolean,
    reconnectPending: Boolean,
    retryDelayMs: Long?,
    heldWhileHidden: Boolean,
    pageFromReturnReload: Boolean,
): LiveReloadStep =
    when {
        reconnectPending && !started -> LiveReloadStep.Held
        reconnectPending && pageFromReturnReload -> LiveReloadStep.CoveredReconnect
        reconnectPending -> LiveReloadStep.Reconnect
        retryDelayMs == null -> LiveReloadStep.None
        !started -> LiveReloadStep.Held
        heldWhileHidden -> LiveReloadStep.Retry(delayMs = 0L, onReturn = true)
        else -> LiveReloadStep.Retry(delayMs = retryDelayMs, onReturn = false)
    }

// Whether a page `fatal` says the page's map data could not be reached — its
// style, its tile host, or its backend's script got no response, a server
// error or a throttle — rather than a failure a reload cannot fix: a request
// the server refused (style-load-rejected, tile-host-rejected) or an
// exception thrown once the map library loaded (map-init-exception). Read
// from the detail's leading kind, which webmap/src/load-outcome.ts assigns;
// the kinds are a compatibility contract with the page, which
// FailureKindContractTest guards.
internal fun isNetworkFailure(failureDetail: String): Boolean = failureKind(failureDetail) in NetworkFailureKinds

// Whether a page `fatal` says the map server answered the OSM page and
// refused its requests (a 4xx): a tile host or a hosted style that only a
// setting or the provider can change.
internal fun isRefusedFailure(failureDetail: String): Boolean = failureKind(failureDetail) in RefusedFailureKinds

private fun failureKind(failureDetail: String): String = failureDetail.substringBefore(':').trim()

// The host's mirror of the kinds webmap/src/load-outcome.ts reports: the
// network kinds (the true side of each of its classifying ternaries) and the
// refused-request kinds (the false side of its two outcome gates).
internal val NetworkFailureKinds = setOf("tile-host-unreachable", "style-load-failed", "backend-load-failed")
internal val RefusedFailureKinds = setOf("tile-host-rejected", "style-load-rejected")

// The failure notice's title and hint.
internal data class LiveMapNoticeText(
    @StringRes val title: Int,
    @StringRes val hint: Int,
)

// Which notice replaces a failed live page. [fatalDetail] is the page's
// `fatal`, or null when the page reported none (a renderer give-up, a
// missing key).
internal fun liveMapNoticeText(
    rendererGaveUp: Boolean,
    googleMapsKeyMissing: Boolean,
    googleMapsBackend: Boolean,
    customStyleActive: Boolean,
    fatalDetail: String?,
): LiveMapNoticeText =
    when {
        rendererGaveUp -> {
            LiveMapNoticeText(R.string.map_live_renderer_gone, R.string.map_live_renderer_gone_hint)
        }

        // Before the fatal: a blank key also makes the page report one, so both
        // hold at once.
        googleMapsKeyMissing -> {
            LiveMapNoticeText(R.string.map_googlemaps_no_key, R.string.map_googlemaps_no_key_hint)
        }

        googleMapsBackend -> {
            LiveMapNoticeText(R.string.map_googlemaps_failed, R.string.map_googlemaps_failed_hint)
        }

        // A custom style that never loaded is a Settings problem under
        // Appearance, not a provider problem.
        customStyleActive -> {
            LiveMapNoticeText(R.string.map_custom_style_failed, R.string.map_custom_style_failed_hint)
        }

        // The retry recovers unreachable data by itself, so the notice says so
        // instead of sending the driver to Settings.
        fatalDetail?.let(::isNetworkFailure) == true -> {
            LiveMapNoticeText(R.string.map_live_data_unavailable, R.string.map_live_data_unavailable_hint)
        }

        fatalDetail?.let(::isRefusedFailure) == true -> {
            LiveMapNoticeText(R.string.map_live_data_refused, R.string.map_live_data_refused_hint)
        }

        else -> {
            LiveMapNoticeText(R.string.map_live_init_failed, R.string.map_live_init_failed_hint)
        }
    }

private const val LIVE_RELOAD_RETRY_BASE_MS = 5_000L
private const val LIVE_RELOAD_RETRY_MAX_DELAY_MS = 60_000L

// coerceAtMost on the shift keeps the Long shift well-defined for any attempt.
private const val LIVE_RELOAD_RETRY_MAX_SHIFT = 5
internal const val MAX_LIVE_RELOAD_RETRIES = 6

// Settle window for style pushes: longer than one animation frame (so a churning
// theme fade keeps cancelling the push) but short enough to feel immediate once
// the colours stop moving.
private const val STYLE_PUSH_DEBOUNCE_MS = 150L

// One renderer death rebuilds the WebView silently (a lone death is usually the
// system reclaiming memory, not a fault in the map); a second death on screen
// inside this window means a crash loop, so the rebuild stops and the notice
// shows instead. A death while the launcher is hidden is never counted.
private const val RENDERER_DEATH_WINDOW_MS = 5 * 60_000L
private const val MAX_RENDERER_DEATHS = 2

// How long the renderer give-up holds before a return to the launcher may lift
// it: the crash-loop window itself, so the deaths that tripped it have aged out
// of the window and the crash-loop rule judges afresh. A map that still crashes
// then needs two new deaths inside the window to give up again, so a genuine
// crash loop costs at most two rebuilds per return, while a give-up tripped by
// an unlucky pair of kills on screen does not outlast the next return after the
// window.
internal const val RENDERER_GIVE_UP_SETTLE_MS = RENDERER_DEATH_WINDOW_MS
