package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.location.PersistedTrip
import io.github.seijikohara.femto.data.location.TripStateStore

/**
 * In-memory [TripStateStore]: [read] serves [stored], [write] records every
 * snapshot so tests can assert both the final state and the write cadence.
 */
internal class FakeTripStateStore(
    initial: PersistedTrip = PersistedTrip.Initial,
) : TripStateStore {
    var stored: PersistedTrip = initial
        private set

    val writes: MutableList<PersistedTrip> = mutableListOf()

    override suspend fun read(): PersistedTrip = stored

    override suspend fun write(value: PersistedTrip) {
        stored = value
        writes += value
    }
}
