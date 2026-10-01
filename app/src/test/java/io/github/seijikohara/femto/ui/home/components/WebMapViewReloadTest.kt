package io.github.seijikohara.femto.ui.home.components

import android.location.Location
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.seijikohara.femto.BuildConfig
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.display.MapBackend
import io.github.seijikohara.femto.data.display.MapColorScheme
import io.github.seijikohara.femto.data.display.MapStyleSetting
import io.github.seijikohara.femto.data.map.MapRuntimeSignals
import io.github.seijikohara.femto.testfixtures.BoundedFailureDetails
import io.github.seijikohara.femto.testfixtures.FakeLifecycleOwner
import io.github.seijikohara.femto.testfixtures.NetworkFailureDetails
import io.github.seijikohara.femto.testfixtures.styleLoadRejectedDetail
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Wiring tests for [WebMapView]'s reload schedule: a failed page reloads on
 * the backoff while the launcher is visible, never while it is hidden, and
 * once at once on its return; an offline->online edge that arrives while
 * hidden reloads on the return too; the OSM page's first tile restarts the
 * backoff. The page is the real WebMapView over a Robolectric WebView: an
 * event arrives through the registered `femtoBridge` the way the page sends
 * it, the failure notice replaces the WebView, and a reload shows as a new
 * WebView instance.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WebMapViewReloadTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val host = FakeLifecycleOwner()
    private val online = mutableStateOf(true)
    private val mapConfig = mutableStateOf(MapConfig())

    @Test fun `a network failure while visible reloads after the backoff`() {
        showMap()
        val first = page()
        reportFatal(NetworkFailure)
        assertEquals(0, pages().size, "the notice replaces the failed page")
        advanceBy(liveReloadRetryDelayMs(0) - MARGIN_MS)
        assertEquals(0, pages().size)
        advanceBy(2 * MARGIN_MS)
        assertNotSame(first, page())
    }

    @Test fun `a network failure keeps reloading past the bounded budget while offline`() {
        // Offline throughout: the bounded budget and its `online` gate would
        // have stopped this ladder after six tries, or never started it.
        showMap(onlineAtStart = false)
        repeat(MAX_LIVE_RELOAD_RETRIES + 2) { attempt ->
            reportFatal(NetworkFailure)
            advanceBy(liveReloadRetryDelayMs(attempt) - MARGIN_MS)
            assertEquals(0, pages().size, "attempt $attempt waits out its delay")
            advanceBy(2 * MARGIN_MS)
            assertEquals(1, pages().size, "attempt $attempt reloads")
        }
    }

    @Test fun `a hidden launcher holds the retry and reloads at once on its return`() {
        showMap()
        reportFatal(NetworkFailure)
        setLifecycle(Lifecycle.State.CREATED)
        advanceBy(TEN_MINUTES_MS)
        assertEquals(0, pages().size, "no reload while hidden")
        setLifecycle(Lifecycle.State.RESUMED)
        val returned = page()
        advanceBy(liveReloadRetryDelayMs(0) - MARGIN_MS)
        assertSame(returned, page(), "exactly one reload on the return")
    }

    @Test fun `the return reload counts as a retry and the backoff resumes`() {
        showMap()
        reportFatal(NetworkFailure)
        setLifecycle(Lifecycle.State.CREATED)
        advanceBy(TEN_MINUTES_MS)
        setLifecycle(Lifecycle.State.RESUMED)
        reportFatal(NetworkFailure)
        advanceBy(liveReloadRetryDelayMs(1) - MARGIN_MS)
        assertEquals(0, pages().size)
        advanceBy(2 * MARGIN_MS)
        assertEquals(1, pages().size)
    }

    // The backoff delay is not frame-bound: it can run out after the hide and
    // before any composition has seen it.
    @Test fun `a backoff that runs out just after the hide waits for the return`() {
        showMap()
        // The backoff starts in the frame that composes the fatal, one frame on.
        val backoffEnds = rule.mainClock.currentTime + FRAME_MS + liveReloadRetryDelayMs(0)
        reportFatal(NetworkFailure)
        rule.mainClock.advanceTimeBy(backoffEnds - HALF_FRAME_MS - rule.mainClock.currentTime, ignoreFrameDuration = true)
        host.moveTo(Lifecycle.State.CREATED)
        rule.mainClock.advanceTimeBy(FRAME_MS, ignoreFrameDuration = true)
        settle()
        advanceBy(TEN_MINUTES_MS)
        assertEquals(0, pages().size, "no reload while hidden")
        setLifecycle(Lifecycle.State.RESUMED)
        assertEquals(1, pages().size, "one reload on the return")
    }

    @Test fun `a failure reported while hidden reloads on the return, not before`() {
        showMap()
        setLifecycle(Lifecycle.State.CREATED)
        // The page's own grace timer can fire behind another app.
        reportFatal(NetworkFailure)
        advanceBy(TEN_MINUTES_MS)
        assertEquals(0, pages().size)
        setLifecycle(Lifecycle.State.RESUMED)
        val returned = page()
        advanceBy(liveReloadRetryDelayMs(0) - MARGIN_MS)
        assertSame(returned, page(), "exactly one reload on the return")
    }

    @Test fun `a reconnect while visible reloads at once`() {
        showMap(onlineAtStart = false)
        val first = page()
        setOnline(true)
        assertNotSame(first, page())
    }

    @Test fun `a reconnect while hidden reloads on the return, not before`() {
        showMap(onlineAtStart = false)
        val first = page()
        setLifecycle(Lifecycle.State.CREATED)
        setOnline(true)
        advanceBy(TEN_MINUTES_MS)
        assertSame(first, page(), "no reload while hidden")
        setLifecycle(Lifecycle.State.RESUMED)
        assertNotSame(first, page())
    }

    @Test fun `a reconnect lost again before the return does not reload`() {
        showMap(onlineAtStart = false)
        val first = page()
        setLifecycle(Lifecycle.State.CREATED)
        setOnline(true)
        setOnline(false)
        setLifecycle(Lifecycle.State.RESUMED)
        assertSame(first, page())
    }

    // The dashboard collects its state with the lifecycle, so a reconnect that
    // happened behind another app reaches the map only after the return —
    // seconds after the return reload has already rebuilt the page.
    @Test fun `a reconnect seen just after the return reload does not reload again`() {
        showMap(onlineAtStart = false)
        reportFatal(NetworkFailure)
        setLifecycle(Lifecycle.State.CREATED)
        advanceBy(TEN_MINUTES_MS)
        setLifecycle(Lifecycle.State.RESUMED)
        val reloaded = page()
        setOnline(true)
        advanceBy(TEN_MINUTES_MS)
        assertSame(reloaded, page(), "the return reload already covered the reconnect")
    }

    @Test fun `a return-reloaded page that fails again still reloads at once on a reconnect`() {
        showMap(onlineAtStart = false)
        reportFatal(NetworkFailure)
        setLifecycle(Lifecycle.State.CREATED)
        setLifecycle(Lifecycle.State.RESUMED)
        reportFatal(NetworkFailure)
        assertEquals(0, pages().size)
        setOnline(true)
        assertEquals(1, pages().size, "the reconnect reloads the failed page at once")
    }

    // After a long outage the backoff sits at its cap; the page's data coming
    // back must bring the next, unrelated failure down to the first step.
    @Test fun `a first tile restarts the backoff`() {
        showMap()
        failPages(OUTAGE_STEPS)
        reportTile()
        reportFatal(NetworkFailure)
        advanceBy(liveReloadRetryDelayMs(0) - MARGIN_MS)
        assertEquals(0, pages().size)
        advanceBy(2 * MARGIN_MS)
        assertEquals(1, pages().size, "the failure after the data came back reloads on the first step")
    }

    // The page's own no-tile fatal can land a frame before its first tile: the
    // notice already stands, so that tile must neither restart the backoff nor
    // clear the failure on record.
    @Test fun `a first tile right behind the page's own fatal changes nothing`() {
        showMap()
        failPages(1)
        send("fatal", NetworkFailure)
        send("tile", "openmaptiles")
        settle()
        assertEquals(NetworkFailure, MapRuntimeSignals.lastFailureOrNull()?.detail, "the failure stays on record")
        advanceBy(liveReloadRetryDelayMs(0) + MARGIN_MS)
        assertEquals(0, pages().size, "the backoff kept its step")
        advanceBy(liveReloadRetryDelayMs(1) - liveReloadRetryDelayMs(0))
        assertEquals(1, pages().size)
    }

    // A page a reload replaced can still deliver a late event; like its late
    // fatal, it must never act on its successor.
    @Test fun `a first tile from a replaced page leaves the backoff alone`() {
        showMap()
        val replaced = page()
        reportFatal(NetworkFailure)
        advanceBy(liveReloadRetryDelayMs(0) + MARGIN_MS)
        reportTile(page = replaced)
        reportFatal(NetworkFailure)
        advanceBy(liveReloadRetryDelayMs(0) + MARGIN_MS)
        assertEquals(0, pages().size, "the backoff kept its second step")
        advanceBy(liveReloadRetryDelayMs(1) - liveReloadRetryDelayMs(0))
        assertEquals(1, pages().size)
    }

    // Google's tile events can fire on a page that then reports a rejected
    // key; a reset there would turn the bounded, billed retries into an
    // endless billed loop.
    @Test fun `a tile from a Google page leaves the backoff alone`() {
        showMap(config = GoogleMapsConfig)
        reportFatal(GoogleAuthFailure)
        advanceBy(liveReloadRetryDelayMs(0) + MARGIN_MS)
        reportTile()
        reportFatal(GoogleAuthFailure)
        advanceBy(liveReloadRetryDelayMs(0) + MARGIN_MS)
        assertEquals(0, pages().size, "the billed retries kept their count")
        advanceBy(liveReloadRetryDelayMs(1) - liveReloadRetryDelayMs(0))
        assertEquals(1, pages().size)
    }

    @Test fun `a first tile ends the return reload's claim on a later reconnect`() {
        showMap(onlineAtStart = false)
        reportFatal(NetworkFailure)
        setLifecycle(Lifecycle.State.CREATED)
        setLifecycle(Lifecycle.State.RESUMED)
        val returned = page()
        reportTile()
        setOnline(true)
        assertNotSame(returned, page(), "a page that has drawn reloads on a reconnect like any other")
    }

    // Two hosts: the return reload moved to the second; a reconnect it
    // covered must not send the next reload back to the host that page holds.
    @Test fun `a reconnect a return reload covered keeps the tile host rotation`() {
        showMap(onlineAtStart = false, config = MapConfig(tileHostOverride = OVERRIDE_TILE_HOST))
        val hosts = mapTileHosts(OVERRIDE_TILE_HOST, BuildConfig.MAP_TILE_HOST)
        assertEquals(2, hosts.size, "the override and the build default")
        assertEquals(hosts[0], tileHostOf(page()))
        reportFatal(NetworkFailure)
        setLifecycle(Lifecycle.State.CREATED)
        setLifecycle(Lifecycle.State.RESUMED)
        val returned = page()
        assertEquals(hosts[1], tileHostOf(returned))
        setOnline(true)
        assertSame(returned, page(), "the return reload covered the reconnect")
        reportFatal(NetworkFailure)
        advanceBy(liveReloadRetryDelayMs(0) + MARGIN_MS)
        assertEquals(hosts[0], tileHostOf(page()), "the next reload moves on from the host that failed")
    }

    // OSM sends `ready` once its style has loaded, which a page without data
    // does too, so only the first tile may clear the diagnostics' record.
    @Test fun `an OSM ready leaves the diagnostics' last failure standing`() {
        showMap()
        reportFatal(NetworkFailure)
        advanceBy(liveReloadRetryDelayMs(0) + MARGIN_MS)
        reportReady()
        assertEquals(NetworkFailure, MapRuntimeSignals.lastFailureOrNull()?.detail)
    }

    @Test fun `a first tile clears the diagnostics' last failure`() {
        showMap()
        reportFatal(NetworkFailure)
        advanceBy(liveReloadRetryDelayMs(0) + MARGIN_MS)
        reportTile()
        assertNull(MapRuntimeSignals.lastFailureOrNull())
    }

    // The Google page's `ready` is its first `tilesloaded`: tiles, not a style.
    @Test fun `a Google ready still clears the diagnostics' last failure`() {
        showMap(config = GoogleMapsConfig)
        reportFatal(GoogleAuthFailure)
        advanceBy(liveReloadRetryDelayMs(0) + MARGIN_MS)
        reportReady()
        assertNull(MapRuntimeSignals.lastFailureOrNull())
    }

    @Test fun `a renderer death while visible rebuilds at once`() {
        showMap()
        val dead = page()
        killRenderer(dead)
        assertNotSame(dead, page())
    }

    // A rebuild behind another app loads a page no one sees, and a rebuilt
    // page killed there again would trip the crash-loop give-up unwatched.
    @Test fun `a renderer death while hidden rebuilds on the return, not before`() {
        showMap()
        val dead = page()
        setLifecycle(Lifecycle.State.CREATED)
        killRenderer(dead)
        advanceBy(TEN_MINUTES_MS)
        assertEquals(0, pages().size, "no rebuild while hidden")
        setLifecycle(Lifecycle.State.RESUMED)
        assertNotSame(dead, page(), "one rebuild on the return")
    }

    // A lone kill behind another app is the system reclaiming memory, and
    // nothing rebuilds while hidden, so it cannot be part of a crash loop.
    @Test fun `a renderer kill while hidden does not count toward the give-up`() {
        showMap()
        killRenderer(page())
        setLifecycle(Lifecycle.State.CREATED)
        killRenderer(page())
        setLifecycle(Lifecycle.State.RESUMED)
        assertEquals(1, pages().size, "the map is rebuilt, not given up")
    }

    @Test fun `a Google light-dark flip while visible rebuilds at once`() {
        showMap(config = GoogleMapsConfig.copy(style = MapStyleSetting.LIGHT))
        val light = page()
        setMapConfig(GoogleMapsConfig.copy(style = MapStyleSetting.DARK))
        assertNotSame(light, page())
        assertEquals("DARK", colorSchemeOf(page()))
    }

    // Google fixes its colour scheme at construction, so a flip rebuilds the
    // page: a billed map load, which must wait until someone can see it.
    @Test fun `a Google light-dark flip while hidden rebuilds on the return, not before`() {
        showMap(config = GoogleMapsConfig.copy(style = MapStyleSetting.LIGHT))
        val light = page()
        setLifecycle(Lifecycle.State.CREATED)
        setMapConfig(GoogleMapsConfig.copy(style = MapStyleSetting.DARK))
        advanceBy(TEN_MINUTES_MS)
        assertSame(light, page(), "no rebuild while hidden")
        setLifecycle(Lifecycle.State.RESUMED)
        assertNotSame(light, page(), "one rebuild on the return")
        assertEquals("DARK", colorSchemeOf(page()))
    }

    // Like the return reload's page, a page rebuilt at the return was built on
    // the network the launcher came back to: a reconnect edge that reaches the
    // map after it must not reload it again (on Google, a second billed load).
    @Test fun `a reconnect seen just after the return's renderer rebuild does not reload again`() {
        showMap(onlineAtStart = false)
        setLifecycle(Lifecycle.State.CREATED)
        killRenderer(page())
        setLifecycle(Lifecycle.State.RESUMED)
        val rebuilt = page()
        setOnline(true)
        advanceBy(TEN_MINUTES_MS)
        assertSame(rebuilt, page(), "the rebuild at the return covered the reconnect")
    }

    @Test fun `a reconnect seen just after the return's Google light-dark rebuild does not reload again`() {
        showMap(onlineAtStart = false, config = GoogleMapsConfig.copy(style = MapStyleSetting.LIGHT))
        setLifecycle(Lifecycle.State.CREATED)
        setMapConfig(GoogleMapsConfig.copy(style = MapStyleSetting.DARK))
        setLifecycle(Lifecycle.State.RESUMED)
        val rebuilt = page()
        setOnline(true)
        advanceBy(TEN_MINUTES_MS)
        assertSame(rebuilt, page(), "the rebuild at the return covered the reconnect")
    }

    // Only a rebuild at the return claims the edge: one on screen is like any
    // other page.
    @Test fun `a reconnect after a renderer rebuild on screen still reloads`() {
        showMap(onlineAtStart = false)
        killRenderer(page())
        val rebuilt = page()
        setOnline(true)
        assertNotSame(rebuilt, page())
    }

    @Test fun `a reconnect after a Google light-dark rebuild on screen still reloads`() {
        showMap(onlineAtStart = false, config = GoogleMapsConfig.copy(style = MapStyleSetting.LIGHT))
        setMapConfig(GoogleMapsConfig.copy(style = MapStyleSetting.DARK))
        val rebuilt = page()
        setOnline(true)
        assertNotSame(rebuilt, page())
    }

    @Test fun `a renderer give-up lifts on a return once the settle period has passed`() {
        showMap()
        killRenderer(page())
        killRenderer(page())
        assertEquals(0, pages().size, "a second death inside the window gives up")
        setLifecycle(Lifecycle.State.CREATED)
        advanceSystemClock(RENDERER_GIVE_UP_SETTLE_MS - MARGIN_MS)
        setLifecycle(Lifecycle.State.RESUMED)
        assertEquals(0, pages().size, "a return before the settle period keeps the notice")
        setLifecycle(Lifecycle.State.CREATED)
        advanceSystemClock(2 * MARGIN_MS)
        setLifecycle(Lifecycle.State.RESUMED)
        assertEquals(1, pages().size, "a return after it rebuilds the map")
    }

    @Test fun `a renderer give-up stays while the launcher stays on screen`() {
        showMap()
        killRenderer(page())
        killRenderer(page())
        advanceSystemClock(RENDERER_GIVE_UP_SETTLE_MS + MARGIN_MS)
        assertEquals(0, pages().size, "only a return lifts it")
    }

    // A custom style changes inside the live page, while the failure state
    // re-keys on its URL: the page's fatal must reach the new state.
    @Test fun `a refused custom style after its URL changed on a live page shows the notice`() {
        showMap(config = customStyleConfig(FIRST_STYLE_URL))
        val live = page()
        setMapConfig(customStyleConfig(SECOND_STYLE_URL))
        assertSame(live, page(), "the new style loads in the live page")
        reportFatal(styleLoadRejectedDetail(SECOND_STYLE_URL))
        assertEquals(0, pages().size, "the notice replaces the page")
        rule.onNodeWithText(rule.activity.getString(R.string.map_custom_style_failed)).assertExists()
        advanceBy(liveReloadRetryDelayMs(0) + MARGIN_MS)
        assertEquals(1, pages().size, "and the retry reloads it")
    }

    @Test fun `a refused custom style after a flip onto it on a live page shows the notice`() {
        val config = customStyleConfig(FIRST_STYLE_URL).copy(schemeLight = MapColorScheme.ACCENT)
        showMap(config = config.copy(style = MapStyleSetting.LIGHT))
        val live = page()
        setMapConfig(config.copy(style = MapStyleSetting.DARK))
        assertSame(live, page(), "the flip restyles the live page")
        reportFatal(styleLoadRejectedDetail(FIRST_STYLE_URL))
        assertEquals(0, pages().size, "the notice replaces the page")
    }

    // A custom style saved for OSM is not the Google page's state: a flip onto
    // or off it must leave a failed Google page alone while hidden, where a
    // fresh page would be a billed map load no one sees.
    @Test fun `a flip across a saved OSM custom style leaves a failed Google page alone while hidden`() {
        val config =
            GoogleMapsConfig.copy(
                style = MapStyleSetting.LIGHT,
                schemeLight = MapColorScheme.CUSTOM,
                customStyleUrl = FIRST_STYLE_URL,
            )
        showMap(config = config)
        reportFatal(GoogleAuthFailure)
        setLifecycle(Lifecycle.State.CREATED)
        setMapConfig(config.copy(style = MapStyleSetting.DARK))
        advanceBy(TEN_MINUTES_MS)
        assertEquals(0, pages().size, "no page while hidden")
        setLifecycle(Lifecycle.State.RESUMED)
        val returned = page()
        advanceBy(liveReloadRetryDelayMs(0) + MARGIN_MS)
        assertSame(returned, page(), "exactly one page on the return")
    }

    @Test fun `a late fatal from a replaced page leaves its successor alone`() {
        showMap()
        val replaced = page()
        reportFatal(NetworkFailure)
        advanceBy(liveReloadRetryDelayMs(0) + MARGIN_MS)
        val successor = page()
        reportFatal(NetworkFailure, page = replaced)
        assertSame(successor, page())
    }

    private fun showMap(
        onlineAtStart: Boolean = true,
        config: MapConfig = MapConfig(),
    ) {
        online.value = onlineAtStart
        mapConfig.value = config
        host.moveTo(Lifecycle.State.RESUMED)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides host) {
                FemtoTheme {
                    WebMapView(
                        location = Location("test"),
                        mapConfig = mapConfig.value,
                        onTap = {},
                        online = online.value,
                    )
                }
            }
        }
        settle()
    }

    private fun pages(): List<WebView> =
        rule.activity.window.decorView
            .webViews()

    private fun page(): WebView = pages().single()

    // The bridge object the host registered on [page], as the page script
    // reaches it.
    private fun bridgeOf(page: WebView): Any = shadowOf(page).getJavascriptInterface("femtoBridge")

    // An event as the page sends it: through the bridge of [page], the one on
    // screen unless a test replays a page a reload replaced.
    private fun report(
        kind: String,
        detail: String,
        page: WebView = page(),
    ) {
        send(kind, detail, page)
        settle()
    }

    // [report] without the frames after it, for events the page sends back to
    // back, before the host has composed anything in between.
    private fun send(
        kind: String,
        detail: String,
        page: WebView = page(),
    ) {
        val bridge = bridgeOf(page)
        bridge.javaClass
            .getMethod("onMapEvent", String::class.java, String::class.java)
            .invoke(bridge, kind, detail)
    }

    private fun reportFatal(
        detail: String,
        page: WebView = page(),
    ) = report("fatal", detail, page)

    // The OSM page's success signal: the first tile of its style.
    private fun reportTile(page: WebView = page()) = report("tile", "openmaptiles", page)

    private fun reportReady() = report("ready", "test renderer")

    // Fails [steps] pages in a row, each reloaded once its backoff step passes.
    private fun failPages(steps: Int) =
        repeat(steps) { attempt ->
            reportFatal(NetworkFailure)
            advanceBy(liveReloadRetryDelayMs(attempt) + MARGIN_MS)
        }

    // The tile host [page] loads with, read through its bridge getter.
    private fun tileHostOf(page: WebView): String = bridgeGetter(page, "tileHost")

    // The colour scheme a Google [page] is built with, read the same way.
    private fun colorSchemeOf(page: WebView): String = bridgeGetter(page, "googleMapsColorScheme")

    private fun bridgeGetter(
        page: WebView,
        name: String,
    ): String {
        val bridge = bridgeOf(page)
        return bridge.javaClass.getMethod(name).invoke(bridge) as String
    }

    // A renderer death as the platform reports it, through the page's client.
    private fun killRenderer(page: WebView) {
        // Reached past onRenderProcessGone, whose detail object apps may not
        // construct.
        (shadowOf(page).webViewClient as LiveMapWebViewClient).onRendererGone(page, crashed = true)
        settle()
    }

    // The clock renderer containment reads (SystemClock); the compose test
    // clock does not move it.
    private fun advanceSystemClock(ms: Long) {
        ShadowSystemClock.advanceBy(Duration.ofMillis(ms))
        settle()
    }

    private fun setMapConfig(config: MapConfig) {
        mapConfig.value = config
        settle()
    }

    private fun setLifecycle(state: Lifecycle.State) {
        host.moveTo(state)
        settle()
    }

    private fun setOnline(value: Boolean) {
        online.value = value
        settle()
    }

    private fun advanceBy(ms: Long) {
        rule.mainClock.advanceTimeBy(ms)
        settle()
    }

    // Runs the bridge's main-thread posts and a few frames: one to recompose
    // on the new state, one for the effects that recomposition starts, and
    // one for the state those effects write.
    private fun settle() {
        repeat(SETTLE_FRAMES) {
            shadowOf(Looper.getMainLooper()).idle()
            rule.mainClock.advanceTimeByFrame()
        }
        shadowOf(Looper.getMainLooper()).idle()
    }
}

private fun View.webViews(): List<WebView> =
    when (this) {
        is WebView -> listOf(this)
        is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).webViews() }
        else -> emptyList()
    }

private val NetworkFailure = NetworkFailureDetails.first()

// A rejected Google key: a bounded failure, retried only within the budget.
private val GoogleAuthFailure = BoundedFailureDetails.first()
private val GoogleMapsConfig = MapConfig(backend = MapBackend.GOOGLEMAPS, googleMapsApiKey = "test-key")
private const val OVERRIDE_TILE_HOST = "https://tiles.example.test"

// Both schemes on the user's hosted style, in the light context.
private fun customStyleConfig(url: String) =
    MapConfig(
        style = MapStyleSetting.LIGHT,
        schemeLight = MapColorScheme.CUSTOM,
        schemeDark = MapColorScheme.CUSTOM,
        customStyleUrl = url,
    )

private const val FIRST_STYLE_URL = "https://styles.example.test/first/style.json"
private const val SECOND_STYLE_URL = "https://styles.example.test/second/style.json"

// Enough failed pages in a row to put the backoff at its cap.
private const val OUTAGE_STEPS = 5
private const val TEN_MINUTES_MS = 10 * 60_000L

// Wide enough to cover the frames each settle() runs, far below any backoff step.
private const val MARGIN_MS = 500L
private const val SETTLE_FRAMES = 3

// The compose test clock's frame, and half of it.
private const val FRAME_MS = 16L
private const val HALF_FRAME_MS = FRAME_MS / 2
