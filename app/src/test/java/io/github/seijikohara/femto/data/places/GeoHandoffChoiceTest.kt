package io.github.seijikohara.femto.data.places

import org.junit.Test
import kotlin.test.assertEquals

/**
 * Every branch of the rule that keeps the launcher's own `geo:` hand-off away
 * from the launcher, which takes `geo:` links itself: a resolved default counts
 * only when it is among the other apps' handlers, so neither the system's
 * resolver nor the launcher's own entrance is ever started as a default.
 */
class GeoHandoffChoiceTest {
    private fun choice(
        handlers: List<AppActivity>,
        resolved: AppActivity?,
    ): GeoHandoffChoice =
        object : GeoHandlerLookup {
            override fun handlers() = handlers

            override fun resolvedOrNull() = resolved
        }.geoHandoffChoice(ownPackage = OWN_PACKAGE)

    @Test
    fun `no handler at all is no hand-off`() {
        assertEquals(GeoHandoffChoice.NoHandler, choice(handlers = emptyList(), resolved = null))
    }

    @Test
    fun `the launcher alone is no hand-off`() {
        assertEquals(GeoHandoffChoice.NoHandler, choice(handlers = listOf(ENTRANCE), resolved = ENTRANCE))
    }

    @Test
    fun `one other app is started directly though the system would ask`() {
        assertEquals(GeoHandoffChoice.Direct(MAPS), choice(handlers = listOf(ENTRANCE, MAPS), resolved = RESOLVER))
    }

    @Test
    fun `a default among several is started directly`() {
        assertEquals(
            GeoHandoffChoice.Direct(NAVIGATION),
            choice(handlers = listOf(ENTRANCE, MAPS, NAVIGATION), resolved = NAVIGATION),
        )
    }

    @Test
    fun `the system resolver among several becomes a chooser without the launcher`() {
        assertEquals(
            GeoHandoffChoice.Chooser(excluded = listOf(ENTRANCE)),
            choice(handlers = listOf(ENTRANCE, MAPS, NAVIGATION), resolved = RESOLVER),
        )
    }

    @Test
    fun `the launcher chosen as the default among several becomes a chooser without it`() {
        assertEquals(
            GeoHandoffChoice.Chooser(excluded = listOf(ENTRANCE)),
            choice(handlers = listOf(ENTRANCE, MAPS, NAVIGATION), resolved = ENTRANCE),
        )
    }

    @Test
    fun `the launcher chosen as the default beside one other app starts the other`() {
        assertEquals(GeoHandoffChoice.Direct(MAPS), choice(handlers = listOf(ENTRANCE, MAPS), resolved = ENTRANCE))
    }

    private companion object {
        const val OWN_PACKAGE = "io.github.seijikohara.femto"
        val ENTRANCE = AppActivity(OWN_PACKAGE, "$OWN_PACKAGE.ui.destination.ReceiveDestinationActivity")
        val MAPS = AppActivity("com.example.maps", "com.example.maps.MapActivity")
        val NAVIGATION = AppActivity("com.example.navigation", "com.example.navigation.RouteActivity")
        val RESOLVER = AppActivity("android", "com.android.internal.app.ResolverActivity")
    }
}
