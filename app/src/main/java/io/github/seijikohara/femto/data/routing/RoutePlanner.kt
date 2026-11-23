package io.github.seijikohara.femto.data.routing

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.math.abs

/** BRouter's own default: its Android app and its server both stop a search after 60 s. */
internal const val ROUTE_TIME_LIMIT_MS = 60_000L

/** BRouter's default memory class, in MB, which sizes the engine's caches. */
internal const val ENGINE_MEMORY_CLASS_MB = 64

private const val PROFILES_DIR = "profiles"

// In the cache directory: the platform may clear it, and a lost reference only costs the next reroute a full search.
private const val REFERENCES_DIR = "routing-references"

/**
 * How long a reference stays safe from [RoutePlanner.pruneReferences] after it was written: a search running beside a
 * prune may just have written the reference its route is about to keep.
 */
internal const val REFERENCE_PRUNE_GRACE_MS = 60_000L

private const val NANOS_PER_MILLI = 1_000_000L

/**
 * How one route search ended and how long [RoutePlanner.route] took, for the diagnostics report. It holds no position
 * and no engine message: a message can name where the route ran.
 */
internal data class RouteRun(
    val outcome: RouteRunOutcome,
    val computingMs: Long,
)

internal enum class RouteRunOutcome {
    FOUND,
    MISSING_TILES,
    NO_ROAD_DATA,
    DATA_VERSION_MISMATCH,
    TIMED_OUT,
    FAILED,
}

internal fun RouteResult.runOutcome(): RouteRunOutcome =
    when (this) {
        is RouteResult.Found -> RouteRunOutcome.FOUND
        is RouteResult.MissingTiles -> RouteRunOutcome.MISSING_TILES
        RouteResult.NoRoadData -> RouteRunOutcome.NO_ROAD_DATA
        RouteResult.DataVersionMismatch -> RouteRunOutcome.DATA_VERSION_MISMATCH
        RouteResult.TimedOut -> RouteRunOutcome.TIMED_OUT
        is RouteResult.Failed -> RouteRunOutcome.FAILED
    }

/**
 * Routes computed on the device by BRouter from the installed routing tiles.
 *
 * One search runs at a time: BRouter keeps the parsed profile in one static slot, and each search sizes its caches for
 * the whole memory class. A search runs on [engineDispatcher] under [timeLimitMs] unless its caller sets a limit of
 * its own, and a cancelled caller or the time limit terminates the engine rather than leaving it to run on.
 *
 * Each found route comes with the engine's reference for it, a file in [referenceDir], which a later search to the
 * same destination hands back to stop where it rejoins that route. The files stay until [pruneReferences]. The
 * constructor stays injectable for JVM tests; production wiring goes through [get].
 */
internal class RoutePlanner(
    private val tiles: RoutingTileStore,
    private val profiles: RoutingProfileFiles,
    private val referenceDir: File,
    private val engine: RouteEngine = BRouterEngine,
    private val timeLimitMs: Long = ROUTE_TIME_LIMIT_MS,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val engineDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val nanoTime: () -> Long = System::nanoTime,
    // The wall clock a reference's file time is compared with when pruning.
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    private val lock = Mutex()

    @Volatile
    private var lastRun: RouteRun? = null

    // Numbers the reference files this process names; read and written only under [lock].
    private var referenceCount = 0L

    /**
     * Compute a route from [origin] to [destination]; the tiles around both must be installed first. A [reference]
     * from an earlier [RouteResult.Found] to the same destination lets the search stop where it rejoins that route,
     * and [startHeadingDegrees] keeps the route from opening with a U-turn. The search stops after [timeLimitMs].
     */
    suspend fun route(
        origin: RoutePoint,
        destination: RoutePoint,
        startHeadingDegrees: Int? = null,
        reference: File? = null,
        timeLimitMs: Long = this.timeLimitMs,
    ): RouteResult =
        lock.withLock {
            val startNanos = nanoTime()
            (
                tiles
                    .missingTiles(routingTilesAround(origin, destination))
                    .takeIf { it.isNotEmpty() }
                    ?.let(RouteResult::MissingTiles)
                    ?: search(origin, destination, startHeadingDegrees, reference, timeLimitMs).withoutNoDataTiles()
            ).also { result ->
                lastRun = RouteRun(result.runOutcome(), computingMs = (nanoTime() - startNanos) / NANOS_PER_MILLI)
            }
        }

    /** How the last route this process computed ended, and how long it took; null before the first one. */
    fun lastRunOrNull(): RouteRun? = lastRun

    /**
     * Delete every reference in the reference directory but those in [keep], the references of the routes still in
     * use, and those written within [REFERENCE_PRUNE_GRACE_MS]. Blocking; a file that cannot be deleted waits for the
     * next prune.
     */
    fun pruneReferences(keep: Collection<File>) =
        referenceDir
            .listFiles()
            .orEmpty()
            .filterNot { it in keep || it.writtenWithinGrace() }
            .forEach { it.delete() }

    // Either side of now: an AI box can boot with a wrong clock that NTP sets later, and a reference dated far ahead of
    // the corrected clock must not stay for good.
    private fun File.writtenWithinGrace(): Boolean = abs(nowEpochMs() - lastModified()) < REFERENCE_PRUNE_GRACE_MS

    // BRouter names the tile under the origin or the destination when it cannot open it, whether the tile is not
    // installed or does not exist, and the check above lets a tile known to have no data through. No download brings
    // such a tile, so a point on it has no road data rather than a missing tile.
    private suspend fun RouteResult.withoutNoDataTiles(): RouteResult =
        when (this) {
            is RouteResult.MissingTiles -> {
                tiles
                    .withoutNoData(names)
                    .takeIf { it.isNotEmpty() }
                    ?.let(RouteResult::MissingTiles)
                    ?: RouteResult.NoRoadData
            }

            else -> {
                this
            }
        }

    // The time limit runs on the engine's dispatcher, beside the search it bounds. Each search names a reference file
    // of its own, so the reference it reads is never the one it writes, and a cancelled search leaves none behind.
    private suspend fun search(
        origin: RoutePoint,
        destination: RoutePoint,
        startHeadingDegrees: Int?,
        reference: File?,
        timeLimitMs: Long,
    ): RouteResult {
        val referenceOut = File(referenceDir, "route-${System.nanoTime()}-${++referenceCount}.bin")
        return try {
            withContext(engineDispatcher) {
                withTimeoutOrNull(timeLimitMs) {
                    try {
                        val profile =
                            withContext(ioDispatcher) {
                                referenceDir.mkdirs()
                                profiles.prepare()
                            }
                        engine
                            .prepare(
                                EngineRequest(
                                    profile = profile,
                                    segmentDir = tiles.segmentDir,
                                    origin = origin,
                                    destination = destination,
                                    memoryClassMb = ENGINE_MEMORY_CLASS_MB,
                                    startHeadingDegrees = startHeadingDegrees,
                                    referenceIn = reference,
                                    referenceOut = referenceOut,
                                ),
                            ).runTerminable(timeLimitMs)
                            .toRouteResult()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // BRouter throws plain RuntimeExceptions from its constructor (an unreadable profile or lookup
                        // table), and the profile copy throws IOException; each is a failed search, not a crash.
                        RouteResult.Failed(e.message ?: e.toString())
                    }
                } ?: RouteResult.TimedOut
            }.let { result -> keepReference(result, referenceOut) }
        } catch (e: CancellationException) {
            referenceOut.delete()
            throw e
        }
    }

    // A found route carries the reference the engine wrote for it, when the file is there; any other outcome
    // deletes what the engine may have written.
    private suspend fun keepReference(
        result: RouteResult,
        referenceOut: File,
    ): RouteResult =
        withContext(ioDispatcher) {
            when (result) {
                is RouteResult.Found -> result.copy(reference = referenceOut.takeIf(File::isFile))
                else -> result.also { referenceOut.delete() }
            }
        }

    // The search blocks its thread and ignores interrupts; only terminate() stops it, so a cancel calls it and then
    // waits for the search to return before the lock is released.
    private suspend fun EngineSearch.runTerminable(timeLimitMs: Long): EngineOutcome =
        coroutineScope {
            val work = async { run(timeLimitMs) }
            try {
                work.await()
            } catch (e: CancellationException) {
                terminate()
                throw e
            }
        }

    companion object {
        @Volatile
        private var instance: RoutePlanner? = null

        // Construction writes no file: the profile is copied, and the reference directory made, on the first search.
        fun get(context: Context): RoutePlanner =
            instance ?: synchronized(this) {
                instance ?: context.applicationContext
                    .let { app ->
                        RoutePlanner(
                            tiles = RoutingTileStore.get(app),
                            profiles = RoutingProfileFiles(File(routingDir(app), PROFILES_DIR)) { app.assets.open(it) },
                            referenceDir = File(app.cacheDir, REFERENCES_DIR),
                        )
                    }.also { instance = it }
            }
    }
}
