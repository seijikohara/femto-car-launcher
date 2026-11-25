package io.github.seijikohara.femto.data.diagnostics

import io.github.seijikohara.femto.data.display.GoogleMapsRendering
import io.github.seijikohara.femto.data.display.MapBackend
import io.github.seijikohara.femto.data.map.MapRuntimeSignals
import io.github.seijikohara.femto.data.map.offline.OfflineRegionsState
import io.github.seijikohara.femto.data.map.offline.RegionDownload
import io.github.seijikohara.femto.data.map.offline.RegionDownloadFailure
import io.github.seijikohara.femto.data.map.offline.RegionDownloadPhase
import io.github.seijikohara.femto.data.map.offline.SavedRegion
import io.github.seijikohara.femto.data.map.offline.TileBenchmarkOutcome
import io.github.seijikohara.femto.data.map.offline.TileBenchmarkRow
import io.github.seijikohara.femto.data.routing.CandidateSearch
import io.github.seijikohara.femto.data.routing.CandidateSearchEnding
import io.github.seijikohara.femto.data.routing.InstalledRoutingTile
import io.github.seijikohara.femto.data.routing.RerouteStats
import io.github.seijikohara.femto.data.routing.RouteConditions
import io.github.seijikohara.femto.data.routing.RouteRestoreDecision
import io.github.seijikohara.femto.data.routing.RouteRun
import io.github.seijikohara.femto.data.routing.RouteRunOutcome
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapFactsTest {
    private fun facts(
        glEsVersion: String? = "3.2",
        backend: MapBackend = MapBackend.OSM,
        googleRendering: GoogleMapsRendering = GoogleMapsRendering.AUTO,
        hasGoogleMapId: Boolean = false,
        lastFailure: MapRuntimeSignals.MapFailure? = null,
        failureCount: Int = 0,
        nowElapsedRealtimeMs: Long = 0L,
        webGlRenderer: String? = null,
        pageFrames: MapRuntimeSignals.PageFrames? = null,
        googleLens: MapRuntimeSignals.GoogleLens? = null,
        tileBenchmark: TileBenchmarkOutcome? = null,
        tileBenchmarkRuns: Int = 0,
        offlineRegions: OfflineRegionsState? = null,
        routing: RoutingFacts? = null,
    ) = mapFactsFrom(
        glEsVersion,
        backend,
        googleRendering,
        hasGoogleMapId,
        lastFailure,
        failureCount,
        nowElapsedRealtimeMs,
        webGlRenderer,
        pageFrames,
        googleLens,
        tileBenchmark,
        tileBenchmarkRuns,
        offlineRegions,
        routing,
    )

    private fun savedRegion(sizeBytes: Long) = SavedRegion("r$sizeBytes", "Region $sizeBytes", sizeBytes, null, 0L)

    private fun download(
        phase: RegionDownloadPhase,
        receivedBytes: Long = 14,
        totalBytes: Long = 100,
        failure: RegionDownloadFailure? = null,
    ) = RegionDownload("r", "Secret Name", receivedBytes, totalBytes, phase, failure)

    private fun downloadFact(download: RegionDownload?) =
        facts(offlineRegions = OfflineRegionsState(loaded = true, download = download))
            .single { it.label == "Region download" }
            .value

    @Test
    fun `no region facts without the offline state`() {
        assertTrue(facts().none { it.label == "Saved regions" || it.label == "Region download" })
    }

    @Test
    fun `saved regions read zero when empty`() {
        assertEquals(
            FactValue.Text("0, 0 MB"),
            facts(offlineRegions = OfflineRegionsState(loaded = true)).single { it.label == "Saved regions" }.value,
        )
    }

    @Test
    fun `saved regions sum their sizes in decimal megabytes rounded down`() {
        val state = OfflineRegionsState(loaded = true, saved = listOf(savedRegion(862_351_360), savedRegion(1_728_512)))
        assertEquals(
            FactValue.Text("2, 864 MB"),
            facts(offlineRegions = state).single { it.label == "Saved regions" }.value,
        )
    }

    @Test
    fun `region facts sit after the map facts and before the benchmark`() {
        val labels =
            facts(
                offlineRegions = OfflineRegionsState(loaded = true),
                tileBenchmark = TileBenchmarkOutcome.Failed("s"),
            ).map { it.label }
        assertEquals(listOf("Saved regions", "Region download", "Tile conversion"), labels.takeLast(3))
    }

    @Test
    fun `no download reads none`() {
        assertEquals(FactValue.Text("none"), downloadFact(null))
    }

    @Test
    fun `a running download reads its percentage`() {
        assertEquals(FactValue.Text("running 14%"), downloadFact(download(RegionDownloadPhase.RUNNING)))
    }

    @Test
    fun `a verifying download reads its phase`() {
        assertEquals(
            FactValue.Text("verifying 100%"),
            downloadFact(download(RegionDownloadPhase.VERIFYING, receivedBytes = 100)),
        )
    }

    @Test
    fun `a paused download with a failure warns and names it`() {
        assertEquals(
            FactValue.Status("paused 14%: network", FactHealth.WARNING),
            downloadFact(download(RegionDownloadPhase.PAUSED, failure = RegionDownloadFailure.NETWORK)),
        )
    }

    @Test
    fun `a paused download without a failure is plain text`() {
        assertEquals(FactValue.Text("paused 14%"), downloadFact(download(RegionDownloadPhase.PAUSED)))
    }

    @Test
    fun `a failed download is an error naming the failure`() {
        assertEquals(
            FactValue.Status("failed: corrupt", FactHealth.ERROR),
            downloadFact(download(RegionDownloadPhase.FAILED, failure = RegionDownloadFailure.CORRUPT)),
        )
    }

    @Test
    fun `the percentage of a very large download is computed in Long`() {
        assertEquals(
            FactValue.Text("running 50%"),
            downloadFact(
                download(RegionDownloadPhase.RUNNING, receivedBytes = 29_222_385_664, totalBytes = 58_444_771_328),
            ),
        )
    }

    @Test
    fun `an unknown total reads zero percent`() {
        assertEquals(
            FactValue.Text("running 0%"),
            downloadFact(download(RegionDownloadPhase.RUNNING, receivedBytes = 0, totalBytes = 0)),
        )
    }

    @Test
    fun `no region name reaches the report`() {
        val state =
            OfflineRegionsState(
                loaded = true,
                saved = listOf(savedRegion(1)),
                download = download(RegionDownloadPhase.RUNNING),
            )
        assertTrue(facts(offlineRegions = state).none { "Region 1" in it.label || "Secret" in it.value.toString() })
    }

    @Test
    fun `the Google lens row appears only on the Google backend`() {
        assertTrue(facts(backend = MapBackend.OSM).none { it.label == "Google lens" })
        assertEquals(
            FactValue.Text("not reported yet by this map page"),
            facts(backend = MapBackend.GOOGLEMAPS).single { it.label == "Google lens" }.value,
        )
    }

    @Test
    fun `a measured Google lens reports its source and field of view`() {
        val fact =
            facts(
                backend = MapBackend.GOOGLEMAPS,
                googleLens =
                    MapRuntimeSignals.GoogleLens(
                        MapRuntimeSignals.LensStatus.MEASURED,
                        source = "webgl",
                        fovyDeg = 27.3,
                    ),
            ).single { it.label == "Google lens" }
        assertEquals(FactValue.Status("measured (WebGL overlay), fovy 27.3°", FactHealth.OK), fact.value)
    }

    @Test
    fun `an unmeasured Google lens warns that the road leans beside the cards`() {
        val fact =
            facts(
                backend = MapBackend.GOOGLEMAPS,
                googleLens =
                    MapRuntimeSignals.GoogleLens(
                        MapRuntimeSignals.LensStatus.UNMEASURED,
                        source = "canvas",
                        fovyDeg = null,
                    ),
            ).single { it.label == "Google lens" }
        assertEquals(
            FactValue.Status("unmeasured (2D projection) — the road ahead leans", FactHealth.WARNING),
            fact.value,
        )
    }

    @Test
    fun `an unused Google lens says a raster or flat map needs none, without a warning`() {
        val fact =
            facts(
                backend = MapBackend.GOOGLEMAPS,
                googleLens = MapRuntimeSignals.GoogleLens(
                    MapRuntimeSignals.LensStatus.UNUSED,
                    source = null,
                    fovyDeg = null,
                ),
            ).single { it.label == "Google lens" }
        assertEquals(FactValue.Text("not needed (raster or flat map)"), fact.value)
    }

    @Test
    fun `page frames read no sample until the page has reported one`() {
        assertEquals(
            DiagnosticFact("Page frames", FactValue.Text("no sample yet")),
            facts().single { it.label == "Page frames" },
        )
    }

    @Test
    fun `a page keeping pace reports its cadence and the sample age as plain text`() {
        val fact =
            facts(
                pageFrames = MapRuntimeSignals.PageFrames(
                    medianMs = 33,
                    worstMs = 66,
                    sampledFrames = 30,
                    elapsedRealtimeMs = 5_000L,
                ),
                nowElapsedRealtimeMs = 17_000L,
            ).single { it.label == "Page frames (30)" }
        assertEquals(FactValue.Text("median 33 ms, worst 66 ms, 12s ago"), fact.value)
    }

    @Test
    fun `a page below ten frames a second warns`() {
        // The launcher's own UI frames can be on time while the WebView's renderer
        // has fallen to a frame a second; this row is what shows it.
        val fact =
            facts(
                pageFrames = MapRuntimeSignals.PageFrames(
                    medianMs = 980,
                    worstMs = 1400,
                    sampledFrames = 30,
                    elapsedRealtimeMs = 0L,
                ),
            ).single { it.label == "Page frames (30)" }
        assertEquals(
            FactValue.Status("median 980 ms, worst 1400 ms, 0s ago (< 10 fps)", FactHealth.WARNING),
            fact.value,
        )
    }

    @Test
    fun `the WebGL renderer reads unreported until a page has rendered`() {
        val fact = facts().single { it.label == "WebGL renderer" }
        assertEquals(FactValue.Text("unreported (no map page has rendered yet)"), fact.value)
    }

    @Test
    fun `a hardware WebGL renderer reports OK`() {
        val fact = facts(webGlRenderer = "Mali-G52 MC2").single { it.label == "WebGL renderer" }
        assertEquals(FactValue.Status("Mali-G52 MC2", FactHealth.OK), fact.value)
    }

    @Test
    fun `a software WebGL renderer warns, since a vector map then renders on the CPU`() {
        // The version fact above it can still read "expected": the platform's
        // OpenGL ES is fine, it is the WebView's blocklist that hands WebGL to
        // SwiftShader. Only the unmasked renderer string shows that.
        val fact = facts(webGlRenderer = "Google SwiftShader").single { it.label == "WebGL renderer" }
        assertEquals(
            FactValue.Status("Google SwiftShader — software; vector maps render on the CPU", FactHealth.WARNING),
            fact.value,
        )
    }

    @Test
    fun `a WebGL 2 backend below the OpenGL ES floor warns that it cannot render`() {
        // The Android 13 CDD mandates only ES 2.0, so this device is compliant and
        // still cannot host the WebGL 2 maplibre 6 requires.
        assertEquals(
            DiagnosticFact(
                "WebGL 2",
                FactValue.Status(
                    "unavailable (OpenGL ES 2.0, needs 3.0) — this backend cannot render",
                    FactHealth.WARNING,
                ),
            ),
            facts(glEsVersion = "2.0", backend = MapBackend.OSM).first(),
        )
    }

    @Test
    fun `a Google raster map never warns, because it needs no WebGL at all`() {
        // Raster tiles are rendered server-side, so flagging WebGL here would
        // report a problem on a map that draws perfectly.
        assertEquals(
            DiagnosticFact("WebGL 2", FactValue.Text("not required (Google Maps raster)")),
            facts(
                glEsVersion = "2.0",
                backend = MapBackend.GOOGLEMAPS,
                googleRendering = GoogleMapsRendering.AUTO,
                hasGoogleMapId = false,
            ).first(),
        )
    }

    @Test
    fun `an explicit Google raster choice never warns, even with a Map ID set`() {
        // RASTER overrides the Map ID's cloud configuration, so the Map ID no
        // longer implies vector — the pre-setting inference would have warned here.
        assertEquals(
            DiagnosticFact("WebGL 2", FactValue.Text("not required (Google Maps raster)")),
            facts(
                glEsVersion = "2.0",
                backend = MapBackend.GOOGLEMAPS,
                googleRendering = GoogleMapsRendering.RASTER,
                hasGoogleMapId = true,
            ).first(),
        )
    }

    @Test
    fun `an explicit Google vector choice warns without a Map ID`() {
        // Vector no longer needs a Map ID (Maps JS 3.56.10+), so the warning must
        // not depend on one.
        assertEquals(
            FactHealth.WARNING,
            facts(
                glEsVersion = "2.0",
                backend = MapBackend.GOOGLEMAPS,
                googleRendering = GoogleMapsRendering.VECTOR,
                hasGoogleMapId = false,
            ).first().health,
        )
    }

    @Test
    fun `a Google vector map warns that it silently degrades rather than fails`() {
        // Google falls back to raster instead of failing, so the map still draws —
        // what is lost is the vector opt-in the user configured a Map ID for.
        assertEquals(
            DiagnosticFact(
                "WebGL 2",
                FactValue.Status(
                    "unavailable (OpenGL ES 2.0, needs 3.0) — vector map falls back to raster",
                    FactHealth.WARNING,
                ),
            ),
            facts(
                glEsVersion = "2.0",
                backend = MapBackend.GOOGLEMAPS,
                googleRendering = GoogleMapsRendering.AUTO,
                hasGoogleMapId = true,
            ).first(),
        )
    }

    @Test
    fun `an OpenGL ES version at or above 3_0 reports WebGL 2 as expected`() {
        assertEquals(
            DiagnosticFact("WebGL 2", FactValue.Status("expected (OpenGL ES 3.2)", FactHealth.OK)),
            facts(glEsVersion = "3.2").first(),
        )
    }

    @Test
    fun `a minor version alone never crosses the floor`() {
        // "2.9" must stay below 3.0: comparing the strings, or the minor in
        // isolation, would read it as sufficient.
        assertEquals(FactHealth.WARNING, facts(glEsVersion = "2.9").first().health)
    }

    @Test
    fun `an unreported OpenGL ES version says so rather than guessing`() {
        assertEquals(
            DiagnosticFact("WebGL 2", FactValue.Text("unknown (OpenGL ES unreported)")),
            facts(glEsVersion = null).first(),
        )
    }

    @Test
    fun `a clean session reports no failure`() {
        assertEquals(
            DiagnosticFact("Last failure", FactValue.Status("none this session", FactHealth.OK)),
            facts().single { it.label == "Last failure" },
        )
    }

    @Test
    fun `a recorded failure reports its reason and age`() {
        assertEquals(
            DiagnosticFact("Last failure", FactValue.Status("no-webgl-context (90s ago)", FactHealth.ERROR)),
            facts(
                lastFailure = MapRuntimeSignals.MapFailure("no-webgl-context", elapsedRealtimeMs = 1_000L),
                failureCount = 1,
                nowElapsedRealtimeMs = 91_000L,
            ).single { it.label == "Last failure" },
        )
    }

    @Test
    fun `a single failure adds no count row`() {
        assertTrue(
            facts(
                lastFailure = MapRuntimeSignals.MapFailure("no-webgl-context", elapsedRealtimeMs = 0L),
                failureCount = 1,
            ).none { it.label == "Failures this session" },
        )
    }

    @Test
    fun `repeated failures surface the count so a flapping map is visible`() {
        assertEquals(
            DiagnosticFact("Failures this session", FactValue.Text("4")),
            facts(
                lastFailure = MapRuntimeSignals.MapFailure("style-load-failed", elapsedRealtimeMs = 0L),
                failureCount = 4,
            ).single { it.label == "Failures this session" },
        )
    }

    @Test
    fun `no benchmark outcome adds no fact`() {
        assertTrue(facts().none { it.label.startsWith("Tile") })
    }

    @Test
    fun `a measured benchmark reports the run, each sample and the parallel pass`() {
        val facts =
            facts(
                nowElapsedRealtimeMs = 43_000,
                tileBenchmark =
                    TileBenchmarkOutcome.Measured(
                        rows =
                            listOf(
                                TileBenchmarkRow(
                                    "kanto-14-1-2",
                                    rawBytes = 626_688,
                                    features = 21_034,
                                    outputBytes = 552_960,
                                    firstMs = 85.24,
                                    warmMedianMs = 41.0,
                                    warmP95Ms = 55.31,
                                    warmMaxMs = 61.07,
                                    parallelMedianMs = 60.1,
                                    parallelMaxMs = 88.94,
                                ),
                            ),
                        warmRuns = 30,
                        parallelTiles = 4,
                        parallelRounds = 10,
                        cores = 8,
                        finishedAtElapsedRealtimeMs = 1_000,
                    ),
                tileBenchmarkRuns = 2,
            ).filter { it.label.startsWith("Tile") }
        assertEquals(
            listOf(
                DiagnosticFact(
                    "Tile conversion",
                    FactValue.Text("run 2 since launch, tiles 1, warm runs 30, cores 8, 42s ago"),
                ),
                DiagnosticFact(
                    "Tile kanto-14-1-2",
                    FactValue.Text(
                        "612 KB, 21034 features \u2192 540 KB; first 85.2 ms; median 41.0 ms; p95 55.3 ms; max 61.1 ms",
                    ),
                ),
                DiagnosticFact(
                    "Tile kanto-14-1-2 in parallel",
                    FactValue.Text("4 at once, 10 rounds: median 60.1 ms; max 88.9 ms"),
                ),
            ),
            facts,
        )
    }

    @Test
    fun `a failed benchmark is an error fact naming the sample`() {
        assertEquals(
            DiagnosticFact("Tile conversion", FactValue.Status("failed on kanto-14-1-2", FactHealth.ERROR)),
            facts(tileBenchmark = TileBenchmarkOutcome.Failed("kanto-14-1-2")).single { it.label.startsWith("Tile") },
        )
    }

    @Test
    fun `a build without routing says so and reads no routing data`() {
        val facts =
            routingFacts(RoutingFacts(buildEnabled = false, tiles = null, lastRun = null, geocoderPresent = true))

        assertEquals(
            listOf(
                DiagnosticFact("Routing", FactValue.Text("off in this build")),
                DiagnosticFact("Geocoder", FactValue.Text("present")),
            ),
            facts,
        )
    }

    @Test
    fun `routing on the OSM map reports its data, the last route and the geocoder`() {
        val facts =
            routingFacts(
                RoutingFacts(
                    buildEnabled = true,
                    tiles = listOf(routingTile("E130_N30", 48_000_000L), routingTile("E135_N30", 96_000_000L)),
                    lastRun = RouteRun(RouteRunOutcome.TIMED_OUT, computingMs = 60_012L),
                    geocoderPresent = false,
                ),
            )

        assertEquals(
            listOf(
                DiagnosticFact("Routing", FactValue.Text("available")),
                DiagnosticFact("Routing data", FactValue.Text("2 tiles, 144 MB")),
                DiagnosticFact("Last route", FactValue.Text("timed out, 60012 ms")),
                DiagnosticFact("Active route", FactValue.Text("no route")),
                DiagnosticFact("Route conditions", FactValue.Text("none")),
                DiagnosticFact("Route candidates", FactValue.Text("none this session")),
                DiagnosticFact("Last reroute", FactValue.Text("none this session")),
                DiagnosticFact("Route restore", FactValue.Text("none this session")),
                DiagnosticFact("Geocoder", FactValue.Text("absent")),
            ),
            facts,
        )
    }

    @Test
    fun `the route restore says what the start did with the kept route`() {
        val texts =
            RouteRestoreDecision.entries.map { decision ->
                routingFacts(RoutingFacts(true, emptyList(), null, true, restore = decision))
                    .single { it.label == "Route restore" }
                    .value
            }

        assertEquals(
            listOf(
                "restored",
                "parked too long, deleted",
                "no route kept",
                "waiting for a GPS fix",
                "replaced by a new route",
                "no time of the last drive, deleted",
                "GPS time before the last drive, deleted",
                "off in Settings",
            ).map { FactValue.Text(it) },
            texts,
        )
    }

    @Test
    fun `each candidate's computing time is reported in the order found, with how the search ended`() {
        val search = CandidateSearch(listOf(500L, 1_100L, 1_640L), CandidateSearchEnding.ALL_FOUND)

        val facts = routingFacts(RoutingFacts(true, emptyList(), null, true, candidateSearch = search))

        assertEquals(
            FactValue.Text("3: 0.5 s, 1.1 s, 1.6 s; all found"),
            facts.single { it.label == "Route candidates" }.value,
        )
    }

    @Test
    fun `a candidate search that ended early or was stopped says so`() {
        val endings = listOf(CandidateSearchEnding.NO_MORE_ROUTES, CandidateSearchEnding.STOPPED, null)

        val texts =
            endings.map { ending ->
                val search = CandidateSearch(listOf(500L), ending)
                routingFacts(RoutingFacts(true, emptyList(), null, true, candidateSearch = search))
                    .single { it.label == "Route candidates" }
                    .value
            }

        assertEquals(
            listOf("1: 0.5 s; no more routes", "1: 0.5 s; stopped", "1: 0.5 s; searching").map { FactValue.Text(it) },
            texts,
        )
    }

    // Counts only: the report must not say where the route goes, and no turn position enters it.
    @Test
    fun `the active route reports how many turn hints it has`() {
        val texts =
            listOf(
                RoutingFacts(true, emptyList(), null, true),
                RoutingFacts(true, emptyList(), null, true, routeActive = true),
                RoutingFacts(true, emptyList(), null, true, routeActive = true, activeTurnCount = 0),
                RoutingFacts(true, emptyList(), null, true, routeActive = true, activeTurnCount = 7),
            ).map { routingFacts(it).single { fact -> fact.label == "Active route" }.value }

        assertEquals(
            listOf("no route", "no turn data", "0 turn hints", "7 turn hints").map { FactValue.Text(it) },
            texts,
        )
    }

    @Test
    fun `the route conditions the next route runs under are reported`() {
        val conditions = RouteConditions(avoidMotorways = true, avoidTolls = true)

        val facts = routingFacts(RoutingFacts(true, emptyList(), null, true, conditions = conditions))

        assertEquals(
            FactValue.Text("avoid motorways, avoid tolls"),
            facts.single { it.label == "Route conditions" }.value,
        )
    }

    // "Offered": BRouter may still refuse the reference and search in full.
    @Test
    fun `the last reroute reports its outcome, time, count and whether a reference was offered`() {
        val reroutes = RerouteStats(3, RouteRun(RouteRunOutcome.FOUND, computingMs = 63L), usedReference = true)

        val facts = routingFacts(RoutingFacts(true, emptyList(), null, true, reroutes = reroutes))

        assertEquals(
            FactValue.Text("found, 63 ms, 3 this session, reference offered"),
            facts.single { it.label == "Last reroute" }.value,
        )
    }

    @Test
    fun `a reroute without a reference says none was offered`() {
        val reroutes = RerouteStats(1, RouteRun(RouteRunOutcome.MISSING_TILES, computingMs = 5L), usedReference = false)

        val facts = routingFacts(RoutingFacts(true, emptyList(), null, true, reroutes = reroutes))

        assertEquals(
            FactValue.Text("missing tiles, 5 ms, 1 this session, no reference offered"),
            facts.single { it.label == "Last reroute" }.value,
        )
    }

    @Test
    fun `routing facts never name a tile`() {
        val facts =
            routingFacts(
                RoutingFacts(true, listOf(routingTile("E130_N30", 1L)), lastRun = null, geocoderPresent = true),
            )

        assertTrue(facts.none { "E130" in it.label || "E130" in it.value.toString() })
        assertEquals(FactValue.Text("none this session"), facts.single { it.label == "Last route" }.value)
    }

    @Test
    fun `routing on the Google backend is off`() {
        assertEquals(
            FactValue.Text("off (Google Maps backend)"),
            routingFacts(RoutingFacts(true, emptyList(), null, true), backend = MapBackend.GOOGLEMAPS)
                .single { it.label == "Routing" }
                .value,
        )
    }

    private fun routingFacts(
        routing: RoutingFacts,
        backend: MapBackend = MapBackend.OSM,
    ): List<DiagnosticFact> {
        val label =
            setOf(
                "Routing",
                "Routing data",
                "Last route",
                "Active route",
                "Route conditions",
                "Route candidates",
                "Last reroute",
                "Route restore",
                "Geocoder",
            )
        return facts(backend = backend, routing = routing).filter { it.label in label }
    }

    private fun routingTile(
        name: String,
        sizeBytes: Long,
    ) = InstalledRoutingTile(name, sizeBytes, dataDateEpochMs = null, checkedAtEpochMs = null)

    @Test
    fun `an aborted benchmark is an error fact giving the reason`() {
        assertEquals(
            DiagnosticFact("Tile conversion", FactValue.Status("did not finish: interrupted", FactHealth.ERROR)),
            facts(tileBenchmark = TileBenchmarkOutcome.Aborted("interrupted")).single { it.label.startsWith("Tile") },
        )
    }
}
