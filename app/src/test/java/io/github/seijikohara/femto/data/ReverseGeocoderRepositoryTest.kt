@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.seijikohara.femto.data

import android.location.Address
import android.location.Geocoder
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import io.github.seijikohara.femto.testfixtures.fakeLocation
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReverseGeocoderRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `emits short address when geocoder returns full result`() =
        runTest {
            val geocoder = Geocoder(context)
            val response =
                Address(java.util.Locale.US).apply {
                    locality = "Shibuya"
                    adminArea = "Tokyo"
                }
            shadowOf(geocoder).setFromLocation(listOf(response))

            val repo =
                ReverseGeocoderRepository(
                    context = context,
                    locationFlow = flowOf(fakeLocation()),
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                    geocoder = geocoder,
                )

            repo.addressFlow().test {
                assertEquals(ShortAddress("Shibuya", "Tokyo"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `emits null when geocoder returns no result`() =
        runTest {
            val geocoder = Geocoder(context)
            shadowOf(geocoder).setFromLocation(emptyList())

            val repo =
                ReverseGeocoderRepository(
                    context = context,
                    locationFlow = flowOf(fakeLocation()),
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                    geocoder = geocoder,
                )

            repo.addressFlow().test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `emits null when location flow yields null`() =
        runTest {
            val repo =
                ReverseGeocoderRepository(
                    context = context,
                    locationFlow = flowOf(null),
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )

            repo.addressFlow().test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
