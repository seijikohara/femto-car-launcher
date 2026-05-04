package io.github.seijikohara.femto.ui.locale

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SystemUnitsTest {
    @Test
    fun `us country defaults to imperial speed and distance`() {
        assertEquals(SpeedUnit.MILES_PER_HOUR, speedUnitFor(Locale.US))
        assertEquals(DistanceUnit.FEET, distanceUnitFor(Locale.US))
    }

    @Test
    fun `gb country defaults to imperial speed and distance`() {
        val gb = Locale
            .Builder()
            .setLanguage("en")
            .setRegion("GB")
            .build()
        assertEquals(SpeedUnit.MILES_PER_HOUR, speedUnitFor(gb))
        assertEquals(DistanceUnit.FEET, distanceUnitFor(gb))
    }

    @Test
    fun `mm country defaults to imperial speed and distance`() {
        val mm = Locale
            .Builder()
            .setLanguage("my")
            .setRegion("MM")
            .build()
        assertEquals(SpeedUnit.MILES_PER_HOUR, speedUnitFor(mm))
        assertEquals(DistanceUnit.FEET, distanceUnitFor(mm))
    }

    @Test
    fun `jp country defaults to metric speed and distance`() {
        assertEquals(SpeedUnit.KILOMETERS_PER_HOUR, speedUnitFor(Locale.JAPAN))
        assertEquals(DistanceUnit.METERS, distanceUnitFor(Locale.JAPAN))
    }

    @Test
    fun `de country defaults to metric speed and distance`() {
        assertEquals(SpeedUnit.KILOMETERS_PER_HOUR, speedUnitFor(Locale.GERMANY))
        assertEquals(DistanceUnit.METERS, distanceUnitFor(Locale.GERMANY))
    }

    @Test
    fun `unknown country defaults to metric`() {
        val xx = Locale
            .Builder()
            .setLanguage("xx")
            .setRegion("XX")
            .build()
        assertEquals(SpeedUnit.KILOMETERS_PER_HOUR, speedUnitFor(xx))
        assertEquals(DistanceUnit.METERS, distanceUnitFor(xx))
    }
}
