package io.github.seijikohara.femto.data.routing

import btools.router.FormatJson
import btools.router.OsmTrack
import btools.router.RoutingContext
import btools.router.RoutingEngine
import btools.router.RoutingParamCollector
import io.github.seijikohara.femto.data.common.ATOMIC_WRITE_TEMP_SUFFIX
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File

private const val TURN_INSTRUCTION_MODE = 2

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
    private val alternativeIndex = request.alternativeIndex

    private val routingContext =
        RoutingContext().apply {
            // lookups.dat must sit beside the profile: BRouter reads it from the profile's directory.
            localFunction = request.profile.path
            memoryclass = request.memoryClassMb
            // The profile is parsed with these in place of its own values. They also enter the profile's
            // checksum, so a reference written under other parameters is refused and the search runs in full.
            if (request.parameters.isNotEmpty()) keyValues = HashMap(request.parameters)
            // Set on the field and not through keyValues: the mode changes nothing about the route, so it stays out
            // of the profile's checksum, which then still says only what the driver asked of the route. Mode 2 is
            // one of the two that name a motorway exit (commands 17 and 18) where the others fold it into keep left
            // and keep right, and its rows are always five numbers.
            turnInstructionMode = TURN_INSTRUCTION_MODE
            // BRouter's fast partial recalculation: a search to the reference's destination stops where it
            // rejoins the reference. BRouter checks the destination and the profile's checksum itself.
            rawTrackPath = request.referenceIn?.path
            // BRouter's own "heading" parameter sets the two together; the direction alone only counts on the
            // fast path.
            request.startHeadingDegrees?.let { heading ->
                startDirection = heading
                forceUseStartDirection = true
            }
            setAlternativeIdx(request.alternativeIndex)
        }

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
            routingContext,
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
                    turns = turnsOrNull(track),
                )
            }
            ?: EngineOutcome.Error("no track found")
    }

    override fun terminate() = engine.terminate()

    // The hints are an extra: a formatter that throws leaves the route without them. A profile that assigns a mode
    // of its own replaces the one set above while the engine is constructed (only the value 1, "automatic", which
    // the shipped profile assigns, leaves it alone). With the hints switched off that way the text has no hint to
    // read, which must not pass for a route without a turn.
    private fun turnsOrNull(track: OsmTrack): List<EngineTurn>? =
        track
            .takeIf { routingContext.turnInstructionMode == TURN_INSTRUCTION_MODE }
            ?.let { runCatching { brouterTurnsOrNull(FormatJson(routingContext).format(it)) }.getOrNull() }

    // A reference only saves time on the next search, so one that cannot be written is left out rather than failing
    // the route. The rename keeps a half-written file from ever standing as a reference.
    private fun writeReference(target: File) {
        val raw = engine.foundRawTrack ?: return
        val reference = if (alternativeIndex == 0) raw else alternativeReference(raw)
        val temp = File(target.parentFile, target.name + ATOMIC_WRITE_TEMP_SUFFIX)
        runCatching {
            reference.writeBinary(temp.path)
            check(temp.renameTo(target)) { "could not rename $temp" }
        }.onFailure { temp.delete() }
    }

    // For a later alternative, BRouter's raw track is still the best route's, computed first on the way. The found
    // track is the alternative itself; with the raw track's destination, checksums and profile stamp it reads as a
    // reference BRouter accepts, and a reroute beside the alternative stays on its roads.
    private fun alternativeReference(raw: OsmTrack): OsmTrack =
        engine.foundTrack.apply {
            endPoint = raw.endPoint
            nogoChecksums = raw.nogoChecksums
            profileTimestamp = raw.profileTimestamp
            isDirty = false
        }
}

/**
 * The `voicehints` rows of BRouter's JSON for a track; empty when the text has none, null when it cannot be read.
 *
 * BRouter leaves the key out of a track without a hint, so a missing key is no turn and not damaged text. Command
 * numbers are not checked here: [toRouteResult] knows which ones this build has a [TurnKind] for.
 */
internal fun brouterTurnsOrNull(json: String): List<EngineTurn>? =
    runCatching {
        val properties =
            Json
                .parseToJsonElement(json)
                .jsonObject
                .getValue("features")
                .jsonArray
                .first()
                .jsonObject
                .getValue("properties")
                .jsonObject
        // Not an elvis: unreadable rows are null, which must not read as no turn.
        when (val hints = properties["voicehints"]) {
            null -> emptyList()
            else -> turnRowsOrNull(hints.jsonArray)
        }
    }.getOrNull()

// All or nothing: a list with a silent gap would make a later turn card skip a turn. The distance, the fourth field,
// is not kept.
private fun turnRowsOrNull(rows: JsonArray): List<EngineTurn>? =
    rows
        .map { (it as? JsonArray)?.toEngineTurnOrNull() }
        .takeIf { null !in it }
        ?.requireNoNulls()

// Modes other than 2 append a string to the five numbers, which is tolerated.
private fun JsonArray.toEngineTurnOrNull(): EngineTurn? =
    takeIf { size >= 5 }
        ?.let { row -> listOf(0, 1, 2, 4).map { row[it].wholeNumberOrNull() } }
        ?.takeIf { null !in it }
        ?.requireNoNulls()
        ?.let { (positionIndex, command, exitNumber, angleDegrees) ->
            EngineTurn(positionIndex, command, exitNumber, angleDegrees)
        }

// A string such as "5" would pass intOrNull, but BRouter writes these fields as bare numbers.
private fun JsonElement.wholeNumberOrNull(): Int? = (this as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
