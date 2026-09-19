// Android <-> JS bridge plumbing shared by every backend module: the Window
// surface the host drives via evaluateJavascript, the femtoBridge event
// channel back into Kotlin, per-page logging, error throttling, and the
// pending-call stubs that buffer host pushes until the backend module has
// loaded. WebMapView.kt is the host side of every contract in this file.

// Android -> JS surface, called by the host through evaluateJavascript. Every
// function is feature-detected on the Kotlin side (`window.updateCamera && ...`),
// so the names below are a compatibility contract — never rename without
// updating WebMapView.kt. The full surface is declared once here; each backend
// module installs the subset it implements (the boot stubs install all of
// them, so the host never calls into a hole).
declare global {
    interface Window {
        femtoBridge?: { onMapEvent(kind: string, detail: string): void };
        updateCamera: (
            lat: number,
            lon: number,
            bearing: number,
            zoom: number,
            tilt: number,
            markerPos: number,
            bottomSafe: number,
            rightSafe: number,
            leftSafe: number,
            markerColor: string,
        ) => void;
        // OSM (MapLibre) only.
        setStyleUrl: (
            url: string,
            bg: string,
            water: string,
            land: string,
            roadMajor: string,
            roadMinor: string,
            roadCasing: string,
            building: string,
            label: string,
            // Whether the page draws MapLibre's attribution control for this
            // style: true for a user-supplied style whose credits the host
            // cannot know, false when the host's own overlay shows them.
            pageAttribution: boolean,
        ) => void;
        setFeatures: (buildings: boolean, terrain: boolean, buildingColor: string) => void;
        // Google Maps only.
        setGoogleMapsOptions: (mapType: string, traffic: boolean) => void;
        // Callback the Google Maps JS API invokes when authentication fails;
        // installed by the googlemaps backend before its bootstrap loader.
        gm_authFailure?: () => void;
        // Shared by all backends.
        onHostResume: () => void;
        setFollow: (follow: boolean) => void;
        setNorthUp: (enabled: boolean) => void;
    }
}

// JS -> Android event kinds. "fatal" = definitive never-going-to-render facts
// (no WebGL context, a missing BYO credential, map construction threw);
// "error" = transient resource failures (tile / style / DEM fetch), log-only
// on the host; "follow" = camera-follow state flips; "bearing" = throttled
// camera bearing for the compass overlay. `ready` marks the first frame the
// backend actually painted — the host records it for the MAP diagnostics
// section, which otherwise cannot tell a working map from one that failed
// silently. No kind triggers a backend switch — the host keeps the chosen
// backend (no auto-fallback).
export type MapEventKind = "ready" | "fatal" | "error" | "follow" | "bearing" | "frames";

export interface PageReporter {
    // Diagnostic logging only (visible via chrome://inspect or the debug
    // build's WebChromeClient); prefixed per backend for logcat greppability.
    log(msg: string): void;
    report(kind: MapEventKind, detail: unknown): void;
    // Tile / style fetch failures (and uncaught page errors) can fire per
    // frame on a flaky link, so reports to the host are throttled.
    reportErrorThrottled(detail: string): void;
}

const ERROR_REPORT_INTERVAL_MS = 10_000;

// Credential query parameters ride the provider URLs inside error strings
// (Google appends the BYO key to its script and tile URLs; the pattern is
// deliberately provider-agnostic). Redact them before a detail leaves the
// page: the host logs details to logcat in EVERY build, and the debug notice
// prints them on screen. A client-side key is embedded by design, but a
// screenshot or a log capture must not hand it out verbatim.
// Hyphen and underscore spellings both occur across tile providers.
const SECRET_QUERY_PARAM =
    /([?&](?:access[-_]?token|api[-_]?key|subscription[-_]?key|key|token)=)[^&#\s"']+/gi;

export function redactSecrets(detail: string): string {
    return detail.replace(SECRET_QUERY_PARAM, "$1<redacted>");
}

// Module-scoped (it captures nothing per reporter): the one funnel for every
// JS -> Android event.
function reportToHost(kind: MapEventKind, detail: unknown): void {
    try {
        window.femtoBridge?.onMapEvent(kind, redactSecrets(String(detail ?? "")));
    } catch {
        // The bridge may be absent outside the launcher (e.g. vp dev).
    }
}

export function createReporter(prefix: string): PageReporter {
    // Mutable throttle clock in a const holder (let/var are banned — see the
    // lint block in vite.config.ts and no-let.js).
    const throttle = { lastMs: 0 };
    return {
        log(msg: string): void {
            try {
                // Redacted like reportToHost: WebView console lines reach
                // logcat in every build.
                console.log(`[${prefix}] ${redactSecrets(msg)}`);
            } catch {
                // Logging must never break the page.
            }
        },
        report: reportToHost,
        reportErrorThrottled(detail: string): void {
            const now = Date.now();
            if (now - throttle.lastMs >= ERROR_REPORT_INTERVAL_MS) {
                throttle.lastMs = now;
                reportToHost("error", detail.slice(0, 200));
            }
        },
    };
}

// The camera-bearing report for the host's compass overlay: one implementation
// for both backends, fed the current bearing from each camera-change event
// (MapLibre's "move", Google's "heading_changed").
//
// Throttled to this interval so a rotating camera does not hand the host a
// bridge call per frame — but with the LAST value always delivered: an event
// arriving inside the interval is held and sent when the interval ends, if
// nothing newer has been sent by then. A plain leading-edge throttle drops
// the final frame of a rotation, and a source that fires only on change
// (Google's heading_changed) never sends another event to repair it, so the
// compass would rest a few degrees off wherever the last dropped frame left
// the map — after a north-up flip, visibly off north. Deduped on the rounded
// payload, not the raw float: getters rarely return bit-identical values, so
// a float compare would re-send visually identical bearings.
export const BEARING_REPORT_INTERVAL_MS = 150;

export function createBearingReporter(
    report: (kind: MapEventKind, detail: unknown) => void,
    deps: {
        now?: () => number;
        schedule?: (callback: () => void, delayMs: number) => void;
    } = {},
): (bearingDeg: number) => void {
    const now = deps.now ?? (() => Date.now());
    const schedule =
        deps.schedule ??
        ((callback: () => void, delayMs: number) => {
            setTimeout(callback, delayMs);
        });
    // Mutable throttle state in a const holder (let/var are banned — see the
    // lint block in vite.config.ts and no-let.js). held is the bearing
    // waiting for the interval to end; armed, whether a timer is pending for
    // it; generation retires that timer when an immediate report supersedes
    // it — a timer can run late, after such a report, and must not then
    // send a value held since, inside the new interval.
    const state = {
        lastMs: 0,
        lastSent: "",
        held: null as string | null,
        armed: false,
        generation: 0,
    };
    function send(bearing: string, atMs: number): void {
        state.lastMs = atMs;
        state.lastSent = bearing;
        report("bearing", bearing);
    }
    return (bearingDeg: number): void => {
        const bearing = bearingDeg.toFixed(1);
        const atMs = now();
        const elapsed = atMs - state.lastMs;
        if (elapsed >= BEARING_REPORT_INTERVAL_MS) {
            // A held value is stale next to this one, and so is the timer
            // waiting to send it; the next held value arms a fresh one.
            state.held = null;
            state.generation += 1;
            state.armed = false;
            if (bearing !== state.lastSent) send(bearing, atMs);
            return;
        }
        state.held = bearing;
        if (state.armed) return;
        state.armed = true;
        const generation = state.generation;
        schedule(() => {
            if (generation !== state.generation) return;
            state.armed = false;
            const held = state.held;
            state.held = null;
            if (held !== null && held !== state.lastSent) send(held, now());
        }, BEARING_REPORT_INTERVAL_MS - elapsed);
    };
}

// Route uncaught exceptions and unhandled rejections through the throttled
// channel: a post-init exception inside a bridge call would otherwise reach
// only the JS console, invisible on an adb-unreachable head unit.
export function installGlobalErrorHooks(reporter: PageReporter): void {
    window.addEventListener("error", (e) => {
        reporter.log(`uncaught: ${e.message}`);
        reporter.reportErrorThrottled(e.message || "uncaught error");
    });
    window.addEventListener("unhandledrejection", (e) => {
        const reason = e.reason instanceof Error ? e.reason.message : String(e.reason);
        reporter.log(`unhandledrejection: ${reason}`);
        reporter.reportErrorThrottled(reason);
    });
}

// Pending bridge calls recorded by the boot stubs so host pushes arriving
// before the backend module has loaded are not silently dropped:
// `onPageFinished` (which gates host pushes) fires when the entry module has
// run, but the backend chunk behind the dynamic import — and the Google CDN
// load inside it — resolve later. The stubs record the LATEST
// call per function; the backend module replays them after installing the
// real implementations (each in its own push order).
export interface PendingBridgeCalls {
    updateCamera: Parameters<Window["updateCamera"]> | null;
    setStyleUrl: Parameters<Window["setStyleUrl"]> | null;
    setFeatures: Parameters<Window["setFeatures"]> | null;
    setGoogleMapsOptions: Parameters<Window["setGoogleMapsOptions"]> | null;
    setFollow: boolean | null;
    setNorthUp: boolean | null;
    onHostResume: boolean;
}

export function installPendingStubs(): PendingBridgeCalls {
    const pending: PendingBridgeCalls = {
        updateCamera: null,
        setStyleUrl: null,
        setFeatures: null,
        setGoogleMapsOptions: null,
        setFollow: null,
        setNorthUp: null,
        onHostResume: false,
    };
    window.updateCamera = (...a) => {
        pending.updateCamera = a;
    };
    window.setStyleUrl = (...a) => {
        pending.setStyleUrl = a;
    };
    window.setFeatures = (...a) => {
        pending.setFeatures = a;
    };
    window.setGoogleMapsOptions = (...a) => {
        pending.setGoogleMapsOptions = a;
    };
    window.setFollow = (f) => {
        pending.setFollow = f;
    };
    window.setNorthUp = (e) => {
        pending.setNorthUp = e;
    };
    window.onHostResume = () => {
        pending.onHostResume = true;
    };
    return pending;
}

// WebGL availability probe. Each backend applies its own policy: maplibre-gl 6
// hard-requires webgl2 and renders nothing without it (MapLibre dropped its
// WebGL 1 fallback in 6). A Google raster map needs no WebGL at all — its tiles are rendered
// server-side — while a Google VECTOR map treats webgl2 as its bar too, but
// degrades to raster instead of failing. One canvas per probe: a canvas locks to
// its first context mode, so asking one canvas for webgl2 then webgl would
// report a false webgl1=false on every WebGL2-capable device.
export function webglSupport(): { webgl2: boolean; webgl1: boolean } {
    return {
        webgl2: document.createElement("canvas").getContext("webgl2") != null,
        webgl1: document.createElement("canvas").getContext("webgl") != null,
    };
}

// The GPU (or software rasteriser) behind the page's WebGL, as the WebView
// unmasks it — "SwiftShader" means Chromium blocklisted the device's driver
// and every WebGL map renders on the CPU, which no camera tuning can make
// smooth. Reported to the host as the `ready` detail for the diagnostics
// report; empty when no context can be created at all.
export function webglRenderer(): string {
    const gl =
        document.createElement("canvas").getContext("webgl2") ??
        document.createElement("canvas").getContext("webgl");
    if (!gl) return "";
    try {
        // Chrome unmasks RENDERER itself since 101; older WebViews still hide
        // it behind the debug extension.
        const info = gl.getExtension("WEBGL_debug_renderer_info");
        const value = info
            ? gl.getParameter(info.UNMASKED_RENDERER_WEBGL)
            : gl.getParameter(gl.RENDERER);
        return String(value ?? "");
    } catch {
        return "";
    }
}

// How often the page samples its own frame cadence, and how many consecutive
// animation frames one sample spans. A burst rather than a permanent rAF
// loop: a frame request every frame would keep the compositor awake (and
// cost CPU) on a page that has nothing to animate, whereas a burst of thirty
// every ten seconds is a ~10% duty cycle that still catches a renderer that
// has fallen to one frame per second.
export const FRAME_SAMPLE_PERIOD_MS = 10_000;
export const FRAME_SAMPLE_FRAMES = 30;

// The median and worst of a run of frame intervals, as the `frames` event
// detail the host parses: "median=<ms>,worst=<ms>,samples=<n>".
export function frameSampleDetail(intervalsMs: number[]): string {
    if (intervalsMs.length === 0) return "median=0,worst=0,samples=0";
    // Rank selection rather than a sort: Array#toSorted is past the WebView
    // floor (Chrome 110) and the lint bans the mutating Array#sort. Thirty
    // samples make the quadratic pass free. The median is the value holding
    // rank floor(n / 2) — the one with that many samples strictly below it,
    // ties included at its own rank.
    const target = Math.floor(intervalsMs.length / 2);
    const median = intervalsMs.reduce((best, x) => {
        const below = intervalsMs.filter((y) => y < x).length;
        const equal = intervalsMs.filter((y) => y === x).length;
        return below <= target && target < below + equal ? x : best;
    }, intervalsMs[0]);
    const worst = Math.max(...intervalsMs);
    return `median=${Math.round(median)},worst=${Math.round(worst)},samples=${intervalsMs.length}`;
}

// Sample the page's achieved frame interval on its own renderer thread and
// report it to the host for the diagnostics report. The host's UI-frame
// statistics measure the launcher's Compose frames, which stay on time while
// the WebView's renderer is saturated by a heavy map — this is the number
// that shows that. requestAnimationFrame only fires while the page is
// visible, so a hidden page simply pauses mid-burst and resumes with it.
export function startFrameSampler(report: (kind: MapEventKind, detail: string) => void): void {
    const burst = { last: 0, intervals: [] as number[] };
    const onFrame = (now: number): void => {
        if (burst.last > 0) burst.intervals.push(now - burst.last);
        burst.last = now;
        if (burst.intervals.length < FRAME_SAMPLE_FRAMES) {
            requestAnimationFrame(onFrame);
            return;
        }
        report("frames", frameSampleDetail(burst.intervals));
        burst.intervals = [];
        burst.last = 0;
        setTimeout(() => requestAnimationFrame(onFrame), FRAME_SAMPLE_PERIOD_MS);
    };
    requestAnimationFrame(onFrame);
}
