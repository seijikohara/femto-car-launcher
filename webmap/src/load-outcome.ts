// Outcome-gated failure detection for the OSM page: the "no tile arrived"
// watchdog. Pure (the timer is injected, as in createBearingReporter), so the
// decision is unit-tested; osm.ts only wires MapLibre's events into it.
//
// An unreachable tile host does NOT fail the style load: the bundled styles
// come from appassets and only their sources fail, so the style loads and the
// style-load fatal never arms. MapLibre fetches a source's TileJSON once and,
// after a failure, marks the source loaded and ignores it for the page's life,
// so a page opened without data sits blank for good. The watchdog arms one
// grace timer on the first error naming the tile host and reports a `fatal`
// only if NO tile has arrived when it fires. That fatal is what makes the host
// reload the page (on the next tile host, when there is one), with one host as
// with several. A tile arriving at any point stands the watchdog down, so a
// flaky tile on a map that has drawn never reaches the UI.
import type { PageReporter } from "./bridge";

export interface NoTileWatchdog {
    // Feed every map error; the first one naming the tile host arms the timer.
    onError(detail: string): void;
    // A tile arrived: the tile host answers, for the rest of the page's life.
    onTile(): void;
}

export function createNoTileWatchdog(deps: {
    // The origin serving the page's tiles. An error naming another origin (a
    // terrain DEM, a hosted style) never arms: it must not rotate the tile host.
    tileHost: string;
    graceMs: number;
    // A style that never loaded is the style-load fatal's to report.
    styleLoaded: () => boolean;
    reporter: Pick<PageReporter, "log" | "report">;
    schedule?: (callback: () => void, delayMs: number) => void;
}): NoTileWatchdog {
    const schedule =
        deps.schedule ??
        ((callback: () => void, delayMs: number) => {
            setTimeout(callback, delayMs);
        });
    // Mutable state in a const holder (let/var are banned — see the lint block
    // in vite.config.ts and no-let.js).
    const state = { tileArrived: false, armed: false };
    return {
        onError(detail: string): void {
            if (state.tileArrived || state.armed || !detail.includes(deps.tileHost)) return;
            state.armed = true;
            schedule(() => {
                if (state.tileArrived || !deps.styleLoaded()) return;
                deps.reporter.log(`tile host unreachable: ${detail}`);
                deps.reporter.report("fatal", `tile-host-unreachable: ${detail}`.slice(0, 200));
            }, deps.graceMs);
        },
        onTile(): void {
            state.tileArrived = true;
        },
    };
}
