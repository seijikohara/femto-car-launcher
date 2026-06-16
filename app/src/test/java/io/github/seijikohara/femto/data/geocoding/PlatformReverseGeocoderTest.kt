package io.github.seijikohara.femto.data.geocoding

import android.location.Address
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlatformReverseGeocoderTest {
    @Test
    fun `maps locality, region, and the formatted line`() {
        val address =
            Address(Locale.JAPAN).apply {
                locality = "新宿区"
                adminArea = "東京都"
                setAddressLine(0, "東京都新宿区新宿三丁目")
            }

        val short = address.toShortAddressOrNull()

        assertEquals("新宿区", short?.locality)
        assertEquals("東京都", short?.region)
        assertEquals("東京都新宿区新宿三丁目", short?.displayString())
    }

    @Test
    fun `falls through to sub-locality when locality is absent`() {
        val address = Address(Locale.getDefault()).apply { subLocality = "Shibuya" }

        assertEquals("Shibuya", address.toShortAddressOrNull()?.locality)
    }

    @Test
    fun `drops the region when it duplicates the locality`() {
        // Only the admin area is known: it supplies the locality, so repeating it
        // as the region would be redundant.
        val address = Address(Locale.JAPAN).apply { adminArea = "東京都" }

        val short = address.toShortAddressOrNull()

        assertEquals("東京都", short?.locality)
        assertNull(short?.region)
    }

    @Test
    fun `returns null when no place name is present`() {
        assertNull(Address(Locale.getDefault()).toShortAddressOrNull())
    }
}
