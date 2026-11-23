package io.github.seijikohara.femto.data.routing

import btools.router.RoutingContext
import btools.router.RoutingEngine
import btools.router.RoutingParamCollector
import io.github.seijikohara.femto.data.common.ATOMIC_WRITE_TEMP_SUFFIX
import java.io.File

/** The [RouteEngine] that runs BRouter (the `:brouter` module); no other file touches BRouter's types. */
internal object BRouterEngine : RouteEngine {
    override fun prepare(request: EngineRequest): EngineSearch = BRouterSearch(request)
}

/**
 * One BRouter search, set up as BRouter's own Android app and server set theirs up: no output or log files, and
 * `quite` so the engine does not print the track to standard output.
 */
private class BRouterSearch(
    request: EngineRequest,
) : EngineSearch {
    private val referenceOut = request.referenceOut

    // The constructor parses the profile, which BRouter's static ProfileCache keeps for the next search.
    private val engine =
        RoutingEngine(
            null,
            null,
            request.segmentDir,
            RoutingParamCollector().readPositions(
                doubleArrayOf(request.origin.longitude, request.destination.longitude),
                doubleArrayOf(request.origin.latitude, request.destination.latitude),
            ),
            RoutingContext().apply {
                // lookups.dat must sit beside the profile: BRouter reads it from the profile's directory.
                localFunction = request.profile.path
                memoryclass = request.memoryClassMb
                // BRouter's fast partial recalculation: a search to the reference's destination stops where it
                // rejoins the reference. BRouter checks the destination and the profile's checksum itself.
                rawTrackPath = request.referenceIn?.path
                // BRouter's own "heading" parameter sets the two together; the direction alone only counts on the
                // fast path.
                request.startHeadingDegrees?.let { heading ->
                    startDirection = heading
                    forceUseStartDirection = true
                }
            },
            RoutingEngine.BROUTER_ENGINEMODE_ROUTING,
        ).apply { quite = true }

    // doRun reports every failure through getErrorMessage() instead of throwing.
    override fun run(timeLimitMs: Long): EngineOutcome {
        engine.doRun(timeLimitMs)
        return engine.errorMessage?.let(EngineOutcome::Error)
            ?: engine.foundTrack?.let { track ->
                referenceOut?.let(::writeReference)
                EngineOutcome.Track(
                    positions = track.nodes.map { EnginePosition(it.iLon, it.iLat) },
                    distanceMeters = track.distance,
                    durationSeconds = track.totalSeconds,
                )
            }
            ?: EngineOutcome.Error("no track found")
    }

    override fun terminate() = engine.terminate()

    // A reference only saves time on the next search, so one that cannot be written is left out rather than failing
    // the route. The rename keeps a half-written file from ever standing as a reference.
    private fun writeReference(target: File) {
        val raw = engine.foundRawTrack ?: return
        val temp = File(target.parentFile, target.name + ATOMIC_WRITE_TEMP_SUFFIX)
        runCatching {
            raw.writeBinary(temp.path)
            check(temp.renameTo(target)) { "could not rename $temp" }
        }.onFailure { temp.delete() }
    }
}
