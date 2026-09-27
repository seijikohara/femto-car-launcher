// How a map page's load failures are reported, so the host can tell the
// failures a reload cures from the ones it cannot. Pure (timers are injected,
// as in createBearingReporter), so every decision is unit-tested; osm.ts,
// googlemaps.ts and main.ts only wire events into it.
//
// The host (WebMapView.kt, liveReloadRetryDelayMsOrNull) keeps reloading the
// NETWORK kinds — tile-host-unreachable, style-load-failed and
// backend-load-failed — for as long as the outage lasts, and every other kind
// only within a small budget. A page therefore reports a network kind only
// when the data could not be reached: no response at all, a server error, or
// a throttle. A server that answered and refused the request (a mistyped
// style URL's 404, a rejected key's 401/403) is a configuration failure that
// no reload fixes, and so is an exception thrown once the map library has
// loaded — on Google, every reload after the map object exists is a billed
// map load on the user's key.
import type { PageReporter } from "./bridge";

// Whether a failed request's HTTP status (MapLibre's AJAXError `status`) says
// the data could not be reached: 0 is fetch failing without any response
// (offline, DNS, a refused connection, a timeout), 5xx a server error, 408 and
// 429 a server-side timeout and throttle. Every other status is a refusal,
// and null (an error without a status, such as a response that does not
// parse) means the server answered: configuration failures, both.
export function isNetworkStatus(status: number | null): boolean {
    return status === 0 || status === 408 || status === 429 || (status !== null && status >= 500);
}

type Schedule = (callback: () => void, delayMs: number) => void;

function scheduleOrDefault(schedule: Schedule | undefined): Schedule {
    return (
        schedule ??
        ((callback, delayMs) => {
            setTimeout(callback, delayMs);
        })
    );
}

// One outcome gate's evidence: the error that armed it (the fatal's detail)
// and whether any error it counted was a network failure. Mixed signals count
// as network, so the page keeps retrying: stopping during an outage is the
// costly mistake, and a refused request retried once a minute costs nothing.
interface GateEvidence {
    detail: string;
    network: boolean;
}

export interface NoTileWatchdog {
    // Feed every map error with its HTTP status; the first one naming the
    // tile host arms the timer.
    onError(detail: string, status: number | null): void;
    // A tile of [sourceId] arrived: unless that source is served from
    // elsewhere, the tile host answers, for the rest of the page's life.
    onTile(sourceId: string): void;
}

// An unreachable tile host does NOT fail the style load: the bundled styles
// come from appassets and only their sources fail, so the style loads and the
// style-load watchdog never arms. MapLibre fetches a source's TileJSON once
// and, after a failure, marks the source loaded and ignores it for the page's
// life, so a page opened without data sits blank for good. This watchdog arms
// one grace timer on the first error naming the tile host and reports a
// `fatal` only if NO tile has arrived when it fires: tile-host-unreachable,
// or tile-host-rejected when the host refused every request. The fatal is
// what makes the host reload the page (on the next tile host, when there is
// one), with one host as with several. A tile arriving at any point from a
// source the tile host serves stands the watchdog down, so a flaky tile on a
// map that has drawn never reaches the UI.
export function createNoTileWatchdog(deps: {
    // The origin serving the page's tiles. An error naming another origin (a
    // terrain DEM, a hosted style) never arms: it must not rotate the tile host.
    tileHost: string;
    // Sources served from another origin (the terrain DEM): their tiles say
    // nothing about the tile host, so they never stand the watchdog down.
    ignoredSourceIds: readonly string[];
    graceMs: number;
    // A style that never loaded is the style-load watchdog's to report.
    styleLoaded: () => boolean;
    reporter: Pick<PageReporter, "log" | "report">;
    schedule?: Schedule;
}): NoTileWatchdog {
    const schedule = scheduleOrDefault(deps.schedule);
    // Mutable state in a const holder (let/var are banned — see the lint block
    // in vite.config.ts and no-let.js).
    const state = { tileArrived: false, evidence: null as GateEvidence | null };
    return {
        onError(detail: string, status: number | null): void {
            if (state.tileArrived || !detail.includes(deps.tileHost)) return;
            if (state.evidence) {
                state.evidence.network ||= isNetworkStatus(status);
                return;
            }
            const evidence: GateEvidence = { detail, network: isNetworkStatus(status) };
            state.evidence = evidence;
            schedule(() => {
                if (state.tileArrived || !deps.styleLoaded()) return;
                const kind = evidence.network ? "tile-host-unreachable" : "tile-host-rejected";
                deps.reporter.log(`no tile arrived (${kind}): ${evidence.detail}`);
                deps.reporter.report("fatal", `${kind}: ${evidence.detail}`.slice(0, 200));
            }, deps.graceMs);
        },
        onTile(sourceId: string): void {
            if (deps.ignoredSourceIds.includes(sourceId)) return;
            state.tileArrived = true;
        },
    };
}

export interface StyleLoadWatchdog {
    // Feed every map error with its HTTP status; one before the style has
    // loaded arms the timer.
    onError(detail: string, status: number | null): void;
    // The style is in: the map's `load`, any `style.load`, or a painted frame.
    onStyleLoaded(): void;
    // setStyleUrl switched to another style: a fresh load, judged like a cold
    // start. A timer armed for the replaced style has nothing left to judge.
    onStyleSwap(): void;
    styleLoaded(): boolean;
}

// An error before the style has loaded CAN mean the style fetch itself failed,
// and MapLibre never re-fetches a failed style, so the map would stay blank
// for good. But a pre-load error can also be a single flaky request on an
// otherwise healthy load, so the fatal is outcome-gated, not message-gated:
// one grace timer, and a fatal only if the style has STILL not loaded when it
// fires — style-load-failed, or style-load-rejected when the server refused
// the style (a mistyped custom URL's 404).
export function createStyleLoadWatchdog(deps: {
    graceMs: number;
    reporter: Pick<PageReporter, "log" | "report">;
    schedule?: Schedule;
}): StyleLoadWatchdog {
    const schedule = scheduleOrDefault(deps.schedule);
    const state = {
        styleLoaded: false,
        // Bumped by every swap, retiring the timers armed before it.
        generation: 0,
        evidence: null as GateEvidence | null,
    };
    return {
        onError(detail: string, status: number | null): void {
            if (state.styleLoaded) return;
            if (state.evidence) {
                state.evidence.network ||= isNetworkStatus(status);
                return;
            }
            const evidence: GateEvidence = { detail, network: isNetworkStatus(status) };
            state.evidence = evidence;
            const generation = state.generation;
            schedule(() => {
                if (generation !== state.generation || state.styleLoaded) return;
                const kind = evidence.network ? "style-load-failed" : "style-load-rejected";
                deps.reporter.log(`style never loaded (${kind}): ${evidence.detail}`);
                deps.reporter.report("fatal", `${kind}: ${evidence.detail}`.slice(0, 200));
            }, deps.graceMs);
        },
        onStyleLoaded(): void {
            state.styleLoaded = true;
        },
        onStyleSwap(): void {
            state.styleLoaded = false;
            state.generation += 1;
            state.evidence = null;
        },
        styleLoaded: () => state.styleLoaded,
    };
}

// Thrown by a backend whose map library's script could not be fetched: the
// one init failure that a reload cures once data is back.
export class ScriptLoadError extends Error {}

// The fatal detail for a failure that escaped a backend's init. Only a script
// that could not be fetched is backend-load-failed, a network kind. Anything
// else — an exception thrown once the library has loaded, or a failed import
// of the page's own chunk, which is a local asset — is map-init-exception,
// which the host retries only within its budget.
export function initFailureDetail(error: unknown): string {
    const message = error instanceof Error ? error.message : String(error);
    const kind = error instanceof ScriptLoadError ? "backend-load-failed" : "map-init-exception";
    return `${kind}: ${message}`.slice(0, 200);
}
