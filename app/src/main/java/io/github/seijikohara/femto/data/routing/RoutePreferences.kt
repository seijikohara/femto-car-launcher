package io.github.seijikohara.femto.data.routing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.seijikohara.femto.data.common.catchIoAsDefaults
import io.github.seijikohara.femto.data.common.editOrLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val TAG = "RoutePreferences"

// A choice about roads, not about places, so it is backed up like the other settings.
private val Context.routeDataStore: DataStore<Preferences> by preferencesDataStore(name = "route_preferences")

private val AvoidMotorwaysKey = booleanPreferencesKey("avoid_motorways")
private val AvoidTollsKey = booleanPreferencesKey("avoid_tolls")

/** Remembers the route conditions the driver picked last, for the next route. */
internal interface RouteConditionsStore {
    /** The conditions the next route runs under: none until the driver picks one. */
    val conditions: Flow<RouteConditions>

    suspend fun setConditions(conditions: RouteConditions)
}

internal class RoutePreferences(
    private val context: Context,
) : RouteConditionsStore {
    override val conditions: Flow<RouteConditions> = context.routeDataStore.data.routeConditions()

    override suspend fun setConditions(conditions: RouteConditions) {
        context.routeDataStore.editOrLog(TAG) { prefs ->
            prefs[AvoidMotorwaysKey] = conditions.avoidMotorways
            prefs[AvoidTollsKey] = conditions.avoidTolls
        }
    }
}

/**
 * The route conditions these preferences hold. A file that cannot be read (power lost mid-write) reads as no
 * conditions, so a route still runs, the way it did before any was picked.
 */
internal fun Flow<Preferences>.routeConditions(): Flow<RouteConditions> =
    catchIoAsDefaults(TAG)
        .map { prefs ->
            RouteConditions(
                avoidMotorways = prefs[AvoidMotorwaysKey] ?: false,
                avoidTolls = prefs[AvoidTollsKey] ?: false,
            )
        }.distinctUntilChanged()
