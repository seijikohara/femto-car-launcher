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

/** BRouter's own default: its Android app and its server both stop a search after 60 s. */
internal const val ROUTE_TIME_LIMIT_MS = 60_000L

/** BRouter's default memory class, in MB, which sizes the engine's caches. */
internal const val ENGINE_MEMORY_CLASS_MB = 64

private const val PROFILES_DIR = "profiles"

/**
 * Routes computed on the device by BRouter from the installed routing tiles.
 *
 * One search runs at a time: BRouter keeps the parsed profile in one static slot, and each search sizes its caches for
 * the whole memory class. A search runs on [engineDispatcher] under [timeLimitMs], and a cancelled caller or the time
 * limit terminates the engine rather than leaving it to run on. The constructor stays injectable for JVM tests;
 * production wiring goes through [get].
 */
internal class RoutePlanner(
    private val tiles: RoutingTileStore,
    private val profiles: RoutingProfileFiles,
    private val engine: RouteEngine = BRouterEngine,
    private val timeLimitMs: Long = ROUTE_TIME_LIMIT_MS,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val engineDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val lock = Mutex()

    /** Compute a route from [origin] to [destination]; the tiles around both must be installed first. */
    suspend fun route(
        origin: RoutePoint,
        destination: RoutePoint,
    ): RouteResult =
        lock.withLock {
            tiles
                .missingTiles(routingTilesAround(origin, destination))
                .takeIf { it.isNotEmpty() }
                ?.let(RouteResult::MissingTiles)
                ?: search(origin, destination).withoutNoDataTiles()
        }

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

    // The time limit runs on the engine's dispatcher, beside the search it bounds.
    private suspend fun search(
        origin: RoutePoint,
        destination: RoutePoint,
    ): RouteResult =
        withContext(engineDispatcher) {
            withTimeoutOrNull(timeLimitMs) {
                try {
                    val profile = withContext(ioDispatcher) { profiles.prepare() }
                    engine
                        .prepare(EngineRequest(profile, tiles.segmentDir, origin, destination, ENGINE_MEMORY_CLASS_MB))
                        .runTerminable()
                        .toRouteResult()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // BRouter throws plain RuntimeExceptions from its constructor (an unreadable profile or lookup
                    // table), and the profile copy throws IOException; each is a failed search, not a crash.
                    RouteResult.Failed(e.message ?: e.toString())
                }
            } ?: RouteResult.TimedOut
        }

    // The search blocks its thread and ignores interrupts; only terminate() stops it, so a cancel calls it and then
    // waits for the search to return before the lock is released.
    private suspend fun EngineSearch.runTerminable(): EngineOutcome =
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

        // Construction touches no file: the profile is copied on the first search.
        fun get(context: Context): RoutePlanner =
            instance ?: synchronized(this) {
                instance ?: context.applicationContext
                    .let { app ->
                        RoutePlanner(
                            tiles = RoutingTileStore.get(app),
                            profiles = RoutingProfileFiles(File(routingDir(app), PROFILES_DIR)) { app.assets.open(it) },
                        )
                    }.also { instance = it }
            }
    }
}
