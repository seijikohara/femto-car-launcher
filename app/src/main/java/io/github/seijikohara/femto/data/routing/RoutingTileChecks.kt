package io.github.seijikohara.femto.data.routing

/**
 * Whether a tile last checked against the server at [checkedAtEpochMs] is due for another check at [nowEpochMs]: never
 * checked, or checked [ROUTING_TILE_CHECK_INTERVAL_MS] ago or longer. A check dated after [nowEpochMs] is due too, so
 * a clock set back cannot hold a tile unchecked for longer than the interval.
 */
internal fun isCheckDue(
    checkedAtEpochMs: Long?,
    nowEpochMs: Long,
): Boolean = checkedAtEpochMs == null || nowEpochMs - checkedAtEpochMs !in 0 until ROUTING_TILE_CHECK_INTERVAL_MS

/**
 * The tiles among [needed] (the tiles a route may read) that are [installed] and due for their weekly check at
 * [nowEpochMs], in the order of [needed]. A route asks the server about these before it is computed, so the routing
 * data is refreshed only where the driver routes, and never by a job in the background.
 */
internal fun tilesDueForCheck(
    needed: List<String>,
    installed: List<InstalledRoutingTile>,
    nowEpochMs: Long,
): List<String> {
    val checkedAt = installed.associate { it.name to it.checkedAtEpochMs }
    return needed.filter { name -> name in checkedAt && isCheckDue(checkedAt[name], nowEpochMs) }
}
