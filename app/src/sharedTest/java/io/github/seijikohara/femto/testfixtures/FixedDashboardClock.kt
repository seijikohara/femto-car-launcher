package io.github.seijikohara.femto.testfixtures

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Friday 1 May 2026, 10:08 UTC — the one fixed instant behind every dashboard
 * capture and header assertion, so the goldens and the tests agree on the time
 * and the date they show.
 */
internal val FixedDashboardClock: Clock = Clock.fixed(Instant.parse("2026-05-01T10:08:00Z"), ZoneOffset.UTC)
