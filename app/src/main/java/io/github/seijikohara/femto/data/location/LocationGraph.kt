package io.github.seijikohara.femto.data.location

import android.content.Context
import android.location.Location
import io.github.seijikohara.femto.data.common.WhileUiSubscribed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * App-scoped owner of the single location + trip pipeline.
 *
 * The launcher's location stack — [LocationRepository] (one platform GPS
 * registration fanned out to every consumer) and [TripRepository] (the running
 * distance / average accumulators) — must be shared by both the dashboard
 * [io.github.seijikohara.femto.ui.home.HomeViewModel] and the background-ranging
 * foreground service. If each built its own instances they would register GPS
 * twice and, worse, keep two independent sets of trip accumulators that diverge.
 * Hoisting them onto a process-lifetime singleton (mirroring `FontRepository`)
 * makes the sharing real.
 *
 * [tripState] is the crux: [TripRepository.stateFlow] is a cold flow whose
 * accrual sequence mutates plain instance fields, an invariant that holds only
 * while a *single* collector walks it. Sharing the cold flow through one
 * [stateIn] here means the UI and the service observe the same hot [StateFlow]
 * with exactly one upstream collector — no double accrual. [WhileUiSubscribed]
 * keeps that upstream (and therefore GPS) hot while *either* the UI or the
 * service subscribes, and lets it stop once neither does (preserving the
 * foreground-only behaviour when background ranging is off).
 */
internal class LocationGraph private constructor(
    context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val preferences: LocationSettingsStore = LocationPreferences(context)

    private val locationRepository = LocationRepository(context, preferences.settings)

    private val tripRepository = TripRepository(locationRepository.locationFlow())

    fun locationFlow(): Flow<Location?> = locationRepository.locationFlow()

    val tripState: StateFlow<TripState> =
        tripRepository.stateFlow().stateIn(scope, WhileUiSubscribed, TripState.Initial)

    /** Whether the user has opted into background ranging (the foreground-service toggle). */
    val backgroundRangingEnabled: Flow<Boolean> =
        preferences.settings.map { it.backgroundRangingEnabled }.distinctUntilChanged()

    fun resetTrip() = tripRepository.reset()

    companion object {
        @Volatile
        private var instance: LocationGraph? = null

        fun get(context: Context): LocationGraph =
            instance ?: synchronized(this) {
                instance ?: LocationGraph(context.applicationContext).also { instance = it }
            }
    }
}
