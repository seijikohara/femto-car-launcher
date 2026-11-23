package io.github.seijikohara.femto.data.routing

/**
 * The target speed, in km/h, every search runs with. car-vario caps every road at its target speed, 90 km/h unless
 * told otherwise, which understates motorways where the limit is higher. On test routes in Japan, Portugal, Spain and
 * the United States, 90 and 100 kept a route off the expressway most drivers take, 110 matched typical travel times,
 * and 120 or more came out optimistic.
 */
internal const val ROUTE_TARGET_SPEED_KMH = 110

/** What the driver asks of a route: the roads to avoid. The default asks nothing. */
internal data class RouteConditions(
    /**
     * Make motorways and their links about ten times as costly: a route uses one only where no fair way round exists.
     */
    val avoidMotorways: Boolean = false,
    /** Never use a toll road or pass a toll booth: a destination only a toll road reaches gets no route. */
    val avoidTolls: Boolean = false,
) {
    val isDefault: Boolean get() = !avoidMotorways && !avoidTolls
}

/**
 * The car-vario profile parameters for these conditions, as BRouter takes them: the target speed always, and each
 * condition only when it is set. BRouter reads a boolean parameter as `1` or `0`; `true` makes the profile fail to
 * parse.
 */
internal fun RouteConditions.engineParameters(): Map<String, String> =
    buildMap {
        put(TARGET_SPEED_PARAMETER, "$ROUTE_TARGET_SPEED_KMH")
        if (avoidMotorways) put(AVOID_MOTORWAYS_PARAMETER, ENGINE_TRUE)
        if (avoidTolls) put(AVOID_TOLLS_PARAMETER, ENGINE_TRUE)
    }

private const val TARGET_SPEED_PARAMETER = "vmax"
private const val AVOID_MOTORWAYS_PARAMETER = "avoid_motorways"
private const val AVOID_TOLLS_PARAMETER = "avoid_toll"
private const val ENGINE_TRUE = "1"
