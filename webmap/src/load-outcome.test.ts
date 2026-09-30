import { describe, expect, it } from "vite-plus/test";
import {
    createNoTileWatchdog,
    createStyleLoadWatchdog,
    initFailureDetail,
    isNetworkStatus,
    ScriptLoadError,
} from "./load-outcome";
import { TERRAIN_SOURCE_ID } from "./style";

const TILE_HOST = "https://tiles.openfreemap.org";
// The bundled styles' vector source, served by the tile host.
const VECTOR_SOURCE_ID = "openmaptiles";
// The success signal the first tile of a style sends the host.
const DATA_ARRIVED = `tile=${VECTOR_SOURCE_ID}`;
const GRACE_MS = 10_000;
// The first error a page opened without data logs: the vector source's
// TileJSON fetch, which MapLibre never repeats. Status 0: no response.
const TILEJSON_FAILURE = `AJAXError: Failed to fetch (0): ${TILE_HOST}/planet`;
const TILEJSON_FORBIDDEN = `AJAXError: Forbidden (403): ${TILE_HOST}/planet`;
const STYLE_URL = "https://styles.example.test/basic/style.json";

// A fake timer queue: advance() moves the clock and runs the timers due.
function fakeTimers() {
    const clock = { now: 0 };
    const timers: Array<{ at: number; run: () => void }> = [];
    const schedule = (run: () => void, delayMs: number): void => {
        timers.push({ at: clock.now + delayMs, run });
    };
    const advance = (ms: number): void => {
        clock.now += ms;
        for (const timer of timers.splice(0)) {
            if (timer.at <= clock.now) timer.run();
            else timers.push(timer);
        }
    };
    return { schedule, advance };
}

// The events a watchdog sent to the host, as "kind=detail".
function fakeReporter() {
    const events: string[] = [];
    const reporter = {
        log: () => {},
        report: (kind: string, detail: unknown) => {
            events.push(`${kind}=${String(detail)}`);
        },
    };
    return { events, reporter };
}

// A no-tile watchdog wired the way osm.ts wires it (one tile host, the
// terrain DEM ignored), with a style that has loaded unless the test says
// otherwise.
function noTileHarness(options: { styleLoaded?: boolean } = {}) {
    const { schedule, advance } = fakeTimers();
    const { events, reporter } = fakeReporter();
    const watchdog = createNoTileWatchdog({
        tileHost: TILE_HOST,
        ignoredSourceIds: [TERRAIN_SOURCE_ID],
        graceMs: GRACE_MS,
        styleLoaded: () => options.styleLoaded ?? true,
        reporter,
        schedule,
    });
    return { watchdog, events, advance };
}

function styleHarness() {
    const { schedule, advance } = fakeTimers();
    const { events, reporter } = fakeReporter();
    const watchdog = createStyleLoadWatchdog({ graceMs: GRACE_MS, reporter, schedule });
    return { watchdog, events, advance };
}

describe("isNetworkStatus", () => {
    it("treats a request that got no response as a network failure", () => {
        // MapLibre's AJAXError carries status 0 when fetch itself failed:
        // offline, DNS, a refused connection, a timeout.
        expect(isNetworkStatus(0)).toBe(true);
    });

    it("treats server errors, timeouts and throttling as network failures", () => {
        [500, 502, 503, 504, 408, 429].forEach((status) => {
            expect(isNetworkStatus(status), String(status)).toBe(true);
        });
    });

    it("treats a request the server refused as a configuration failure", () => {
        // A mistyped URL's 404, a rejected key's 401/403: no reload fixes them.
        [400, 401, 403, 404, 410].forEach((status) => {
            expect(isNetworkStatus(status), String(status)).toBe(false);
        });
    });

    it("treats an error without an HTTP status as a configuration failure", () => {
        // A response that arrived but could not be used (a style that does
        // not parse): the server answered.
        expect(isNetworkStatus(null)).toBe(false);
    });
});

describe("createNoTileWatchdog", () => {
    it("reports a fatal when a single-host page gets no tile within the grace", () => {
        const h = noTileHarness();
        h.watchdog.onError(TILEJSON_FAILURE, 0);
        h.advance(GRACE_MS - 1);
        expect(h.events).toEqual([]);
        h.advance(1);
        expect(h.events).toEqual([`fatal=tile-host-unreachable: ${TILEJSON_FAILURE}`]);
    });

    it("reports a tile host that refused the request as rejected", () => {
        const h = noTileHarness();
        h.watchdog.onError(TILEJSON_FORBIDDEN, 403);
        h.advance(GRACE_MS);
        expect(h.events).toEqual([`fatal=tile-host-rejected: ${TILEJSON_FORBIDDEN}`]);
    });

    it("reports a tile host that failed with a server error as unreachable", () => {
        const failure = `AJAXError: Service Unavailable (503): ${TILE_HOST}/planet`;
        const h = noTileHarness();
        h.watchdog.onError(failure, 503);
        h.advance(GRACE_MS);
        expect(h.events).toEqual([`fatal=tile-host-unreachable: ${failure}`]);
    });

    it("keeps the host unreachable when a network failure joins refusals", () => {
        // Mixed signals keep the page retrying: stopping during an outage is
        // the costly mistake.
        const h = noTileHarness();
        h.watchdog.onError(TILEJSON_FORBIDDEN, 403);
        h.watchdog.onError(`AJAXError: Failed to fetch (0): ${TILE_HOST}/sprites/ofm`, 0);
        h.advance(GRACE_MS);
        expect(h.events).toEqual([`fatal=tile-host-unreachable: ${TILEJSON_FORBIDDEN}`]);
    });

    it("reports no fatal when a tile arrives within the grace", () => {
        const h = noTileHarness();
        h.watchdog.onError(TILEJSON_FAILURE, 0);
        h.advance(GRACE_MS / 2);
        h.watchdog.onTile(VECTOR_SOURCE_ID);
        h.advance(GRACE_MS);
        expect(h.events).toEqual([DATA_ARRIVED]);
    });

    it("reports the first tile of the style to the host once", () => {
        // The host's success signal: it restarts the retry backoff on it, so
        // a tile per request would be noise.
        const h = noTileHarness();
        h.watchdog.onTile(VECTOR_SOURCE_ID);
        h.watchdog.onTile(VECTOR_SOURCE_ID);
        // The bundled light style's relief raster, also from the tile host.
        h.watchdog.onTile("ne2_shaded");
        expect(h.events).toEqual([DATA_ARRIVED]);
    });

    it("does not report a terrain DEM tile as the page's data", () => {
        // The DEM host answering says nothing about the tile host.
        const h = noTileHarness();
        h.watchdog.onTile(TERRAIN_SOURCE_ID);
        expect(h.events).toEqual([]);
    });

    it("reports the first tile of a swapped-in style again", () => {
        // A swap is judged like a fresh page, and so is its data.
        const h = noTileHarness();
        h.watchdog.onTile(VECTOR_SOURCE_ID);
        h.watchdog.onStyleSwap();
        h.watchdog.onTile(VECTOR_SOURCE_ID);
        expect(h.events).toEqual([DATA_ARRIVED, DATA_ARRIVED]);
    });

    it("does not count a terrain DEM tile as the tile host answering", () => {
        // Terrain on, the tile host dead, the DEM host answering: shading
        // with no roads is still a dead map.
        const h = noTileHarness();
        h.watchdog.onError(TILEJSON_FAILURE, 0);
        h.watchdog.onTile(TERRAIN_SOURCE_ID);
        h.advance(GRACE_MS);
        expect(h.events).toEqual([`fatal=tile-host-unreachable: ${TILEJSON_FAILURE}`]);
    });

    it("judges a swapped-in style afresh although the replaced style drew tiles", () => {
        // A light/dark flip between a hosted and a bundled style re-creates the
        // vector source, so the new style fetches the TileJSON again; if that
        // fails, only this watchdog can see the blank map. A DEM tile of the
        // new style still proves nothing.
        const h = noTileHarness();
        h.watchdog.onTile(VECTOR_SOURCE_ID);
        h.watchdog.onStyleSwap();
        h.watchdog.onError(TILEJSON_FAILURE, 0);
        h.watchdog.onTile(TERRAIN_SOURCE_ID);
        h.advance(GRACE_MS);
        expect(h.events).toEqual([
            DATA_ARRIVED,
            `fatal=tile-host-unreachable: ${TILEJSON_FAILURE}`,
        ]);
    });

    it("retires a timer armed for the replaced style", () => {
        const second = `AJAXError: Failed to fetch (0): ${TILE_HOST}/planet?style=2`;
        const h = noTileHarness();
        h.watchdog.onError(TILEJSON_FAILURE, 0);
        h.advance(GRACE_MS / 2);
        h.watchdog.onStyleSwap();
        // The replaced style's timer comes due here: that style is gone.
        h.advance(GRACE_MS / 2);
        expect(h.events).toEqual([]);
        h.watchdog.onError(second, 0);
        h.advance(GRACE_MS);
        expect(h.events).toEqual([`fatal=tile-host-unreachable: ${second}`]);
    });

    it("never arms once a tile has arrived", () => {
        // A flaky tile on a map that has drawn is no outage.
        const h = noTileHarness();
        h.watchdog.onTile(VECTOR_SOURCE_ID);
        h.watchdog.onError(`AJAXError: Failed to fetch (0): ${TILE_HOST}/planet/1/0/0.pbf`, 0);
        h.advance(GRACE_MS);
        expect(h.events).toEqual([DATA_ARRIVED]);
    });

    it("arms once per page", () => {
        const h = noTileHarness();
        h.watchdog.onError(TILEJSON_FAILURE, 0);
        h.advance(GRACE_MS / 2);
        h.watchdog.onError(
            `AJAXError: Failed to fetch (0): ${TILE_HOST}/fonts/Noto Sans Regular`,
            0,
        );
        h.advance(GRACE_MS * 3);
        expect(h.events).toEqual([`fatal=tile-host-unreachable: ${TILEJSON_FAILURE}`]);
    });

    it("does not arm on an error from another origin", () => {
        // A terrain DEM served from elsewhere must not rotate the tile host,
        // and must not use up the one arm the tile host's own error gets.
        const h = noTileHarness();
        h.watchdog.onError(
            "AJAXError: Failed to fetch (0): https://dem.example.test/tiles.json",
            0,
        );
        h.advance(GRACE_MS);
        expect(h.events).toEqual([]);
        h.watchdog.onError(TILEJSON_FAILURE, 0);
        h.advance(GRACE_MS);
        expect(h.events).toEqual([`fatal=tile-host-unreachable: ${TILEJSON_FAILURE}`]);
    });

    it("leaves a style that never loaded to the style-load fatal", () => {
        const h = noTileHarness({ styleLoaded: false });
        h.watchdog.onError(TILEJSON_FAILURE, 0);
        h.advance(GRACE_MS);
        expect(h.events).toEqual([]);
    });

    it("caps the fatal detail at 200 characters", () => {
        const h = noTileHarness();
        h.watchdog.onError(`${TILEJSON_FAILURE}/${"x".repeat(300)}`, 0);
        h.advance(GRACE_MS);
        expect(h.events).toHaveLength(1);
        expect(h.events[0]).toHaveLength("fatal=".length + 200);
    });
});

describe("createStyleLoadWatchdog", () => {
    it("reports a style that never arrived as a network failure", () => {
        const failure = `AJAXError: Failed to fetch (0): ${STYLE_URL}`;
        const h = styleHarness();
        h.watchdog.onError(failure, 0);
        h.advance(GRACE_MS - 1);
        expect(h.events).toEqual([]);
        h.advance(1);
        expect(h.events).toEqual([`fatal=style-load-failed: ${failure}`]);
    });

    it("reports a style URL the server refused as rejected", () => {
        // A mistyped custom style URL.
        const failure = `AJAXError: Not Found (404): ${STYLE_URL}`;
        const h = styleHarness();
        h.watchdog.onError(failure, 404);
        h.advance(GRACE_MS);
        expect(h.events).toEqual([`fatal=style-load-rejected: ${failure}`]);
    });

    it("stays silent when the style loads within the grace", () => {
        const h = styleHarness();
        h.watchdog.onError(`AJAXError: Failed to fetch (0): ${STYLE_URL}`, 0);
        h.watchdog.onStyleLoaded();
        h.advance(GRACE_MS);
        expect(h.events).toEqual([]);
        expect(h.watchdog.styleLoaded()).toBe(true);
    });

    it("ignores errors once the style has loaded", () => {
        const h = styleHarness();
        h.watchdog.onStyleLoaded();
        h.watchdog.onError(`AJAXError: Failed to fetch (0): ${TILE_HOST}/planet`, 0);
        h.advance(GRACE_MS);
        expect(h.events).toEqual([]);
    });

    it("judges a swapped-in style afresh and retires the earlier load's timer", () => {
        const first = `AJAXError: Failed to fetch (0): ${TILE_HOST}/styles/positron`;
        const second = `AJAXError: Not Found (404): ${STYLE_URL}`;
        const h = styleHarness();
        h.watchdog.onError(first, 0);
        h.advance(GRACE_MS / 2);
        h.watchdog.onStyleSwap();
        expect(h.watchdog.styleLoaded()).toBe(false);
        h.watchdog.onError(second, 404);
        // The first load's timer comes due here: that style is gone.
        h.advance(GRACE_MS / 2);
        expect(h.events).toEqual([]);
        h.advance(GRACE_MS / 2);
        expect(h.events).toEqual([`fatal=style-load-rejected: ${second}`]);
    });
});

describe("initFailureDetail", () => {
    it("reports a map script that could not be fetched as backend-load-failed", () => {
        expect(
            initFailureDetail(
                new ScriptLoadError("The Google Maps JavaScript API could not load."),
            ),
        ).toBe("backend-load-failed: The Google Maps JavaScript API could not load.");
    });

    it("reports any other init failure as map-init-exception", () => {
        // Thrown with the script loaded (a reload after the map object exists
        // is a billed map load), or a local chunk that failed to import.
        expect(initFailureDetail(new TypeError("mapsLib.Map is not a constructor"))).toBe(
            "map-init-exception: mapsLib.Map is not a constructor",
        );
        expect(initFailureDetail("boom")).toBe("map-init-exception: boom");
    });

    it("caps the detail at 200 characters", () => {
        expect(initFailureDetail(new ScriptLoadError("x".repeat(300)))).toHaveLength(200);
    });
});
