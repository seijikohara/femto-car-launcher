package io.github.seijikohara.femto.data.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.location.Geocoder
import android.os.SystemClock
import androidx.core.content.getSystemService
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.display.DisplayPreferences
import io.github.seijikohara.femto.data.display.GoogleMapsRendering
import io.github.seijikohara.femto.data.display.MapBackend
import io.github.seijikohara.femto.data.map.MapRuntimeSignals
import io.github.seijikohara.femto.data.map.offline.OfflineRegionRepository
import io.github.seijikohara.femto.data.map.offline.OfflineRegionsState
import io.github.seijikohara.femto.data.map.offline.RegionDownload
import io.github.seijikohara.femto.data.map.offline.RegionDownloadFailure
import io.github.seijikohara.femto.data.map.offline.RegionDownloadPhase
import io.github.seijikohara.femto.data.map.offline.TileBenchmarkOutcome
import io.github.seijikohara.femto.data.map.offline.TileBenchmarkResults
import io.github.seijikohara.femto.data.map.offline.TileBenchmarkRow
import io.github.seijikohara.femto.data.routing.ActiveRouteStore
import io.github.seijikohara.femto.data.routing.CandidateFinder
import io.github.seijikohara.femto.data.routing.CandidateSearch
import io.github.seijikohara.femto.data.routing.CandidateSearchEnding
import io.github.seijikohara.femto.data.routing.InstalledRoutingTile
import io.github.seijikohara.femto.data.routing.RerouteStats
import io.github.seijikohara.femto.data.routing.Rerouter
import io.github.seijikohara.femto.data.routing.RouteConditions
import io.github.seijikohara.femto.data.routing.RoutePersistence
import io.github.seijikohara.femto.data.routing.RoutePlanner
import io.github.seijikohara.femto.data.routing.RoutePreferences
import io.github.seijikohara.femto.data.routing.RouteRestoreDecision
import io.github.seijikohara.femto.data.routing.RouteRun
import io.github.seijikohara.femto.data.routing.RouteRunOutcome
import io.github.seijikohara.femto.data.routing.RoutingTileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Locale

// WebGL 2 maps onto OpenGL ES 3.0, so that is the floor below which the map
// backends that need it cannot acquire a context. The Android 13 CDD mandates
// only ES 2.0 ([C-1-1]) and merely strongly-recommends ES 3.1, so a compliant
// device — and an uncertified one all the more — can legitimately sit below it.
private const val WEBGL2_GL_ES_FLOOR = 0x30000

/**
 * What the active map configuration loses without WebGL 2. Verified against each
 * vendor's own documentation rather than inferred:
 *
 *  - maplibre-gl 6 removed the WebGL 1 fallback — it renders nothing without it.
 *  - A Google VECTOR map (a Cloud Map ID) treats WebGL 2 as its bar too, but
 *    silently falls back to raster instead of failing: the map still draws, and
 *    only the vector opt-in (heading-up, tilt, 3D) is lost.
 *  - A Google RASTER map is server-rendered pixel tiles and needs no WebGL.
 */
private enum class WebGl2Need { TO_RENDER, FOR_VECTOR, NONE }

private fun webGl2Need(
    backend: MapBackend,
    googleRendering: GoogleMapsRendering,
    hasGoogleMapId: Boolean,
): WebGl2Need =
    when (backend) {
        MapBackend.OSM -> {
            WebGl2Need.TO_RENDER
        }

        // Mirrors `wantsVector` in webmap/src/backends/googlemaps.ts, which is the
        // page-side home of the same rule: an explicit VECTOR asks for it, AUTO
        // may get it from the Map ID's cloud configuration, and everything else
        // renders raster.
        MapBackend.GOOGLEMAPS -> {
            when (googleRendering) {
                GoogleMapsRendering.VECTOR -> WebGl2Need.FOR_VECTOR
                GoogleMapsRendering.AUTO -> if (hasGoogleMapId) WebGl2Need.FOR_VECTOR else WebGl2Need.NONE
                GoogleMapsRendering.RASTER -> WebGl2Need.NONE
            }
        }
    }

/**
 * Builds the MAP section facts from already-resolved values. Pure so
 * [io.github.seijikohara.femto.data.diagnostics.MapFactsTest] can pin the floor
 * comparison and the failure formatting without a device.
 *
 * Reports what the map DID, not how it is configured: every map setting
 * (backend, style, credentials) is already a SETTINGS fact, and restating it
 * here would be a second home for the same value. [backend], [googleRendering]
 * and [hasGoogleMapId] are read only to judge what a missing WebGL 2 actually
 * costs — flagging it unconditionally would warn about a Google raster map that
 * renders perfectly.
 */
internal fun mapFactsFrom(
    glEsVersion: String?,
    backend: MapBackend,
    googleRendering: GoogleMapsRendering,
    hasGoogleMapId: Boolean,
    lastFailure: MapRuntimeSignals.MapFailure?,
    failureCount: Int,
    nowElapsedRealtimeMs: Long,
    webGlRenderer: String? = null,
    pageFrames: MapRuntimeSignals.PageFrames? = null,
    googleLens: MapRuntimeSignals.GoogleLens? = null,
    tileBenchmark: TileBenchmarkOutcome? = null,
    tileBenchmarkRuns: Int = 0,
    offlineRegions: OfflineRegionsState? = null,
    routing: RoutingFacts? = null,
): List<DiagnosticFact> =
    buildList {
        add(webGl2Fact(glEsVersion, webGl2Need(backend, googleRendering, hasGoogleMapId)))
        add(webGlRendererFact(webGlRenderer))
        add(pageFramesFact(pageFrames, nowElapsedRealtimeMs))
        if (backend == MapBackend.GOOGLEMAPS) add(googleLensFact(googleLens))
        add(lastFailureFact(lastFailure, nowElapsedRealtimeMs))
        if (failureCount > 1) {
            add(DiagnosticFact("Failures this session", FactValue.Text("$failureCount")))
        }
        offlineRegions?.let { addAll(offlineRegionFacts(it)) }
        routing?.let { addAll(routingFacts(it, backend)) }
        addAll(tileBenchmarkFacts(tileBenchmark, tileBenchmarkRuns, nowElapsedRealtimeMs))
    }

/**
 * What the route preview (#389 phase 3) needs to be judged on a head unit: whether the build and backend offer it,
 * the routing data on the device, the last route, the conditions the next one runs under, the reroutes, what became of
 * the route kept across a restart, and the geocoder that text destinations need. [tiles] is null when the build offers
 * no routing, which reads no file.
 */
internal data class RoutingFacts(
    val buildEnabled: Boolean,
    val tiles: List<InstalledRoutingTile>?,
    val lastRun: RouteRun?,
    val geocoderPresent: Boolean,
    val reroutes: RerouteStats? = null,
    val conditions: RouteConditions? = null,
    /** The last search for more routes: each candidate's computing time, and how the search ended. */
    val candidateSearch: CandidateSearch? = null,
    /** What the start of this process did with the route the last one kept; null before it decided anything. */
    val restore: RouteRestoreDecision? = null,
    /** Whether a route is on the map now. */
    val routeActive: Boolean = false,
    /** How many turn hints that route has; null when it has no turn data, which is not the same as none. */
    val activeTurnCount: Int? = null,
)

// Counts, sizes and times only: a tile name says where the owner drives, and an engine message can too.
private fun routingFacts(
    facts: RoutingFacts,
    backend: MapBackend,
): List<DiagnosticFact> =
    buildList {
        add(
            DiagnosticFact(
                "Routing",
                FactValue.Text(
                    when {
                        !facts.buildEnabled -> "off in this build"
                        backend != MapBackend.OSM -> "off (Google Maps backend)"
                        else -> "available"
                    },
                ),
            ),
        )
        facts.tiles?.let { tiles ->
            add(
                DiagnosticFact(
                    "Routing data",
                    FactValue.Text("${tiles.size} tiles, ${tiles.sumOf { it.sizeBytes } / BYTES_PER_MEGABYTE} MB"),
                ),
            )
        }
        if (facts.buildEnabled) {
            add(
                DiagnosticFact(
                    "Last route",
                    FactValue.Text(facts.lastRun?.let { "${it.outcome.label()}, ${it.computingMs} ms" } ?: NO_RUN_YET),
                ),
            )
            // A count and never a position: the report must not say where the route goes. "No turn data" is a route
            // from an earlier build's file or hints the app could not read, which "0 turn hints" is not.
            add(
                DiagnosticFact(
                    "Active route",
                    FactValue.Text(
                        when {
                            !facts.routeActive -> "no route"
                            facts.activeTurnCount == null -> "no turn data"
                            else -> "${facts.activeTurnCount} turn hints"
                        },
                    ),
                ),
            )
            // A failed route reads differently with a road ruled out: avoiding tolls leaves some places unreachable.
            add(DiagnosticFact("Route conditions", FactValue.Text((facts.conditions ?: RouteConditions()).summary())))
            // A later candidate recomputes every one before it, so its time says how much the card costs.
            add(DiagnosticFact("Route candidates", FactValue.Text(facts.candidateSearch?.summary() ?: NO_RUN_YET)))
            add(DiagnosticFact("Last reroute", FactValue.Text(facts.reroutes?.summaryOrNull() ?: NO_RUN_YET)))
            // A route that did or did not come back after a restart reads differently once the reason is known.
            add(DiagnosticFact("Route restore", FactValue.Text(facts.restore?.label() ?: NO_RUN_YET)))
        }
        add(DiagnosticFact("Geocoder", FactValue.Text(if (facts.geocoderPresent) "present" else "absent")))
    }

private const val NO_RUN_YET = "none this session"

private fun RouteRunOutcome.label(): String = name.lowercase().replace('_', ' ')

private fun RouteRestoreDecision.label(): String =
    when (this) {
        RouteRestoreDecision.RESTORED -> "restored"
        RouteRestoreDecision.PARKED_TOO_LONG -> "parked too long, deleted"
        RouteRestoreDecision.NO_SNAPSHOT -> "no route kept"
        RouteRestoreDecision.WAITING_FOR_FIX -> "waiting for a GPS fix"
        RouteRestoreDecision.REPLACED -> "replaced by a new route"
        RouteRestoreDecision.UNDATED -> "no time of the last drive, deleted"
        RouteRestoreDecision.FIX_BEFORE_LAST_DRIVE -> "GPS time before the last drive, deleted"
        RouteRestoreDecision.SWITCHED_OFF -> "off in Settings"
    }

// "3: 0.5 s, 1.1 s, 1.6 s; all found": how many candidates, each one's time in the order they were found, and how
// the search ended.
private fun CandidateSearch.summary(): String =
    timesMs.joinToString(
        prefix = "${timesMs.size}: ",
        postfix = "; ${ending.label()}",
    ) { String.format(Locale.ROOT, "%.1f s", it / MILLIS_PER_SECOND) }

private fun CandidateSearchEnding?.label(): String =
    when (this) {
        null -> "searching"
        CandidateSearchEnding.ALL_FOUND -> "all found"
        CandidateSearchEnding.NO_MORE_ROUTES -> "no more routes"
        CandidateSearchEnding.STOPPED -> "stopped"
    }

private const val MILLIS_PER_SECOND = 1_000.0

private fun RouteConditions.summary(): String =
    listOfNotNull("avoid motorways".takeIf { avoidMotorways }, "avoid tolls".takeIf { avoidTolls })
        .joinToString(", ")
        .ifEmpty { "none" }

// Whether the reroute had a reference tells a slow reroute from a fast one: with it, BRouter stops where the new
// route rejoins the old one. "Offered", since BRouter silently refuses a reference built under other profile
// settings and then searches in full.
private fun RerouteStats.summaryOrNull(): String? =
    last?.let { run ->
        val reference = if (usedReference == true) "reference offered" else "no reference offered"
        "${run.outcome.label()}, ${run.computingMs} ms, $count this session, $reference"
    }

// Counts and sizes only: the choice of regions can say where the owner drives, so no name enters the report.
private fun offlineRegionFacts(state: OfflineRegionsState): List<DiagnosticFact> =
    listOf(
        DiagnosticFact(
            "Saved regions",
            FactValue.Text("${state.saved.size}, ${state.saved.sumOf { it.sizeBytes } / BYTES_PER_MEGABYTE} MB"),
        ),
        DiagnosticFact("Region download", regionDownloadValue(state.download)),
    )

private const val BYTES_PER_MEGABYTE = 1_000_000L

private fun regionDownloadValue(download: RegionDownload?): FactValue =
    when {
        download == null -> {
            FactValue.Text("none")
        }

        download.phase == RegionDownloadPhase.FAILED -> {
            FactValue.Status("failed: ${download.failure.words()}", FactHealth.ERROR)
        }

        download.failure != null -> {
            FactValue.Status("${download.progressText()}: ${download.failure.words()}", FactHealth.WARNING)
        }

        else -> {
            FactValue.Text(download.progressText())
        }
    }

// Long throughout: a region file can exceed 50 GB, which overflows an Int product.
private fun RegionDownload.progressText(): String {
    val percent = if (totalBytes == 0L) 0L else receivedBytes * 100 / totalBytes
    return "${phase.name.lowercase()} $percent%"
}

private fun RegionDownloadFailure?.words(): String = this?.name?.lowercase()?.replace('_', ' ') ?: "unknown"

private fun ms(value: Double): String = "%.1f".format(Locale.ROOT, value)

private fun kb(bytes: Int): Int = (bytes + 512) / 1024

// The nightly benchmark answers whether converting a Shortbread tile per request
// is fast enough on this head unit; the owner reads these rows off the copied report.
private fun tileBenchmarkFacts(
    outcome: TileBenchmarkOutcome?,
    runs: Int,
    nowElapsedRealtimeMs: Long,
): List<DiagnosticFact> =
    when (outcome) {
        null -> {
            emptyList()
        }

        is TileBenchmarkOutcome.Failed -> {
            listOf(DiagnosticFact("Tile conversion", FactValue.Status("failed on ${outcome.sample}", FactHealth.ERROR)))
        }

        is TileBenchmarkOutcome.Aborted -> {
            listOf(
                DiagnosticFact(
                    "Tile conversion",
                    FactValue.Status("did not finish: ${outcome.reason}", FactHealth.ERROR),
                ),
            )
        }

        is TileBenchmarkOutcome.Measured -> {
            val ageS = ((nowElapsedRealtimeMs - outcome.finishedAtElapsedRealtimeMs) / 1_000L).coerceAtLeast(0)
            // The run number tells a first run in the process from a repeat, whose `first` figures are already warm.
            listOf(
                DiagnosticFact(
                    "Tile conversion",
                    FactValue.Text(
                        "run $runs since launch, tiles ${outcome.rows.size}, warm runs ${outcome.warmRuns}, " +
                            "cores ${outcome.cores}, ${ageS}s ago",
                    ),
                ),
            ) + outcome.rows.map(::tileSampleFact) + outcome.rows.map { tileParallelFact(it, outcome) }
        }
    }

private fun tileSampleFact(row: TileBenchmarkRow): DiagnosticFact {
    val raw = kb(row.rawBytes)
    val out = kb(row.outputBytes)
    val first = ms(row.firstMs)
    val median = ms(row.warmMedianMs)
    val p95 = ms(row.warmP95Ms)
    val max = ms(row.warmMaxMs)
    return DiagnosticFact(
        "Tile ${row.sample}",
        FactValue.Text(
            "$raw KB, ${row.features} features \u2192 $out KB; " +
                "first $first ms; median $median ms; p95 $p95 ms; max $max ms",
        ),
    )
}

private fun tileParallelFact(
    row: TileBenchmarkRow,
    outcome: TileBenchmarkOutcome.Measured,
): DiagnosticFact {
    val median = ms(row.parallelMedianMs)
    val max = ms(row.parallelMaxMs)
    return DiagnosticFact(
        "Tile ${row.sample} in parallel",
        FactValue.Text(
            "${outcome.parallelTiles} at once, ${outcome.parallelRounds} rounds: median $median ms; max $max ms",
        ),
    )
}

// The GLES version the platform reports is the capability behind WebGL 2, not a
// direct probe of it: a driver the WebView blocklists can still deny a context
// above the floor. Above the floor the fact therefore says "expected", and the
// authoritative answer is the failure row below it.
private fun webGl2Fact(
    glEsVersion: String?,
    need: WebGl2Need,
): DiagnosticFact {
    val encoded = glEsVersionCode(glEsVersion)
    return when {
        need == WebGl2Need.NONE -> {
            DiagnosticFact("WebGL 2", FactValue.Text("not required (Google Maps raster)"))
        }

        encoded == null -> {
            DiagnosticFact("WebGL 2", FactValue.Text("unknown (OpenGL ES unreported)"))
        }

        encoded >= WEBGL2_GL_ES_FLOOR -> {
            DiagnosticFact("WebGL 2", FactValue.Status("expected (OpenGL ES $glEsVersion)", FactHealth.OK))
        }

        // Below the floor: name the consequence, which differs by backend.
        need == WebGl2Need.FOR_VECTOR -> {
            DiagnosticFact(
                "WebGL 2",
                FactValue.Status(
                    "unavailable (OpenGL ES $glEsVersion, needs 3.0) — vector map falls back to raster",
                    FactHealth.WARNING,
                ),
            )
        }

        else -> {
            DiagnosticFact(
                "WebGL 2",
                FactValue.Status(
                    "unavailable (OpenGL ES $glEsVersion, needs 3.0) — this backend cannot render",
                    FactHealth.WARNING,
                ),
            )
        }
    }
}

// The renderer the page's WebGL actually runs on, unmasked by the WebView on
// the first render. This is the authoritative counterpart of the version fact
// above: a GPU the WebView blocklists still reports a fine OpenGL ES version
// yet hands WebGL to SwiftShader, Chromium's software rasteriser — and then a
// vector map crawls on the CPU no matter how the camera is driven, while a
// server-rendered raster map is unaffected.
private fun webGlRendererFact(renderer: String?): DiagnosticFact =
    when {
        renderer == null -> {
            DiagnosticFact("WebGL renderer", FactValue.Text("unreported (no map page has rendered yet)"))
        }

        SOFTWARE_RENDERERS.any { renderer.contains(it, ignoreCase = true) } -> {
            DiagnosticFact(
                "WebGL renderer",
                FactValue.Status("$renderer — software; vector maps render on the CPU", FactHealth.WARNING),
            )
        }

        else -> {
            DiagnosticFact("WebGL renderer", FactValue.Status(renderer, FactHealth.OK))
        }
    }

private val SOFTWARE_RENDERERS = listOf("SwiftShader", "llvmpipe", "softpipe", "Software")

// The map page's own frame cadence, from its most recent sampling burst. The
// PERFORMANCE section's UI-frame statistics measure the launcher's Compose
// frames, which stay on time while the WebView's renderer is saturated by a
// heavy map, so this is the row that shows a map that has fallen to one frame
// a second. The threshold names the point where a following map visibly
// steps rather than glides.
private fun pageFramesFact(
    frames: MapRuntimeSignals.PageFrames?,
    nowElapsedRealtimeMs: Long,
): DiagnosticFact {
    if (frames == null) return DiagnosticFact("Page frames", FactValue.Text("no sample yet"))
    val ageS = ((nowElapsedRealtimeMs - frames.elapsedRealtimeMs) / 1_000L).coerceAtLeast(0)
    // Kept short: the fact row gives the value priority over the label, and a
    // long value on the head unit's width ellipsises the label away.
    val value = "median ${frames.medianMs} ms, worst ${frames.worstMs} ms, ${ageS}s ago"
    return DiagnosticFact(
        "Page frames (${frames.sampledFrames})",
        if (frames.medianMs >= PAGE_FRAME_WARNING_MS) {
            FactValue.Status("$value (< 10 fps)", FactHealth.WARNING)
        } else {
            FactValue.Text(value)
        },
    )
}

private const val PAGE_FRAME_WARNING_MS = 100

// Whether the Google page measured its tilted map's perspective (webmap
// lens.ts). Measured, the road ahead runs straight up through the chevron
// beside the cards; unmeasured, the page keeps the chevron there with no
// correction and the road leans — the row that tells an owner which on the
// device. A raster or flat map needs no lens and says so; the host forgets
// the state on every page load, so null is a page that has not reported yet.
private fun googleLensFact(lens: MapRuntimeSignals.GoogleLens?): DiagnosticFact =
    DiagnosticFact(
        "Google lens",
        when (lens?.status) {
            null -> {
                FactValue.Text("not reported yet by this map page")
            }

            MapRuntimeSignals.LensStatus.UNUSED -> {
                FactValue.Text("not needed (raster or flat map)")
            }

            MapRuntimeSignals.LensStatus.MEASURED -> {
                FactValue.Status(
                    "measured (${lensSourceName(lens.source)}), fovy ${"%.1f".format(Locale.ROOT, lens.fovyDeg)}°",
                    FactHealth.OK,
                )
            }

            MapRuntimeSignals.LensStatus.UNMEASURED -> {
                FactValue.Status(
                    "unmeasured (${lensSourceName(lens.source)}) — the road ahead leans",
                    FactHealth.WARNING,
                )
            }
        },
    )

private fun lensSourceName(source: String?): String = if (source == "webgl") "WebGL overlay" else "2D projection"

// deviceConfigurationInfo reports the version as "major.minor"; compare on the
// same packed 0xMMMMmmmm encoding the platform uses for reqGlEsVersion.
private fun glEsVersionCode(glEsVersion: String?): Int? {
    val major = glEsVersion?.substringBefore('.')?.toIntOrNull() ?: return null
    val minor = glEsVersion.substringAfter('.', "0").toIntOrNull() ?: 0
    return (major shl 16) or minor
}

private fun lastFailureFact(
    lastFailure: MapRuntimeSignals.MapFailure?,
    nowElapsedRealtimeMs: Long,
): DiagnosticFact =
    lastFailure?.let { failure ->
        val agoSeconds = ((nowElapsedRealtimeMs - failure.elapsedRealtimeMs) / 1000L).coerceAtLeast(0L)
        DiagnosticFact(
            "Last failure",
            FactValue.Status("${failure.detail} (${agoSeconds}s ago)", FactHealth.ERROR),
        )
    } ?: DiagnosticFact("Last failure", FactValue.Status("none this session", FactHealth.OK))

/** Collects the MAP diagnostics section. */
internal class MapFactsCollector(
    private val context: Context,
) {
    suspend fun mapFacts(): SectionPayload.Facts =
        withContext(Dispatchers.IO) {
            val display = DisplayPreferences(context).settings.first()
            SectionPayload.Facts(
                mapFactsFrom(
                    glEsVersion = context.getSystemService<ActivityManager>()?.deviceConfigurationInfo?.glEsVersion,
                    backend = display.mapBackend,
                    googleRendering = display.googleMapsRendering,
                    hasGoogleMapId = display.googleMapsMapId.isNotBlank(),
                    lastFailure = MapRuntimeSignals.lastFailureOrNull(),
                    failureCount = MapRuntimeSignals.failureCount(),
                    nowElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                    webGlRenderer = MapRuntimeSignals.webGlRendererOrNull(),
                    pageFrames = MapRuntimeSignals.pageFramesOrNull(),
                    googleLens = MapRuntimeSignals.googleLensOrNull(),
                    tileBenchmark = TileBenchmarkResults.latestOrNull(),
                    tileBenchmarkRuns = TileBenchmarkResults.runCount(),
                    offlineRegions = OfflineRegionRepository.get(context).run {
                        load()
                        state.value
                    },
                    routing = routingFacts(),
                ),
            )
        }

    // A build without routing creates neither the tile store nor the planner.
    private suspend fun routingFacts(): RoutingFacts {
        val enabled = context.resources.getBoolean(R.bool.routing_enabled)
        val activeRoute = if (enabled) ActiveRouteStore.shared.active.value else null
        return RoutingFacts(
            buildEnabled = enabled,
            tiles = if (enabled) RoutingTileStore.get(context).installedTiles() else null,
            lastRun = if (enabled) RoutePlanner.get(context).lastRunOrNull() else null,
            geocoderPresent = Geocoder.isPresent(),
            reroutes = if (enabled) Rerouter.get(context).stats.value else null,
            conditions = if (enabled) RoutePreferences(context).conditions.first() else null,
            candidateSearch = if (enabled) CandidateFinder.get(context).lastSearch.value else null,
            restore = if (enabled) RoutePersistence.get(context).decision.value else null,
            routeActive = activeRoute != null,
            activeTurnCount = activeRoute?.route?.turns?.size,
        )
    }
}
