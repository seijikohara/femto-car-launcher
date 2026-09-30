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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.seijikohara.femto.BuildConfig
import io.github.seijikohara.femto.data.display.MapBackend
import io.github.seijikohara.femto.data.map.MapRuntimeSignals
import io.github.seijikohara.femto.testfixtures.BoundedFailureDetails
import io.github.seijikohara.femto.testfixtures.FakeLifecycleOwner
import io.github.seijikohara.femto.testfixtures.NetworkFailureDetails
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
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
        val bridge = bridgeOf(page)
        bridge.javaClass
            .getMethod("onMapEvent", String::class.java, String::class.java)
            .invoke(bridge, kind, detail)
        settle()
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
    private fun tileHostOf(page: WebView): String {
        val bridge = bridgeOf(page)
        return bridge.javaClass.getMethod("tileHost").invoke(bridge) as String
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

// Enough failed pages in a row to put the backoff at its cap.
private const val OUTAGE_STEPS = 5
private const val TEN_MINUTES_MS = 10 * 60_000L

// Wide enough to cover the frames each settle() runs, far below any backoff step.
private const val MARGIN_MS = 500L
private const val SETTLE_FRAMES = 3
