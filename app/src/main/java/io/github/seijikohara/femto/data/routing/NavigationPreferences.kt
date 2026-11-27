package io.github.seijikohara.femto.data.routing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.seijikohara.femto.data.common.catchIoAsDefaults
import io.github.seijikohara.femto.data.common.editOrLog
import io.github.seijikohara.femto.data.common.toEnumOr
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG = "NavigationPreferences"

/** Which time the speed readout's route row shows beside the distance left. */
internal enum class RouteRowTime {
    /** The time of day the vehicle arrives. */
    ARRIVAL,

    /** The travel time left. */
    REMAINING,
}

/**
 * The Navigation settings (issue #389): which parts of the route preview run besides the line on the map, which time
 * its row shows, whether the map draws the vehicle on the route line while it drives along it, what the turn card
 * over the map shows, and whether the launcher speaks it. Each of the first four defaults is what the route preview
 * did before these settings existed, so a driver who never opens them sees no change there; the vehicle is drawn on
 * the line, and the turn card shows with the turn after next but without "continue straight", unless the driver
 * switches them.
 */
internal data class NavigationSettings(
    val rerouteAutomatically: Boolean,
    val suggestOtherRoutes: Boolean,
    val restoreRouteAfterRestart: Boolean,
    val routeRowTime: RouteRowTime,
    val vehicleOnRouteLine: Boolean,
    /** Whether the turn card shows the next turn over the map. */
    val turnCard: Boolean,
    /** Whether the turn card shows the turn after next when it follows closely. */
    val turnCardNextAfter: Boolean,
    /** Whether the turn card also announces "continue straight". */
    val turnCardStraight: Boolean,
    /** Whether the launcher speaks the turn card's content through the text-to-speech engine. */
    val spokenPrompts: Boolean,
) {
    companion object {
        val Default =
            NavigationSettings(
                rerouteAutomatically = true,
                suggestOtherRoutes = true,
                restoreRouteAfterRestart = true,
                routeRowTime = RouteRowTime.ARRIVAL,
                vehicleOnRouteLine = true,
                turnCard = true,
                turnCardNextAfter = true,
                turnCardStraight = false,
                spokenPrompts = true,
            )
    }
}

/**
 * Read/write surface for [NavigationSettings]. [NavigationPreferences] is the DataStore-backed production
 * implementation; tests substitute an in-memory fake.
 */
internal interface NavigationSettingsStore {
    val settings: Flow<NavigationSettings>

    suspend fun setRerouteAutomatically(value: Boolean)

    suspend fun setSuggestOtherRoutes(value: Boolean)

    suspend fun setRestoreRouteAfterRestart(value: Boolean)

    suspend fun setRouteRowTime(value: RouteRowTime)

    suspend fun setVehicleOnRouteLine(value: Boolean)

    suspend fun setTurnCard(value: Boolean)

    suspend fun setTurnCardNextAfter(value: Boolean)

    suspend fun setTurnCardStraight(value: Boolean)

    suspend fun setSpokenPrompts(value: Boolean)

    suspend fun resetToDefaults()
}

// Choices about what the route preview does, not about places, so the file is backed up like the other settings.
internal const val NAVIGATION_STORE_NAME = "navigation_preferences"

// Internal (not private): NavigationPreferencesTest clears the raw store between tests.
internal val Context.navigationDataStore: DataStore<Preferences> by preferencesDataStore(name = NAVIGATION_STORE_NAME)

/** DataStore-backed accessor for [NavigationSettings]. Modelled on `VideoPreferences`. */
internal class NavigationPreferences(
    private val context: Context,
) : NavigationSettingsStore {
    override val settings: Flow<NavigationSettings> =
        context.navigationDataStore.data
            .catchIoAsDefaults(TAG)
            .map { prefs ->
                NavigationSettings(
                    rerouteAutomatically = prefs[REROUTE_KEY] ?: NavigationSettings.Default.rerouteAutomatically,
                    suggestOtherRoutes = prefs[OTHER_ROUTES_KEY] ?: NavigationSettings.Default.suggestOtherRoutes,
                    restoreRouteAfterRestart =
                        prefs[RESTORE_KEY] ?: NavigationSettings.Default.restoreRouteAfterRestart,
                    // A name a later build wrote, and this one does not know, reads as the default.
                    routeRowTime = prefs[ROUTE_ROW_TIME_KEY].toEnumOr(NavigationSettings.Default.routeRowTime),
                    vehicleOnRouteLine = prefs[VEHICLE_ON_LINE_KEY] ?: NavigationSettings.Default.vehicleOnRouteLine,
                    turnCard = prefs[TURN_CARD_KEY] ?: NavigationSettings.Default.turnCard,
                    turnCardNextAfter = prefs[TURN_CARD_NEXT_AFTER_KEY] ?: NavigationSettings.Default.turnCardNextAfter,
                    turnCardStraight = prefs[TURN_CARD_STRAIGHT_KEY] ?: NavigationSettings.Default.turnCardStraight,
                    spokenPrompts = prefs[SPOKEN_PROMPTS_KEY] ?: NavigationSettings.Default.spokenPrompts,
                )
            }

    override suspend fun setRerouteAutomatically(value: Boolean) {
        context.navigationDataStore.editOrLog(TAG) { it[REROUTE_KEY] = value }
    }

    override suspend fun setSuggestOtherRoutes(value: Boolean) {
        context.navigationDataStore.editOrLog(TAG) { it[OTHER_ROUTES_KEY] = value }
    }

    override suspend fun setRestoreRouteAfterRestart(value: Boolean) {
        context.navigationDataStore.editOrLog(TAG) { it[RESTORE_KEY] = value }
    }

    override suspend fun setRouteRowTime(value: RouteRowTime) {
        context.navigationDataStore.editOrLog(TAG) { it[ROUTE_ROW_TIME_KEY] = value.name }
    }

    override suspend fun setVehicleOnRouteLine(value: Boolean) {
        context.navigationDataStore.editOrLog(TAG) { it[VEHICLE_ON_LINE_KEY] = value }
    }

    override suspend fun setTurnCard(value: Boolean) {
        context.navigationDataStore.editOrLog(TAG) { it[TURN_CARD_KEY] = value }
    }

    override suspend fun setTurnCardNextAfter(value: Boolean) {
        context.navigationDataStore.editOrLog(TAG) { it[TURN_CARD_NEXT_AFTER_KEY] = value }
    }

    override suspend fun setTurnCardStraight(value: Boolean) {
        context.navigationDataStore.editOrLog(TAG) { it[TURN_CARD_STRAIGHT_KEY] = value }
    }

    override suspend fun setSpokenPrompts(value: Boolean) {
        context.navigationDataStore.editOrLog(TAG) { it[SPOKEN_PROMPTS_KEY] = value }
    }

    // Every key here is a setting, and the read path falls back to NavigationSettings.Default for a missing one, so
    // clearing the file restores the defaults without repeating them.
    override suspend fun resetToDefaults() {
        context.navigationDataStore.editOrLog(TAG) { it.clear() }
    }

    private companion object {
        val REROUTE_KEY = booleanPreferencesKey("reroute_automatically")
        val OTHER_ROUTES_KEY = booleanPreferencesKey("suggest_other_routes")
        val RESTORE_KEY = booleanPreferencesKey("restore_route_after_restart")
        val ROUTE_ROW_TIME_KEY = stringPreferencesKey("route_row_time")
        val VEHICLE_ON_LINE_KEY = booleanPreferencesKey("vehicle_on_route_line")
        val TURN_CARD_KEY = booleanPreferencesKey("turn_card")
        val TURN_CARD_NEXT_AFTER_KEY = booleanPreferencesKey("turn_card_next_after")
        val TURN_CARD_STRAIGHT_KEY = booleanPreferencesKey("turn_card_straight")
        val SPOKEN_PROMPTS_KEY = booleanPreferencesKey("spoken_prompts")
    }
}
