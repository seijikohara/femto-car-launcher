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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import kotlin.test.assertSame

/**
 * Wiring tests for [WebMapView]'s reload schedule: a failed page reloads on
 * the backoff while the launcher is visible, never while it is hidden, and
 * once at once on its return; an offline->online edge that arrives while
 * hidden reloads on the return too. The page is the real WebMapView over a
 * Robolectric WebView: a fatal arrives through the registered `femtoBridge`
 * the way the page sends it, the failure notice replaces the WebView, and a
 * reload shows as a new WebView instance.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WebMapViewReloadTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val host = TestLifecycleOwner()
    private val online = mutableStateOf(true)

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

    @Test fun `a hidden launcher holds the retry and reloads at once on its return`() {
        showMap()
        reportFatal(NetworkFailure)
        setLifecycle(Lifecycle.State.CREATED)
        advanceBy(TEN_MINUTES_MS)
        assertEquals(0, pages().size, "no reload while hidden")
        setLifecycle(Lifecycle.State.RESUMED)
        assertEquals(1, pages().size, "one reload at once on the return")
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
        assertEquals(1, pages().size)
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

    private fun showMap(onlineAtStart: Boolean = true) {
        online.value = onlineAtStart
        host.registry.currentState = Lifecycle.State.RESUMED
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides host) {
                FemtoTheme {
                    WebMapView(
                        location = Location("test"),
                        mapConfig = MapConfig(),
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

    // A `fatal` as the page sends it: through the bridge object the host
    // registered on the current WebView.
    private fun reportFatal(detail: String) {
        val bridge = shadowOf(page()).getJavascriptInterface("femtoBridge")
        bridge.javaClass
            .getMethod("onMapEvent", String::class.java, String::class.java)
            .invoke(bridge, "fatal", detail)
        settle()
    }

    private fun setLifecycle(state: Lifecycle.State) {
        host.registry.currentState = state
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

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }
}

private fun View.webViews(): List<WebView> =
    when (this) {
        is WebView -> listOf(this)
        is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).webViews() }
        else -> emptyList()
    }

private val NetworkFailure = NetworkFailureDetails.first()
private const val TEN_MINUTES_MS = 10 * 60_000L

// Wide enough to cover the frames each settle() runs, far below any backoff step.
private const val MARGIN_MS = 500L
private const val SETTLE_FRAMES = 3
