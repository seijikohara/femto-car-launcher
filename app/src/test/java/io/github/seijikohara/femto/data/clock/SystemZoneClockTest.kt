package io.github.seijikohara.femto.data.clock

import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.util.TimeZone
import kotlin.test.assertEquals

/**
 * The dashboard's wall clock must follow a system timezone change while the
 * process lives — a car crossing a border — where `Clock.systemDefaultZone()`
 * freezes the zone it was built with.
 */
class SystemZoneClockTest {
    private lateinit var previousDefault: TimeZone

    @Before
    fun rememberDefault() {
        previousDefault = TimeZone.getDefault()
    }

    @After
    fun restoreDefault() {
        TimeZone.setDefault(previousDefault)
    }

    @Test
    fun `reads the system zone on every query`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        assertEquals(ZoneId.of("UTC"), SystemZoneClock.zone)

        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        assertEquals(ZoneId.of("Asia/Tokyo"), SystemZoneClock.zone)
    }

    @Test
    fun `the JDK system clock freezes the zone it was built with`() {
        // The contrast that motivates SystemZoneClock: proves the trap is real on
        // this JDK rather than assumed.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val frozen = java.time.Clock.systemDefaultZone()

        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        assertEquals(ZoneId.of("UTC"), frozen.zone)
    }
}
