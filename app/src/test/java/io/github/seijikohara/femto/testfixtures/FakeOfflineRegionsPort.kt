package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.map.offline.CatalogRegion
import io.github.seijikohara.femto.data.map.offline.OfflineRegionsState
import io.github.seijikohara.femto.data.map.offline.RegionCatalog
import io.github.seijikohara.femto.data.map.offline.RegionCatalogResult
import io.github.seijikohara.femto.data.map.offline.RegionOffer
import io.github.seijikohara.femto.ui.offlinemap.OfflineRegionsPort
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [OfflineRegionsPort]: a test stages the repository's state by writing [state], chooses what the
 * catalogue and each offer answer, and reads back every call that reached the port. [catalogGate] and
 * [offerGate] hold the matching call until a test completes them, so it can look at the sheet while one is under
 * way. Like the repository, the catalogue suggests a region only when it is given a position.
 */
internal class FakeOfflineRegionsPort(
    initial: OfflineRegionsState = OfflineRegionsState(loaded = true),
) : OfflineRegionsPort {
    override val state = MutableStateFlow(initial)

    /** The catalogue the port answers with; null answers that it is unavailable. */
    var catalog: RegionCatalog? = null

    var suggestedId: String? = null

    /** What an offer answers for a region; a failed check unless a test says otherwise. */
    var offerAnswer: (CatalogRegion) -> RegionOffer = {
        RegionOffer.Unavailable(it, RegionOffer.Unavailable.Reason.NETWORK)
    }

    var catalogGate: CompletableDeferred<Unit>? = null

    var offerGate: CompletableDeferred<Unit>? = null

    var loads = 0
        private set

    /** The position of every catalogue request, longitude first. */
    val catalogCalls = mutableListOf<Pair<Double?, Double?>>()

    /** The id of every region an offer was asked for. */
    val offers = mutableListOf<String>()

    val started = mutableListOf<RegionOffer.Ready>()

    var resumes = 0
        private set

    var cancels = 0
        private set

    val deleted = mutableListOf<String>()

    override suspend fun load() {
        loads++
    }

    override suspend fun catalog(
        longitude: Double?,
        latitude: Double?,
    ): RegionCatalogResult {
        catalogCalls += longitude to latitude
        catalogGate?.await()
        return catalog
            ?.let { RegionCatalogResult.Loaded(it, suggestedId.takeIf { longitude != null && latitude != null }) }
            ?: RegionCatalogResult.Unavailable
    }

    override suspend fun offer(region: CatalogRegion): RegionOffer {
        offers += region.id
        offerGate?.await()
        return offerAnswer(region)
    }

    override fun start(offer: RegionOffer.Ready) {
        started += offer
    }

    override fun resume() {
        resumes++
    }

    override fun cancel() {
        cancels++
    }

    override suspend fun delete(regionId: String) {
        deleted += regionId
    }
}
