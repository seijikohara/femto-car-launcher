package io.github.seijikohara.femto.data.routing

import io.github.seijikohara.femto.testfixtures.shippedAssetFile
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals

/**
 * BRouter itself, with the shipped lookup table and profile, on an empty segment directory. The search ends at the
 * first tile the engine cannot open, which is the one under the origin: BRouter matches the points to roads in order.
 * So the profile must parse, the coordinates must reach the engine as BRouter reads them, and the engine's message must
 * map to the tile's name.
 */
class BRouterEngineTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `an empty segment directory names the tile under the origin`() {
        assertEquals(RouteResult.MissingTiles(listOf("E130_N30")), search(MATSUYAMA, OSAKA))
    }

    @Test
    fun `a point west and south of zero names its tile from the west and south edges`() {
        assertEquals(RouteResult.MissingTiles(listOf("W60_S35")), search(BUENOS_AIRES, MENDOZA))
    }

    private fun search(
        origin: RoutePoint,
        destination: RoutePoint,
    ): RouteResult {
        val profile = RoutingProfileFiles(tempFolder.newFolder()) { shippedAssetFile(it).inputStream() }.prepare()
        val request = EngineRequest(profile, tempFolder.newFolder(), origin, destination, ENGINE_MEMORY_CLASS_MB)
        return BRouterEngine.prepare(request).run(ROUTE_TIME_LIMIT_MS).toRouteResult()
    }

    private companion object {
        // Each pair spans two tiles, so the tile named tells the origin from the destination.
        val MATSUYAMA = RoutePoint(latitude = 33.84, longitude = 132.77)
        val OSAKA = RoutePoint(latitude = 34.69, longitude = 135.5)
        val BUENOS_AIRES = RoutePoint(latitude = -34.6, longitude = -58.38)
        val MENDOZA = RoutePoint(latitude = -32.89, longitude = -68.84)
    }
}
