package io.github.seijikohara.femto.data.apps

import org.junit.Test
import kotlin.test.assertEquals

/**
 * Pure ordering / (de)serialization logic behind [RecentAppsPreferences],
 * exercised without Android or DataStore (no [android.graphics.Bitmap], no
 * Robolectric) — the round-trip through the real DataStore is covered
 * separately by [RecentAppsPreferencesTest].
 */
class RecentLaunchOrderingTest {
    @Test
    fun `withRecordedLaunch appends a new component at the front`() =
        assertEquals(
            listOf(RecentLaunch("maps", 100L, 1)),
            emptyList<RecentLaunch>().withRecordedLaunch("maps", 100L),
        )

    @Test
    fun `withRecordedLaunch moves an existing component to the front and increments its count`() {
        val history = listOf(RecentLaunch("maps", 100L, 3), RecentLaunch("music", 200L, 1))

        val updated = history.withRecordedLaunch("maps", 300L)

        assertEquals(listOf(RecentLaunch("maps", 300L, 4), RecentLaunch("music", 200L, 1)), updated)
    }

    @Test
    fun `withRecordedLaunch orders most-recent-first`() {
        val history =
            listOf(RecentLaunch("maps", 100L, 1))
                .withRecordedLaunch("music", 200L)
                .withRecordedLaunch("phone", 300L)

        assertEquals(listOf("phone", "music", "maps"), history.map { it.component })
    }

    @Test
    fun `withRecordedLaunch trims history past RECENT_APPS_MAX_COUNT`() {
        val history =
            (0 until RECENT_APPS_MAX_COUNT + 3).fold(emptyList<RecentLaunch>()) { acc, i ->
                acc.withRecordedLaunch("app$i", i.toLong())
            }

        assertEquals(RECENT_APPS_MAX_COUNT, history.size)
        // The most recently launched survive; the oldest ones are dropped.
        assertEquals("app${RECENT_APPS_MAX_COUNT + 2}", history.first().component)
    }

    @Test
    fun `encode then parseRecentLaunches round-trips the history`() {
        val history = listOf(RecentLaunch("com.example.maps/.Main", 1_700_000_000_000L, 5), RecentLaunch("b/.B", 1L, 0))

        assertEquals(history, parseRecentLaunches(history.encode()))
    }

    @Test
    fun `parseRecentLaunches returns empty for a null or blank raw value`() {
        assertEquals(emptyList(), parseRecentLaunches(null))
        assertEquals(emptyList(), parseRecentLaunches(""))
    }

    @Test
    fun `parseRecentLaunches drops a record that fails to parse and keeps the rest`() {
        val good = RecentLaunch("maps", 100L, 1)
        val raw = "${listOf(good).encode()}\nnot-a-valid-record"

        assertEquals(listOf(good), parseRecentLaunches(raw))
    }
}
