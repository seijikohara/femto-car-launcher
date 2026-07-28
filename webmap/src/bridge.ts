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
        ) => void;
        setFeatures: (buildings: boolean, terrain: boolean, buildingColor: string) => void;
        // Mapbox only.
        setMapboxStyle: (styleId: string, lightPreset: "day" | "night", traffic: boolean) => void;
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
export type MapEventKind = "ready" | "fatal" | "error" | "follow" | "bearing";

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
// (Mapbox appends the BYO access_token to style/tile URLs; Google appends
// key). Redact them before a detail leaves the page: the host logs details
// to logcat in EVERY build, and the debug notice prints them on screen. A
// pk. public token is client-embedded by design, but a screenshot or a log
// capture must not hand it out verbatim.
const SECRET_QUERY_PARAM = /([?&](?:access_token|api_?key|key|token)=)[^&#\s"']+/gi;

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
// run, but the backend chunk behind the dynamic import — and the Mapbox UMD /
// Google CDN loads inside it — resolve later. The stubs record the LATEST
// call per function; the backend module replays them after installing the
// real implementations (each in its own push order).
export interface PendingBridgeCalls {
    updateCamera: Parameters<Window["updateCamera"]> | null;
    setStyleUrl: Parameters<Window["setStyleUrl"]> | null;
    setFeatures: Parameters<Window["setFeatures"]> | null;
    setMapboxStyle: Parameters<Window["setMapboxStyle"]> | null;
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
        setMapboxStyle: null,
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
    window.setMapboxStyle = (...a) => {
        pending.setMapboxStyle = a;
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
// and mapbox-gl 3 both hard-require webgl2 and render nothing without it
// (MapLibre dropped its WebGL 1 fallback in 6; mapbox-gl made webgl2 mandatory
// in 3.0). A Google raster map needs no WebGL at all — its tiles are rendered
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
