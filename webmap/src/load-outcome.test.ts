import { describe, expect, it } from "vite-plus/test";
import { createNoTileWatchdog } from "./load-outcome";

const TILE_HOST = "https://tiles.openfreemap.org";
const GRACE_MS = 10_000;
// The first error a page opened without data logs: the vector source's
// TileJSON fetch, which MapLibre never repeats.
const TILEJSON_FAILURE = `AJAXError: Failed to fetch (0): ${TILE_HOST}/planet`;

// A watchdog for the default configuration (one tile host, a style that has
// loaded), a fake timer queue, and the events it sent to the host.
function watchdogHarness(options: { styleLoaded?: boolean } = {}) {
    const clock = { now: 0 };
    const timers: Array<{ at: number; run: () => void }> = [];
    const events: string[] = [];
    const watchdog = createNoTileWatchdog({
        tileHost: TILE_HOST,
        graceMs: GRACE_MS,
        styleLoaded: () => options.styleLoaded ?? true,
        reporter: {
            log: () => {},
            report: (kind, detail) => {
                events.push(`${kind}=${String(detail)}`);
            },
        },
        schedule: (run, delayMs) => {
            timers.push({ at: clock.now + delayMs, run });
        },
    });
    const advance = (ms: number): void => {
        clock.now += ms;
        for (const timer of timers.splice(0)) {
            if (timer.at <= clock.now) timer.run();
            else timers.push(timer);
        }
    };
    return { watchdog, events, advance };
}

describe("createNoTileWatchdog", () => {
    it("reports a fatal when a single-host page gets no tile within the grace", () => {
        const h = watchdogHarness();
        h.watchdog.onError(TILEJSON_FAILURE);
        h.advance(GRACE_MS - 1);
        expect(h.events).toEqual([]);
        h.advance(1);
        expect(h.events).toEqual([`fatal=tile-host-unreachable: ${TILEJSON_FAILURE}`]);
    });

    it("stays silent when a tile arrives within the grace", () => {
        const h = watchdogHarness();
        h.watchdog.onError(TILEJSON_FAILURE);
        h.advance(GRACE_MS / 2);
        h.watchdog.onTile();
        h.advance(GRACE_MS);
        expect(h.events).toEqual([]);
    });

    it("never arms once a tile has arrived", () => {
        // A flaky tile on a map that has drawn is no outage.
        const h = watchdogHarness();
        h.watchdog.onTile();
        h.watchdog.onError(`AJAXError: Failed to fetch (0): ${TILE_HOST}/planet/1/0/0.pbf`);
        h.advance(GRACE_MS);
        expect(h.events).toEqual([]);
    });

    it("arms once per page", () => {
        const h = watchdogHarness();
        h.watchdog.onError(TILEJSON_FAILURE);
        h.advance(GRACE_MS / 2);
        h.watchdog.onError(`AJAXError: Failed to fetch (0): ${TILE_HOST}/fonts/Noto Sans Regular`);
        h.advance(GRACE_MS * 3);
        expect(h.events).toEqual([`fatal=tile-host-unreachable: ${TILEJSON_FAILURE}`]);
    });

    it("does not arm on an error from another origin", () => {
        // A terrain DEM served from elsewhere must not rotate the tile host,
        // and must not use up the one arm the tile host's own error gets.
        const h = watchdogHarness();
        h.watchdog.onError("AJAXError: Failed to fetch (0): https://dem.example.test/tiles.json");
        h.advance(GRACE_MS);
        expect(h.events).toEqual([]);
        h.watchdog.onError(TILEJSON_FAILURE);
        h.advance(GRACE_MS);
        expect(h.events).toEqual([`fatal=tile-host-unreachable: ${TILEJSON_FAILURE}`]);
    });

    it("leaves a style that never loaded to the style-load fatal", () => {
        const h = watchdogHarness({ styleLoaded: false });
        h.watchdog.onError(TILEJSON_FAILURE);
        h.advance(GRACE_MS);
        expect(h.events).toEqual([]);
    });

    it("caps the fatal detail at 200 characters", () => {
        const h = watchdogHarness();
        h.watchdog.onError(`${TILEJSON_FAILURE}/${"x".repeat(300)}`);
        h.advance(GRACE_MS);
        expect(h.events).toHaveLength(1);
        expect(h.events[0]).toHaveLength("fatal=".length + 200);
    });
});
