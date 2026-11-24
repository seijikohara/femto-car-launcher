package io.github.seijikohara.femto.ui.home.components

import android.icu.text.MeasureFormat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals

// Robolectric for the platform's ICU, which formats the units.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TravelTimeTextTest {
    @Test
    fun `a travel time under an hour reads in minutes, rounded to the nearest`() {
        // 18 min 29 s and 18 min 30 s.
        assertEquals(listOf("18 min", "19 min"), listOf(1_109, 1_110).map { travelTimeText(it, Locale.US) })
    }

    @Test
    fun `a travel time from an hour on reads in hours and minutes, with no zero minutes`() {
        assertEquals(listOf("1 hr, 5 min", "2 hr"), listOf(3_900, 7_200).map { travelTimeText(it, Locale.US) })
    }

    @Test
    fun `a travel time under half a minute still reads as a minute`() {
        assertEquals("1 min", travelTimeText(10, Locale.US))
    }

    // The tile's shorter form, where the short units do not fit it on one line.
    @Test
    fun `the narrow width reads in the narrow units`() {
        assertEquals("1h 5m", travelTimeText(3_900, Locale.US, width = MeasureFormat.FormatWidth.NARROW))
    }
}
