package io.github.seijikohara.femto.testfixtures

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A [Clock] the test sets by hand, for time gates that must be probed on both sides of a boundary. */
internal class FakeClock(
    var now: Instant,
    private val zone: ZoneId = ZoneOffset.UTC,
) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = FakeClock(now, zone)

    override fun instant(): Instant = now
}
