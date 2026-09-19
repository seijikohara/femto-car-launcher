// Google Maps JS API backend module, dynamically imported by main.ts when
// the host loads index.html?backend=googlemaps.
//
// The Google Maps JS API is loaded at runtime from Google's CDN via the
// official @googlemaps/js-api-loader npm package — the loading path Google
// recommends (developers.google.com/maps/documentation/javascript/libraries-open-source).
// Only the LOADER is bundled; the API itself must never be bundled or
// self-hosted (Google Maps Platform ToS). The key comes from the Android
// bridge at runtime rather than being baked in at build time.
//
// Two render modes, chosen by whether the user supplied a Cloud Map ID
// (femtoBridge.googleMapsMapId()):
//   - VECTOR (Map ID present): full heading-up rotation, tilt, and 3D —
//     parity with the OSM backend. The map rotates in heading-up; the
//     chevron rotates in north-up.
//   - RASTER (no Map ID): north-up only. A raster map cannot rotate or tilt,
//     and passing heading/tilt to moveCamera stops the camera from
//     positioning, so the map stays north-up and the chevron always rotates
//     to the travel bearing to convey heading. (The raster map's own 45°
//     aerial-imagery mode, which would tilt and turn it on its own, is
//     switched off at construction.)
//
// Like the OSM backend, the screen-pinned chevron sits left-of-centre
// (and drops with markerPos) to clear the side cards / bottom overlay, and
// the camera targets an off-centre point so the GPS location renders under
// the chevron. Google Maps has no camera `padding` (unlike MapLibre),
// so that off-centre target is computed from the flat-Mercator projection,
// un-rotated by the map heading — see offsetCenterFor. Tilt is not modelled,
// so a tilted vector camera offsets approximately; a raster (north-up,
// no tilt) camera offsets exactly.
//
// This backend does NOT use the shared follow-camera engine: Google's camera
// API is immediate (moveCamera has no easing, and there is no easeTo), so the
// page interpolates the camera itself frame by frame (camera-glide.ts, the
// loop Google's own vector-map guidance animates the camera with) under the
// same duration policy as the MapLibre engine; camera-change events carry no
// user-vs-programmatic flag (suppression windows stand in for originalEvent
// gating); and there is no mapId-free, non-deprecated geo marker for the
// detached mode — the chevron simply hides while detached (a geo-anchored
// OverlayView is the documented follow-up). It shares the chevron helpers,
// the bridge plumbing, the reflow lockstep, and the camera.ts / style.ts
// pure math.
import { importLibrary, setOptions } from "@googlemaps/js-api-loader";
import type { PageReporter, PendingBridgeCalls } from "../bridge";
import { createBearingReporter, webglRenderer, webglSupport } from "../bridge";
import {
    AUTO_REFOLLOW_MS,
    type CameraMotion,
    DETACHED_ZOOM_STEP_MOTION,
    followMotion,
    heldHeading,
    isPaddingOnlyReflow,
    isRealPosition,
    LAYOUT_REFLOW_MS,
    LOCATION_STALE_THRESHOLD_MS,
    NO_HEADING_HOLD,
    ORIENTATION_FLIP_MOTION,
    REFLOW_MOTION,
    REFOLLOW_MOTION,
    smoothedBearing,
} from "../camera";
import { type CameraPose, createCameraGlide } from "../camera-glide";
import { chevronHandles, setChevronColor, setChevronTransform, startStaleTicker } from "../chevron";
import { createMarkerTransition } from "../marker-motion";
// Shared self-marker offset model with the OSM backend (style.ts is
// the SSOT): how far left of centre the chevron sits to clear the side cards,
// and how far it drops with markerPos to clear the bottom overlay.
import { markerDrop, markerXFraction } from "../style";

// The Google Maps bridge extends the base femtoBridge with googleMapsApiKey()
// and googleMapsMapId(), present only when the host has wired up the Google
// Maps backend.
interface GoogleMapsFemtoBridge {
    onMapEvent(kind: string, detail: string): void;
    // Synchronous getter injected by the host; returns the Google Maps API
    // key. Returns an empty string when unconfigured.
    googleMapsApiKey(): string;
    // Synchronous getter injected by the host; returns the Cloud Map ID the
    // user supplied, or "" when unconfigured. Independent of the rendering
    // mode below (Maps JS 3.56.10+ renders vector without a Map ID); the Map ID
    // still gates advanced markers and cloud styling.
    googleMapsMapId(): string;
    // Synchronous getter injected by the host; the user's rendering choice as
    // the Kotlin enum name: "AUTO" | "RASTER" | "VECTOR".
    googleMapsRendering(): string;
    // Synchronous getter injected by the host; the map's light/dark context
    // resolved by the host from the Map style setting and the app theme, as
    // a google.maps.ColorScheme name: "LIGHT" | "DARK". Construction-time
    // only, so a change rebuilds the page (see WebMapView's effectiveGoogleDark).
    googleMapsColorScheme(): string;
}

// Minimal type stubs for the Google Maps JS API (CDN-loaded at runtime,
// never bundled). These cover only the surface this module exercises — kept
// narrow and local on purpose, decoupled from the full weekly-channel
// @types/google.maps global surface the loader package pulls in; the one
// boundary cast sits at the importLibrary call.
interface GMLatLng {
    lat: number;
    lng: number;
}
// heading/tilt are sent only for a VECTOR map (Cloud Map ID present); a
// RASTER map rejects them (passing either stops moveCamera from positioning).
interface GMCameraOptions {
    center?: GMLatLng;
    zoom?: number;
    heading?: number;
    tilt?: number;
}
interface GMMapsEventListener {
    remove(): void;
}
interface GMPoint {
    x: number;
    y: number;
}
// google.maps.LatLng (method accessors), as returned by
// Projection.fromPointToLatLng — distinct from the GMLatLng literal we pass
// into moveCamera.
interface GMLatLngObj {
    lat(): number;
    lng(): number;
}
// google.maps.Projection: the flat-Mercator world projection (heading/tilt
// independent), used to compute the off-centre camera target for the
// chevron's left/down placement.
interface GMProjection {
    fromLatLngToPoint(latLng: GMLatLng): GMPoint | null;
    fromPointToLatLng(pixel: GMPoint): GMLatLngObj | null;
}
interface GMMap {
    moveCamera(opts: GMCameraOptions): void;
    setMapTypeId(id: string): void;
    // The camera the map shows now — where every glide starts from.
    getCenter(): GMLatLngObj | undefined;
    getZoom(): number | undefined;
    getHeading(): number | undefined;
    getTilt(): number | undefined;
    // Null until the projection is ready (first idle); offsetCenterFor falls
    // back to the un-offset centre until then.
    getProjection(): GMProjection | null;
    // "VECTOR" | "RASTER" | "UNINITIALIZED". Google may silently downgrade a
    // VECTOR map to RASTER when the device's WebGL cannot host it.
    getRenderingType(): string;
    addListener(event: string, handler: () => void): GMMapsEventListener;
}
interface GMTrafficLayer {
    setMap(map: GMMap | null): void;
}
interface GMMapsLibrary {
    Map: new (el: HTMLElement, opts: Record<string, unknown>) => GMMap;
    TrafficLayer: new () => GMTrafficLayer;
}
interface GMNamespace {
    // google.maps.Point constructor — Projection.fromPointToLatLng requires a
    // Point instance (it does not accept a literal).
    Point: new (x: number, y: number) => GMPoint;
}

function gmBridge(): GoogleMapsFemtoBridge | undefined {
    return window.femtoBridge as GoogleMapsFemtoBridge | undefined;
}

// Typed accessor for the CDN-injected google.maps namespace.
function gmapsNS(): GMNamespace {
    return (window as unknown as { google: { maps: GMNamespace } }).google.maps;
}

// Maps the GoogleMapType enum name strings from the Android bridge to the
// Google Maps JS API map type id strings.
const MAP_TYPE_IDS: Record<string, string> = {
    ROADMAP: "roadmap",
    SATELLITE: "satellite",
    HYBRID: "hybrid",
    TERRAIN: "terrain",
};

// Suppression window after a programmatic camera change: Google Maps fires
// camera-change events for BOTH programmatic moves and user gestures, with no
// originalEvent flag to tell them apart. Change events inside the window are
// treated as programmatic (no follow detach), and events after it as user
// gestures. moveCamera is immediate (no animation), so its events fire well
// within the window while user input between them does not.
//
// One window PER PROPERTY (zoom / heading / tilt), opened only by a frame
// that changes that property: the glide calls moveCamera every frame while
// following, so a single window opened by every call would never close on
// the move and a pinch-zoom would go undetected for as long as the car is
// moving. A pure translation (a straight road) opens nothing, and a turn
// opens only the heading window, so a user zoom stays detectable through
// both. The blind spot that remains is a pinch during the second or two a
// zoom step itself glides — head units have no multitouch, so it is accepted.
//
// The window outlasts a late frame by a wide margin: should the API defer a
// change event to its next render, a frame that stalls on a head unit (the
// diagnostics' worst frame interval) must still land inside it, or a turn
// would detach the follow mid-corner. The per-property split is what keeps a
// window this wide from hiding a user zoom.
const GESTURE_SUPPRESS_MS = 200;

export async function init(reporter: PageReporter, pending: PendingBridgeCalls): Promise<void> {
    const { log, report } = reporter;

    const key = gmBridge()?.googleMapsApiKey?.() ?? "";
    if (!key) {
        log("google-maps-no-key");
        report("fatal", "google-maps-no-key");
        return;
    }

    const mapId = gmBridge()?.googleMapsMapId?.() ?? "";

    // The user's explicit choice. AUTO passes no renderingType, leaving the Map
    // ID's cloud configuration in charge (and, with no Map ID, the API's own
    // RASTER default); RASTER / VECTOR are passed through and OVERRIDE that
    // configuration. Vector is only ever a request: the API silently falls back
    // to raster on a device that cannot host it, which the tilesloaded handler
    // below detects. Requesting vector no longer needs a Map ID.
    const rendering = gmBridge()?.googleMapsRendering?.() ?? "AUTO";
    // The OSM backend recolours its style per theme; Google's equivalent is
    // the colorScheme option, which the API only honours at construction. A
    // Map ID's cloud style takes over from it only if the user has associated
    // a dark-mode style with that Map ID in the Cloud console.
    const colorScheme = gmBridge()?.googleMapsColorScheme?.() ?? "LIGHT";
    // Only an EXPLICIT vector choice is a vector request. AUTO's outcome lives in
    // the Map ID's cloud configuration, which the page cannot read, so guessing
    // "Map ID means vector" would drive a raster-configured Map ID as vector —
    // pushing heading/tilt at a map that reinterprets them, and failing the WebGL
    // gate below on a device where Google would have rendered raster quite
    // happily. AUTO therefore starts raster and the tilesloaded handler resolves
    // it from getRenderingType(), which is the only authoritative answer.
    const wantsVector = rendering === "VECTOR";

    // A raster map is server-rendered pixel tiles and needs no WebGL, so a
    // missing context there is logged only — a premature fatal would blank a
    // working map.
    //
    // A vector map is drawn client-side on the GPU, and Google's own bar for it
    // is WebGL 2 (their support page tells you to test `getContext("webgl2")`).
    // Google does NOT fail without it: a vector Map ID silently falls back to
    // raster. So this fatal is a deliberate product choice, not a technical
    // necessity — we would rather tell the user their vector opt-in cannot be
    // honoured than hand them a flat north-up map with no explanation.
    //
    // KNOWN GAPS, deliberately left as-is here:
    //  - the gate accepts webgl1, so a WebGL-1-only device passes and then gets
    //    Google's silent raster downgrade — the very outcome the fatal exists to
    //    avoid;
    //  - the downgrade is sampled once at the first `tilesloaded` rather than
    //    subscribed via Google's `renderingtype_changed`, so a later switch
    //    (e.g. a dynamic-GPU handover) goes unnoticed.
    const gl = webglSupport();
    if (!gl.webgl2 && !gl.webgl1) {
        if (wantsVector) {
            // Report and stop: loading the API for a page the host is about to
            // tear down is wasted work.
            log("no-webgl-context");
            report("fatal", "no-webgl-context");
            return;
        }
        log("no-webgl-context (raster map renders without WebGL)");
    }

    // Mutable page state in one const holder (let/var are banned — see the
    // lint block in vite.config.ts and no-let.js). All camera pushes and API
    // ops read+write through here.
    const state = {
        map: undefined as GMMap | undefined,
        trafficLayer: null as GMTrafficLayer | null,
        // What the page currently believes it is rendering: VECTOR unlocks
        // heading-up rotation + tilt, RASTER is flat and north-up. Seeded from
        // the request and corrected from getRenderingType() once tiles land.
        isVector: wantsVector,
        // Set to true on the first tilesloaded event; de-dupes that handler,
        // which fires on every tile batch.
        rendered: false,
        // One-shot latch for the auth fatal: gm_authFailure fires per rejected
        // request, and the host counts every fatal it is told about, so a single
        // dead key must not inflate the diagnostics failure count.
        authFailed: false,
        following: true,
        refollowTimer: 0 as ReturnType<typeof setTimeout> | 0,
        // North-up vs heading-up; only meaningful on a VECTOR map (a raster
        // map is always north-up). The chevron and the vector-map camera read
        // this.
        northUp: false,
        lastBearing: null as number | null,
        // The heading the vector map is rotated to, with the dead band's
        // state: the smoothed bearing passes through heldHeading, so the map
        // only rotates on a real turn (and, once, to settle a residual that
        // persists on a straight road). Reset to NO_HEADING_HOLD whenever
        // the next fix should adopt the bearing outright: a signal gap
        // (updateCamera), and every easeHome — a re-follow, a north-up flip,
        // a rendering-mode resolve. The one list of reset points.
        headingHold: NO_HEADING_HOLD,
        lastFixMs: 0,
        lastPushedZoom: 0,
        // The first camera placement snaps into position (no fly-in from the
        // [0,0] construction centre); the rest glide.
        firstCamera: true,
        // See GESTURE_SUPPRESS_MS: the wall-clock ms each property's window
        // is open until.
        programmaticUntil: { zoom: 0, heading: 0, tilt: 0 },
        // The zoom / heading / tilt last passed to moveCamera, so a frame
        // can tell which properties it actually changes; null until the
        // first move.
        lastSet: { zoom: null, heading: null, tilt: null } as Record<
            "zoom" | "heading" | "tilt",
            number | null
        >,
        // Whether the last camera placement could offset the target for the
        // chevron's spot (the projection was ready). A change moves the
        // chevron without a new fix, so it glides in lockstep with the
        // camera like a layout reflow does.
        offsetApplied: false,
        // tilt is used only on a VECTOR map; a raster map ignores it.
        // markerPos / bottomSafe / rightSafe / leftSafe are the host's
        // safe-zone fractions, kept so a re-follow (easeHome) reproduces the
        // same chevron/camera offset as the live updateCamera push.
        lastFix: null as {
            lat: number;
            lng: number;
            heading: number;
            zoom: number;
            tilt: number;
            markerPos: number;
            bottomSafe: number;
            rightSafe: number;
            leftSafe: number;
        } | null,
    };

    const chevron = chevronHandles();
    const markerEl = chevron.el;
    // Lockstep control for a layout reflow — see isPaddingOnlyReflow and
    // marker-motion.ts; the same arrangement as the shared follow engine.
    const markerTransition = createMarkerTransition(markerEl, LAYOUT_REFLOW_MS);

    // VECTOR: heading-up rotates the MAP to [mapHeading] and the chevron turns
    // by whatever the dead band left unrotated, so the arrow still points along
    // the true travel [heading]; north-up rotates the chevron by the heading
    // alone. The perspective lays it onto the tilted ground plane. RASTER: the
    // map is permanently north-up, so the chevron always rotates to the travel
    // bearing and there is no tilt plane.
    function syncChevron(tilt: number, heading: number, mapHeading: number): void {
        if (state.isVector) {
            setChevronTransform(
                markerEl,
                tilt,
                state.northUp ? heading : heading - mapHeading,
                true,
            );
            return;
        }
        setChevronTransform(markerEl, 0, heading, false);
    }

    // The heading the vector map is rotated to for a fix: north-up pins it at
    // 0; heading-up passes the smoothed bearing through the dead band and
    // records what was applied, so the next fix can hold it.
    function mapHeadingFor(heading: number): number {
        if (state.northUp) return 0;
        const hold = heldHeading(state.headingHold, heading, Date.now());
        state.headingHold = hold;
        return hold.applied;
    }

    // gm_authFailure is Google's global hook for invalid/revoked API keys.
    // Install it before the loader fetches the API so it is in place before
    // any authentication attempt. Reported fatal whether or not the map has
    // already rendered: unlike a dropped tile, this hook fires only when
    // Google rejects the key, and a key suspended or unfunded mid-drive
    // otherwise leaves a frozen map with nothing on screen to explain it.
    //
    // The host's notice names the key as the thing to check and its retry
    // budget bounds the reloads that follow — but the budget is refunded on
    // every offline->online edge, so a flapping link can restart the ladder.
    // That is the accepted cost of reporting it: a rate-limited key (which is
    // one way to reach this hook) can now tear down a map that was rendering.
    // The latch keeps one dead key from being counted as many failures.
    window.gm_authFailure = () => {
        log("gm_authFailure");
        if (state.authFailed) return;
        state.authFailed = true;
        report("fatal", "google-maps-auth");
    };

    // Load the Maps JS API from Google's CDN through the official loader
    // package with the runtime key. A rejected import (network, blocked CDN)
    // propagates to the boot module's catch, which reports a fatal the host
    // can auto-retry. The cast narrows the loader's full google.maps typing
    // to the local stubs above.
    setOptions({ key, v: "weekly" });
    const mapsLib = (await importLibrary("maps")) as unknown as GMMapsLibrary;

    const mapEl = document.getElementById("map");
    if (!mapEl) {
        report("fatal", "google-maps-no-container");
        return;
    }

    // Size the container with EXPLICIT pixels before constructing the map.
    // Google Maps lays out its inner containers with height:100%, which only
    // resolves against a DEFINITE container height. An auto-height box — the
    // bare position:fixed;inset:0 the GL backends are happy with (their
    // libraries measure the element themselves) — leaves Google's inner divs
    // at 0px tall and the map renders permanently blank. Explicit pixel
    // width/height make the box definite (they win over the inset shorthand's
    // right/bottom in the over-constrained resolution).
    // window.innerWidth/Height are reliable at module-load time even though
    // <body> has not been laid out yet. Re-apply on resize so the map tracks
    // orientation changes (Google Maps observes the container size).
    const sizeContainer = () => {
        mapEl.style.width = `${window.innerWidth}px`;
        mapEl.style.height = `${window.innerHeight}px`;
    };
    sizeContainer();
    window.addEventListener("resize", sizeContainer);

    // disableDefaultUI suppresses the control buttons only; the Google logo
    // and attribution text render regardless and must remain visible at all
    // times (Google Maps ToS). Do not attempt to hide or reposition the
    // attribution. This makes Google the one exception to the launcher's
    // bottom-left credit convention (see .claude/rules/webmap.md): the Maps
    // JS API fixes the logo bottom-left but the copyright / ToS text
    // bottom-right and exposes no supported way to move it, so the split
    // stays as Google places it.
    //
    // renderingType is only sent for an explicit RASTER / VECTOR choice, since
    // sending it overrides the Map ID's cloud configuration — which is exactly
    // what AUTO must not do. The Map ID rides along whenever it is set: it is
    // independent of the rendering mode and still gates advanced markers and
    // cloud styling, so a raster map keeps it too.
    //
    // heading/tilt are vector-only. On a raster map the API reinterprets them
    // rather than rejecting them (heading applies to aerial imagery and snaps to
    // available angles; tilt takes only 0 or 45 as an imagery-switching policy),
    // and passing them stops moveCamera from positioning — so they are omitted
    // entirely. headingInteractionEnabled stays false because heading is driven
    // solely by the host (north-up vs heading-up), never by user rotation
    // gestures.
    //
    // isFractionalZoomEnabled defaults to false on a raster map, which would
    // round the glide's in-between zoom values and turn a zoom step into a
    // mid-way jump; the host's zoom setting is an integer, so at rest the
    // raster map renders exactly as before.
    //
    // tilt: 0 is passed in both modes. On a vector map it is the flat start
    // the first push tilts from. On a raster map the option means something
    // else — it switches off the automatic 45° aerial imagery that the
    // satellite and hybrid map types otherwise flip on wherever it exists at
    // the zoom (the default) — which keeps the raster map the flat,
    // north-up surface the chevron and the off-centre target assume, and
    // keeps tilt_changed from ever firing there (the gesture detacher below
    // would read a flip as a user gesture).
    const liveMap = new mapsLib.Map(mapEl as HTMLElement, {
        center: { lat: 0, lng: 0 },
        zoom: 1,
        mapTypeId: "roadmap",
        disableDefaultUI: true,
        gestureHandling: "greedy",
        keyboardShortcuts: false,
        isFractionalZoomEnabled: true,
        tilt: 0,
        colorScheme,
        ...(mapId !== "" ? { mapId } : {}),
        ...(rendering === "AUTO" ? {} : { renderingType: rendering }),
        ...(state.isVector ? { heading: 0, headingInteractionEnabled: false } : {}),
    });
    state.map = liveMap;
    // Traffic layer is created once and toggled on/off via setMap (memoized).
    state.trafficLayer = new mapsLib.TrafficLayer();

    // One immediate camera move — the glide's per-frame step, and the jump.
    // Opens the gesture-suppression window of each property the move changes
    // (see GESTURE_SUPPRESS_MS), so the camera-change events this call fires
    // are ignored by the gesture detacher. The tilt window also opens on a
    // zoom change: a vector map clamps tilt by zoom, so a zoom step can move
    // the tilt without this page having asked for a new one. heading/tilt
    // are vector-only (a raster map reinterprets them and stops positioning).
    function moveCam(pose: Partial<CameraPose>): void {
        const now = Date.now();
        const opts: GMCameraOptions = {};
        if (pose.lat !== undefined && pose.lng !== undefined) {
            opts.center = { lat: pose.lat, lng: pose.lng };
        }
        // lastSet records only what is SENT: a heading/tilt the raster phase
        // never passed on must count as a change once the map turns vector,
        // or its first rotation would look like a user gesture.
        const zoomChanged = pose.zoom !== undefined && pose.zoom !== state.lastSet.zoom;
        if (pose.zoom !== undefined) {
            opts.zoom = pose.zoom;
            state.lastSet.zoom = pose.zoom;
        }
        if (zoomChanged) state.programmaticUntil.zoom = now + GESTURE_SUPPRESS_MS;
        if (state.isVector) {
            if (pose.heading !== undefined) {
                opts.heading = pose.heading;
                if (pose.heading !== state.lastSet.heading) {
                    state.programmaticUntil.heading = now + GESTURE_SUPPRESS_MS;
                }
                state.lastSet.heading = pose.heading;
            }
            if (zoomChanged || (pose.tilt !== undefined && pose.tilt !== state.lastSet.tilt)) {
                state.programmaticUntil.tilt = now + GESTURE_SUPPRESS_MS;
            }
            if (pose.tilt !== undefined) {
                opts.tilt = pose.tilt;
                state.lastSet.tilt = pose.tilt;
            }
        }
        liveMap.moveCamera(opts);
    }

    // The camera easing this API lacks: a glide re-applies an interpolated
    // pose per frame, from the pose last applied (or, for a field not applied
    // since the user last took the camera, from what the map shows now).
    const glide = createCameraGlide({
        current: () => {
            const center = liveMap.getCenter();
            return {
                lat: center?.lat() ?? 0,
                lng: center?.lng() ?? 0,
                zoom: liveMap.getZoom() ?? 0,
                heading: liveMap.getHeading() ?? 0,
                tilt: liveMap.getTilt() ?? 0,
            };
        },
        apply: moveCam,
    });

    // Compute the camera centre that renders `target` at screen offset (dxPx
    // right, dyPx down) from centre, via the flat-Mercator world projection.
    // The screen offset is un-rotated by the map heading so the shift stays
    // "screen-left" under heading-up rotation. Tilt is not modelled (the
    // projection is flat), so a tilted vector camera offsets approximately.
    // Returns null until the projection is ready (first idle), so the caller
    // keeps the chevron centred until the offset can actually be applied.
    function offsetCenterFor(
        target: GMLatLng,
        dxPx: number,
        dyPx: number,
        zoom: number,
        headingDeg: number,
    ): GMLatLng | null {
        const proj = liveMap.getProjection();
        const worldPt = proj?.fromLatLngToPoint(target);
        if (!proj || !worldPt) return null;
        // World units -> screen px scale: the 256-unit world is 2**zoom tiles
        // wide, so one world unit spans 2**zoom screen px.
        const scale = 2 ** zoom;
        const th = (headingDeg * Math.PI) / 180;
        const cos = Math.cos(th);
        const sin = Math.sin(th);
        // world = R(heading) . screen. Screen +x = right, +y = down; world
        // +x = east, +y = south; the two coincide at heading 0. Subtracting
        // the world offset from the target places the target at +screen
        // offset from centre.
        const worldDx = (cos * dxPx - sin * dyPx) / scale;
        const worldDy = (sin * dxPx + cos * dyPx) / scale;
        const center = proj.fromPointToLatLng(
            new (gmapsNS().Point)(worldPt.x - worldDx, worldPt.y - worldDy),
        );
        return center ? { lat: center.lat(), lng: center.lng() } : null;
    }

    // Pin the chevron left-of-centre (and dropped per markerPos) and target
    // the camera at the matching off-centre point so the GPS location renders
    // under it — the OSM `markerEl.left/top` + camera `padding`
    // parity, done without a native padding API. mapHeading is the applied
    // map heading (0 for a raster map). When the projection is not yet ready
    // the camera cannot offset, so the chevron stays centred over the
    // un-offset location.
    //
    // The camera snaps (null) or glides per [motion]. The offset becoming
    // available is the same kind of move as a layout reflow — the chevron's
    // screen spot changes without a new fix — so it takes the reflow motion
    // too; on either, the marker's CSS transition runs in lockstep with the
    // camera glide so they land together, while on a fix the marker's
    // left/top write snaps and the camera eases the ground underneath it.
    function placeFollowCamera(
        fix: NonNullable<typeof state.lastFix>,
        mapHeading: number,
        pushMotion: CameraMotion | null,
    ): void {
        // Net horizontal shift: a right-card reserve shifts the marker left,
        // a left-card reserve shifts it right. Only one is ever non-zero.
        const mx = markerXFraction(fix.rightSafe) - markerXFraction(fix.leftSafe);
        const drop = markerDrop(fix.markerPos, fix.bottomSafe);
        const target: GMLatLng = { lat: fix.lat, lng: fix.lng };
        const center =
            offsetCenterFor(
                target,
                -mx * window.innerWidth,
                drop * window.innerHeight,
                fix.zoom,
                mapHeading,
            ) ?? target;
        const offsetApplied = center !== target;
        const motion =
            pushMotion !== null && offsetApplied !== state.offsetApplied
                ? REFLOW_MOTION
                : pushMotion;
        state.offsetApplied = offsetApplied;
        markerTransition.setActive(motion === REFLOW_MOTION);
        markerEl.style.left = offsetApplied ? `${(0.5 - mx) * 100}%` : "50%";
        markerEl.style.top = offsetApplied ? `${(0.5 + drop) * 100}%` : "50%";
        const pose: CameraPose = {
            lat: center.lat,
            lng: center.lng,
            zoom: fix.zoom,
            heading: mapHeading,
            tilt: fix.tilt,
        };
        if (motion === null) {
            glide.jump(pose);
        } else {
            glide.to(pose, motion);
        }
    }

    // --- Camera-follow state machine -----------------------------------------

    // Place the camera back onto the last fix (a re-follow, a north-up flip,
    // a rendering-mode switch), re-syncing the chevron for the current mode;
    // a null [motion] snaps.
    function easeHome(motion: CameraMotion | null): void {
        const fix = state.lastFix;
        if (!fix) return;
        // Raster map re-shows the chevron at the last travel bearing so it
        // reads correctly the instant follow re-attaches, before the next fix
        // arrives; a vector map re-syncs the chevron from the next
        // updateCamera push (the map rotates). placeFollowCamera restores the
        // off-centre target + chevron spot.
        // A re-follow adopts the fix heading outright rather than holding
        // whatever the map was rotated to before the user panned it.
        state.headingHold = NO_HEADING_HOLD;
        const mapHeading = state.isVector ? mapHeadingFor(fix.heading) : 0;
        syncChevron(state.isVector ? fix.tilt : 0, fix.heading, mapHeading);
        placeFollowCamera(fix, mapHeading, motion);
    }

    function setFollowing(follow: boolean): void {
        if (state.following === follow) return;
        state.following = follow;
        report("follow", follow);
        if (follow) {
            if (state.refollowTimer) clearTimeout(state.refollowTimer);
            state.refollowTimer = 0;
            markerEl.style.display = "block";
            // Ease home in one continuous transition; the per-fix cadence
            // easing resumes from the next push.
            easeHome(REFOLLOW_MOTION);
        } else {
            // Detached (free pan): the screen-fixed chevron points at
            // arbitrary map, so hide it until the camera re-attaches to the
            // location.
            //
            // DIVERGENCE from the OSM backend: it swaps to a
            // geo-anchored clone (the shared engine's syncGeoMarker) so the
            // user still sees their GPS position on the panned map. Google
            // Maps has no mapId-free, non-deprecated geo-marker —
            // AdvancedMarkerElement needs a Cloud-configured mapId, and the
            // classic google.maps.Marker is deprecated (barred by
            // AGENTS.md#no-suppress). So the self-marker is simply hidden
            // while detached. A geo-anchored OverlayView (the only mapId-free,
            // non-deprecated route, materially more complex) is a documented
            // follow-up.
            markerEl.style.display = "none";
        }
    }

    function armRefollow(): void {
        if (state.refollowTimer) clearTimeout(state.refollowTimer);
        state.refollowTimer = setTimeout(() => setFollowing(true), AUTO_REFOLLOW_MS);
    }

    // Every user gesture hands the camera to the user: a glide still in
    // flight would keep dragging it back under their finger, and the next
    // glide must start from wherever they leave it (see CameraGlide.release
    // — called on every gesture, not only the detaching one, since a pinch
    // while already detached moves the camera too).
    function userTookCamera(): void {
        glide.release();
        setFollowing(false);
    }

    // dragstart fires only for user pans (not programmatic moveCamera).
    // Detach follow so the user can free-pan; re-attach AUTO_REFOLLOW_MS
    // after the last gesture or on an explicit host setFollow(true).
    liveMap.addListener("dragstart", () => {
        userTookCamera();
        if (state.refollowTimer) {
            clearTimeout(state.refollowTimer);
            state.refollowTimer = 0;
        }
    });
    liveMap.addListener("dragend", () => {
        if (!state.following) armRefollow();
    });

    // User-gesture detach beyond panning. Change events carry no
    // user-vs-programmatic flag, so gate on the suppression window: a change
    // outside it is a user gesture. They have no "end" event, so re-arm the
    // refollow timer immediately. zoom_changed fires in both modes;
    // tilt_changed only on a vector map (a raster map is constructed with
    // its 45° imagery switched off, so it never tilts — see the map options).
    for (const [ev, prop] of [
        ["zoom_changed", "zoom"],
        ["tilt_changed", "tilt"],
    ] as const) {
        liveMap.addListener(ev, () => {
            if (Date.now() <= state.programmaticUntil[prop]) return;
            userTookCamera();
            armRefollow();
        });
    }

    // heading_changed reports the bearing for the host compass overlay
    // (throttled — see createBearingReporter) and detaches follow on a user
    // rotation. Registered in every mode: a raster map is north-up and never
    // rotates, so the event simply never fires there, while a map that
    // starts raster and resolves to vector at its first tilesloaded (the AUTO
    // rendering choice with a vector-configured Map ID) needs the listener
    // in place by then — registering it only for a vector start left that
    // map's compass frozen at north.
    const reportBearing = createBearingReporter(report);
    liveMap.addListener("heading_changed", () => {
        reportBearing(liveMap.getHeading() ?? 0);
        if (Date.now() > state.programmaticUntil.heading) {
            userTookCamera();
            armRefollow();
        }
    });

    // First tilesloaded marks the map as rendered. Log to console for
    // diagnostics; the host detects
    // readiness via onPageFinished, not a bridge event. Detach immediately;
    // the event fires repeatedly. google.maps.Map emits no general "error"
    // event, so the only fatal paths are the missing-key check,
    // gm_authFailure, the WebGL pre-check, and the bootstrap/importLibrary
    // rejection caught by the boot module's init().catch.
    const tilesListener = liveMap.addListener("tilesloaded", () => {
        if (state.rendered) return;
        state.rendered = true;
        report("ready", webglRenderer());
        // getRenderingType() is the only authoritative answer, and it resolves
        // only once tiles are in, so reconcile in BOTH directions here.
        //
        // Downgrade: Google silently renders RASTER when the device's WebGL
        // cannot host a vector map (e.g. a low-end head unit with no usable 3D
        // context), so stop sending heading/tilt — a raster map reinterprets
        // them and passing them stops the camera from positioning.
        //
        // Upgrade: AUTO starts raster because the Map ID's cloud configuration
        // is unreadable from here, so a Map ID configured for vector arrives as
        // an upgrade. Nothing needs re-constructing — heading/tilt ride every
        // updateCamera push, and headingInteractionEnabled already defaults to
        // false on a vector map, which is what the launcher wants anyway.
        //
        // easeHome re-issues a camera move + chevron sync for the resolved
        // mode — as a snap: on the downgrade the raster map has ignored every
        // placement so far (they carried heading/tilt) and still sits at the
        // construction centre, which an ease would fly in from.
        const resolved = liveMap.getRenderingType();
        log(`renderingType=${resolved}`);
        const resolvedVector = resolved === "VECTOR";
        if (state.isVector !== resolvedVector) {
            log(resolvedVector ? "resolved-to-vector" : "vector-fallback-to-raster");
            state.isVector = resolvedVector;
            easeHome(null);
        }
        log("rendered");
        tilesListener.remove();
    });

    // Staleness timer: grey the chevron when fixes stop arriving (a tunnel).
    startStaleTicker(chevron, () => state.lastFixMs);

    // --- Bridge functions ----------------------------------------------------

    // Android -> JS: smooth camera follow. The glide interpolates between the
    // sparse GPS fixes under the same rules as the MapLibre engine's easeTo
    // (followMotion). The chevron is pinned on screen and tinted per fix;
    // markerColor self-heals if the first push raced page load. VECTOR
    // drives heading + tilt (heading-up rotates the map); RASTER sends
    // center + zoom only.
    window.updateCamera = (
        lat,
        lon,
        bearing,
        zoom,
        tilt,
        markerPos,
        bottomSafe,
        rightSafe,
        leftSafe,
        markerColor,
    ) => {
        // Same gate as the shared follow camera: never make an unreal
        // coordinate the camera target (see isRealPosition).
        if (!isRealPosition(lat, lon)) return;
        // The same measurements the shared engine feeds followMotion: the
        // previous push (to tell a reflow from a fix), the interval since it
        // (measured BEFORE lastFixMs is refreshed), and whether that interval
        // is a signal gap (which also restarts bearing smoothing and the
        // heading dead band from the raw value).
        const previousFix = state.lastFix;
        const now = Date.now();
        const sinceLastFixMs = state.lastFixMs > 0 ? now - state.lastFixMs : 0;
        const signalGap = sinceLastFixMs > LOCATION_STALE_THRESHOLD_MS;
        if (signalGap) {
            state.lastBearing = null;
            state.headingHold = NO_HEADING_HOLD;
        }

        setChevronColor(chevron, markerColor);
        state.lastFixMs = now;
        markerEl.classList.remove("stale");

        const heading = smoothedBearing(state.lastBearing, bearing || 0);
        state.lastBearing = heading;
        const previousZoom = state.lastPushedZoom;
        const z = Number.isFinite(zoom) ? zoom : 16;
        state.lastPushedZoom = z;
        const fix = {
            lat,
            lng: lon,
            heading,
            zoom: z,
            tilt: tilt || 0,
            markerPos: markerPos || 0,
            bottomSafe: bottomSafe || 0,
            rightSafe: rightSafe || 0,
            leftSafe: leftSafe || 0,
        };
        state.lastFix = fix;

        if (!state.following) {
            // Detached (free pan): leave the camera centre where the user
            // panned, but a pushed zoom change is the host's +/- button (head
            // units have no multitouch, so the zoom buttons are mandatory) —
            // apply it around the free camera's own centre.
            if (previousZoom > 0 && state.lastPushedZoom !== previousZoom) {
                glide.to({ zoom: state.lastPushedZoom }, DETACHED_ZOOM_STEP_MOTION);
            }
            return;
        }

        const motion = followMotion({
            firstCamera: state.firstCamera,
            signalGap,
            // This page's lat/lng fix onto isPaddingOnlyReflow's lon/lat shape.
            reflow: isPaddingOnlyReflow(previousFix && { ...previousFix, lon: previousFix.lng }, {
                ...fix,
                lon: fix.lng,
            }),
            sinceLastFixMs,
        });
        state.firstCamera = false;

        // VECTOR drives heading-up rotation (through the dead band, so a
        // straight road translates without re-laying-out every label; a real
        // turn then glides round over the segment) + tilt/3D; RASTER is
        // north-up (mapHeading 0, no tilt). placeFollowCamera offsets both
        // the chevron and the camera target so the location sits clear of
        // the side cards — the OSM parity.
        const mapHeading = state.isVector ? mapHeadingFor(heading) : 0;
        syncChevron(tilt || 0, heading, mapHeading);
        markerEl.style.display = "block";
        placeFollowCamera(fix, mapHeading, motion);
    };

    // Android -> JS: switch the map type and toggle the traffic overlay.
    // mapType is a GoogleMapType enum name: ROADMAP / SATELLITE / HYBRID /
    // TERRAIN.
    window.setGoogleMapsOptions = (mapType, traffic) => {
        liveMap.setMapTypeId(MAP_TYPE_IDS[mapType] ?? "roadmap");
        state.trafficLayer?.setMap(traffic ? liveMap : null);
    };

    // A host-driven detach hands the camera over like a gesture would (the
    // host only ever pushes true today — the locate button — but the bridge
    // contract allows both).
    window.setFollow = (follow) => (follow ? setFollowing(true) : userTookCamera());

    window.setNorthUp = (enabled) => {
        state.northUp = !!enabled;
        // Raster maps are north-up only and cannot rotate, so this is a no-op
        // for the map there; the chevron always shows the bearing regardless.
        if (!state.isVector) return;
        // Vector map: re-orient while following (the off-centre target turns
        // with the map, so the whole placement is redone); a detached camera
        // keeps the user's rotation until re-attach. The chevron flips with
        // the camera — waiting for the next fix would leave it pointing wrong
        // for up to one GPS interval.
        if (state.following) {
            easeHome(ORIENTATION_FLIP_MOTION);
        }
    };

    // Host lifecycle resume: re-measure and repaint so the map recovers after
    // a pause/resume cycle (guards a stale GL surface on Android). Google
    // Maps has no resize() API; a window resize event triggers the same
    // relayout path.
    window.onHostResume = () => {
        window.dispatchEvent(new Event("resize"));
    };

    // Replay pending calls the boot stubs recorded before this async init
    // completed. Order mirrors the host's push sequence.
    if (pending.setGoogleMapsOptions) window.setGoogleMapsOptions(...pending.setGoogleMapsOptions);
    if (pending.setNorthUp != null) window.setNorthUp(pending.setNorthUp);
    if (pending.setFollow != null) window.setFollow(pending.setFollow);
    if (pending.updateCamera) window.updateCamera(...pending.updateCamera);
    if (pending.onHostResume) window.onHostResume();
}
