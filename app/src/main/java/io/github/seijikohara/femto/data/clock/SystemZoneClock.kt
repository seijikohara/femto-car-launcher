package io.github.seijikohara.femto.data.clock

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * The wall clock that re-reads the system zone on every query.
 *
 * `Clock.systemDefaultZone()` captures `ZoneId.systemDefault()` once, when it is
 * built; a launcher that outlives a timezone change — a car crossing a border —
 * would keep showing the old zone's time until the process died. [ClockRepository]
 * avoids the same trap with a per-tick zone provider; this is the `Clock`-shaped
 * form for the composables that take a `Clock` (the dashboard header, and the
 * fixed clocks the screenshot tests hand in instead).
 */
internal object SystemZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId): Clock = Clock.system(zone)

    override fun instant(): Instant = Instant.now()
}
