package io.github.seijikohara.femto.data.routing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.display.DisplaySettings
import io.github.seijikohara.femto.data.display.MapBackend
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RoutingAvailabilityTest {
    @Test
    fun `the stable build the unit tests run on does not offer routing`() {
        val context: Context = ApplicationProvider.getApplicationContext()

        assertFalse(context.resources.getBoolean(R.bool.routing_enabled))
    }

    @Test
    fun `a build that enables routing offers it on the OpenStreetMap backend`() =
        runTest {
            assertEquals(
                true,
                routingAvailableFlow(enabled = true, settings = flowOf(settings(MapBackend.OSM))).first(),
            )
        }

    @Test
    fun `the Google backend never offers routing`() =
        runTest {
            assertEquals(
                false,
                routingAvailableFlow(enabled = true, settings = flowOf(settings(MapBackend.GOOGLEMAPS))).first(),
            )
        }

    @Test
    fun `a build that does not enable routing never offers it`() =
        runTest {
            assertEquals(
                false,
                routingAvailableFlow(enabled = false, settings = flowOf(settings(MapBackend.OSM))).first(),
            )
        }

    private fun settings(backend: MapBackend): DisplaySettings = DisplaySettings.Default.copy(mapBackend = backend)
}
