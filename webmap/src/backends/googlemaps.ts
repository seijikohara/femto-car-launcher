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
//     parity with the OSM backend. The map rotates in heading-up, to the
//     same smoothed heading as the OSM map; the chevron rotates in north-up.
//   - RASTER (no Map ID): north-up only. A raster map cannot rotate or tilt,
//     and passing heading/tilt to moveCamera stops the camera from
//     positioning, so the map stays north-up and the chevron always rotates
//     to the travel bearing to convey heading. (The raster map's own 45°
//     aerial-imagery mode, which would tilt and turn it on its own, is
//     switched off at construction.)
//
// Like the OSM backend, the screen-pinned chevron sits clear of the side
// cards (and drops with markerPos to clear the bottom overlay), and the
// camera holds the GPS location under it. Google Maps has no camera
// `padding` (MapLibre's way to put the location there), so the glide carries
// the chevron's screen offset in its pose and the page derives the camera
// centre from it every frame with flat Web Mercator math (cameraCenterFor in
// camera-glide.ts); a rotation or zoom therefore pivots on the chevron, as on
// the OSM map. A tilted vector map needs more than the flat math: its
// perspective converges on the viewport centre, which would lean the road
// ahead toward a chevron beside the centre and miss the fix by tens of px
// vertically. The page measures that perspective through Google's own
// camera (a WebGLOverlayView's coordinate transformer on a map with a Map ID,
// else an OverlayView's MapCanvasProjection) and corrects both — a yaw bias
// on the map heading and the exact ground offset under the chevron
// (lens.ts) — so the chevron keeps the OSM spot
// (markerSpot in style.ts). Until the measurement exists, or when it is
// implausible, the chevron stays at that spot with no yaw and the flat offset:
// the road ahead leans toward the centre until the lens is measured.
//
// This backend does NOT use the shared follow-camera engine: Google's camera
// API is immediate (moveCamera has no easing, and there is no easeTo), so the
// page interpolates the camera itself frame by frame (camera-glide.ts, the
// loop Google's own vector-map guidance animates the camera with) under the
// same motion policy as the MapLibre engine (camera.ts: followMotion and the
// shared curves); camera-change events carry no user-vs-programmatic flag
// (suppression windows stand in for originalEvent gating); and there is no
// mapId-free, non-deprecated geo marker for the detached mode — the chevron
// simply hides while detached (a geo-anchored OverlayView is the documented
// follow-up). It shares the chevron helpers, the bridge plumbing, the reflow
// lockstep, and the camera.ts / style.ts pure math.
import { importLibrary, setOptions } from "@googlemaps/js-api-loader";
import type { PageReporter, PendingBridgeCalls } from "../bridge";
import { createBearingReporter, webglRenderer, webglSupport } from "../bridge";
import {
    AUTO_REFOLLOW_MS,
    type CameraMotion,
    DETACHED_ZOOM_STEP_MOTION,
    type FollowOrientation,
    followMotion,
    followOrientation,
    isPaddingOnlyReflow,
    isRealPosition,
    LAYOUT_REFLOW_MS,
    LOCATION_STALE_THRESHOLD_MS,
    markerTransitionStep,
    normalizeBearing,
    ORIENTATION_FLIP_MOTION,
    REFLOW_MOTION,
    REFOLLOW_MOTION,
    smoothedBearing,
    spotMotion,
} from "../camera";
import {
    anchorAt,
    type CameraPose,
    type CameraView,
    cameraCenterFor,
    createCameraGlide,
} from "../camera-glide";
import { chevronHandles, setChevronColor, setChevronTransform, startStaleTicker } from "../chevron";
import { ScriptLoadError } from "../load-outcome";
import {
    calibrateLens,
    type LensCalibration,
    lensFovyDeg,
    lensGroundOffset,
    lensMoved,
    lensProbeDistancePx,
    lensYawDeg,
} from "../lens";
import { createMarkerTransition } from "../marker-motion";
// The self-marker placement (style.ts is the SSOT, shared with the OSM
// backend): where the chevron sits to clear the side cards and the bottom
// overlay.
import { type MarkerSpot, markerSpot } from "../style";

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
// google.maps.LatLng (method accessors), as returned by Map.getCenter —
// distinct from the GMLatLng literal we pass into moveCamera.
interface GMLatLngObj {
    lat(): number;
    lng(): number;
}
interface GMMap {
    moveCamera(opts: GMCameraOptions): void;
    setMapTypeId(id: string): void;
    // The camera the map shows now — where every glide starts from.
    getCenter(): GMLatLngObj | undefined;
    getZoom(): number | undefined;
    getHeading(): number | undefined;
    getTilt(): number | undefined;
    // "VECTOR" | "RASTER" | "UNINITIALIZED". Google may silently downgrade a
    // VECTOR map to RASTER when the device's WebGL cannot host it.
    getRenderingType(): string;
    addListener(event: string, handler: () => void): GMMapsEventListener;
}
interface GMTrafficLayer {
    setMap(map: GMMap | null): void;
}
// google.maps.MapCanvasProjection, as an OverlayView exposes it from draw()
// on: where a location lands in the map's container, in px. On a tilted
// vector map this is the perspective the map renders with (its visible
// region is a trapezoid there), which is what the lens measures.
interface GMMapCanvasProjection {
    fromLatLngToContainerPixel(latLng: GMLatLng): { x: number; y: number } | null;
}
// google.maps.OverlayView: subclassed for its draw() hook and projection
// only — the lens probe draws nothing.
interface GMOverlayView {
    setMap(map: GMMap | null): void;
    getProjection(): GMMapCanvasProjection | null | undefined;
}
// google.maps.WebGLOverlayView's draw options: only the transformer is read.
// fromLatLngAltitude returns the column-major MVP matrix of a frame at the
// point; getCameraParams the camera that matrix was built for.
interface GMWebGLDrawOptions {
    transformer: {
        fromLatLngAltitude(at: GMLatLng & { altitude: number }): Float64Array;
        getCameraParams(): { center: GMLatLngObj; heading: number; tilt: number; zoom: number };
    };
}
interface GMWebGLOverlayView {
    setMap(map: GMMap | null): void;
}
interface GMMapsLibrary {
    Map: new (el: HTMLElement, opts: Record<string, unknown>) => GMMap;
    OverlayView: new () => GMOverlayView;
    WebGLOverlayView: new () => GMWebGLOverlayView;
    TrafficLayer: new () => GMTrafficLayer;
}

function gmBridge(): GoogleMapsFemtoBridge | undefined {
    return window.femtoBridge as GoogleMapsFemtoBridge | undefined;
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
// moving. While following, the heading window reopens with every frame of a
// turn (the map turns to each smoothed bearing), so a user rotation would go
// undetected; the page therefore switches rotation gestures off
// (headingInteractionEnabled: false at construction, in every mode — see the
// map options). Zoom and tilt open only on frames that change them, so a
// user zoom stays detectable throughout. The blind spot that remains is a
// pinch during the second or two a zoom step itself glides — head units have
// no multitouch, so it is accepted.
//
// The window outlasts a late frame by a wide margin: should the API defer a
// change event to its next render, a frame that stalls on a head unit (the
// diagnostics' worst frame interval) must still land inside it, or a turn
// would detach the follow mid-corner. The per-property split is what keeps a
// window this wide from hiding a user zoom.
const GESTURE_SUPPRESS_MS = 200;

// The value the map will show for a [requested] zoom or tilt, given
// what it shows now ([shown]) after the page last sent [sent]: Google
// clamps a zoom past the map type's ceiling and a vector map's tilt past
// its ceiling at the zoom, and keeps the centre it was sent. The map
// showing less than the page last sent is that clamp; otherwise the
// requested value stands, since it is what this very move is about to
// show. A clamp first met on this move is caught after it (moveCam).
function shownValue(requested: number, shown: number, sent: number | null): number {
    return sent !== null && shown < sent ? Math.min(requested, shown) : requested;
}

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
        // Where the chevron is placed, as fractions of the viewport
        // (markerSpot): its left/top, and — MapLibre's padding
        // analogue — the spot a read-back measures the camera against
        // wherever the glide does not own the offset (see the glide's
        // current()). That is after the user took the camera: the chevron is
        // hidden then, and a layout or tilt change moves its spot with no
        // camera move, so the re-follow's placement sets the new spot before
        // its glide reads the map back. Centred until the first placement.
        spot: { x: 0, y: 0 } as MarkerSpot,
        // Whether the chevron is on screen at [spot]: false while it is
        // hidden (detached) or not yet placed, when a new spot takes no glide
        // of its own (see spotMotion).
        spotShown: false,
        // The tilted vector map's measured perspective (lens.ts); null until
        // the first measurement and whenever one is implausible, when the
        // chevron falls back to the centre line with no yaw.
        lens: null as LensCalibration | null,
        // The camera the lens was last measured at: the perspective only
        // changes with these, so a probe re-measures only when one moved.
        lensAt: { tilt: Number.NaN, zoom: Number.NaN, width: 0, height: 0 },
        // The yaw bias (lens.ts) in the heading last sent to the map: the
        // compass adds it back to report the travel heading.
        lensYaw: 0,
        // The lens state last reported to the host's diagnostics, so a
        // re-measurement that changes nothing reports nothing.
        lensReport: "",
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

    // The map's bearing and the chevron's turn for a fix, the one rule both
    // backends orient by (followOrientation): a VECTOR map rotates like the
    // OSM map, a RASTER map stays north-up with the chevron carrying the
    // heading.
    function orientationFor(heading: number): FollowOrientation {
        return followOrientation(state.northUp, heading, state.isVector);
    }

    // Turn the chevron by [turn]; on a VECTOR map the perspective lays it onto
    // the tilted ground plane, as follow-camera.ts does. A RASTER map has no
    // tilt plane.
    function syncChevron(tilt: number, turn: number): void {
        setChevronTransform(markerEl, state.isVector ? tilt : 0, turn, state.isVector);
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
    // package with the runtime key. The loader rejects only when the script
    // (or a library module it pulls) cannot be fetched — network, blocked CDN
    // — so that rejection is tagged ScriptLoadError, which the boot module
    // reports as backend-load-failed: a network failure the host retries for
    // as long as it lasts. Anything thrown after this point happens with the
    // script loaded and reaches the host as map-init-exception, which it
    // retries only within its budget: from `new mapsLib.Map` on, every reload
    // is a billed map load. The cast narrows the loader's full google.maps
    // typing to the local stubs above.
    setOptions({ key, v: "weekly" });
    const mapsLib = (await importLibrary("maps").catch((e: unknown) => {
        throw new ScriptLoadError(e instanceof Error ? e.message : String(e));
    })) as unknown as GMMapsLibrary;

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
    // entirely.
    //
    // headingInteractionEnabled: false is passed in every mode, because the
    // heading is driven solely by the host (north-up vs heading-up), never by
    // user rotation gestures. The API applies it to a vector map only, and
    // when it is not set in code the Map ID's cloud configuration decides —
    // so an AUTO map that resolves to vector could otherwise let a two-finger
    // twist rotate it. While following, the heading's gesture window reopens
    // with every frame of a turn (see GESTURE_SUPPRESS_MS), so such a twist
    // would not detach the follow: the glide would fight the user's fingers.
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
    // north-up surface the chevron placement and the centre math assume, and
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
        ...(state.isVector ? { heading: 0 } : {}),
        headingInteractionEnabled: false,
    });
    state.map = liveMap;
    // Traffic layer is created once and toggled on/off via setMap (memoized).
    state.trafficLayer = new mapsLib.TrafficLayer();

    // The tilt the lens corrects for when the page asks for [requested].
    function shownTilt(requested: number): number {
        return shownValue(requested, liveMap.getTilt() ?? 0, state.lastSet.tilt);
    }

    // The zoom the centre math works at when the page asks for [requested]:
    // computed at a zoom the map clamps, the centre puts the chevron's
    // anchor off by the clamped fraction of its offset.
    function shownZoom(requested: number): number {
        return shownValue(requested, liveMap.getZoom() ?? 0, state.lastSet.zoom);
    }

    // The view a pose shows the map at: the zoom, the MAP heading, and the
    // chevron's offset as the flat ground offset the centre math takes —
    // plus the lens's yaw bias inside that heading. On a tilted vector map
    // the lens (lens.ts) turns the map past the pose's travel heading by the
    // yaw, so the direction of travel runs straight up through a chevron
    // beside the centre, and solves the ground point under the chevron
    // through the measured perspective, so the fix lands exactly there; with
    // no lens, on a flat map and on a raster map the view is the pose itself
    // (a raster map stays north-up whatever the pose carries). A field the
    // pose leaves out is what the map shows (the heading and tilt) or the
    // chevron's spot (the offset).
    function viewFor(pose: Partial<CameraPose>): { view: CameraView; yaw: number } {
        const zoom = shownZoom(pose.zoom ?? liveMap.getZoom() ?? 0);
        const offsetX = pose.offsetX ?? state.spot.x * window.innerWidth;
        const offsetY = pose.offsetY ?? state.spot.y * window.innerHeight;
        if (!state.isVector) return { view: { zoom, heading: 0, offsetX, offsetY }, yaw: 0 };
        const tilt = shownTilt(pose.tilt ?? liveMap.getTilt() ?? 0);
        const yaw = lensYawDeg(state.lens, offsetX, tilt);
        const ground = lensGroundOffset(state.lens, offsetX, offsetY, tilt);
        const heading =
            pose.heading !== undefined
                ? normalizeBearing(pose.heading - yaw)
                : (liveMap.getHeading() ?? 0);
        return { view: { zoom, heading, offsetX: ground.x, offsetY: ground.y }, yaw };
    }

    // One immediate camera move — the glide's per-frame step, and the jump.
    // A pose that carries the anchor sends the camera centre that shows the
    // anchor at the pose's chevron offset, at the zoom and heading this same
    // move sets (cameraCenterFor through viewFor), so every frame keeps the
    // fix under the chevron while the heading and zoom glide. Opens the
    // gesture-suppression window of each property the move changes (see
    // GESTURE_SUPPRESS_MS), so the camera-change events this call fires are
    // ignored by the gesture detacher. The tilt window also opens on a zoom
    // change: a vector map clamps tilt by zoom, so a zoom step can move the
    // tilt without this page having asked for a new one. heading/tilt are
    // vector-only (a raster map reinterprets them and stops positioning).
    // A move that first runs into a zoom or tilt ceiling is re-centred at
    // once for what the map then shows (see shownValue), rather than leaving
    // the chevron off its anchor until the next move — a snap may have none.
    function moveCam(pose: Partial<CameraPose>): void {
        const now = Date.now();
        const opts: GMCameraOptions = {};
        const { view, yaw } = viewFor(pose);
        if (pose.lat !== undefined && pose.lng !== undefined) {
            opts.center = cameraCenterFor({ lat: pose.lat, lng: pose.lng }, view);
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
                opts.heading = view.heading;
                // Before moveCamera: its heading_changed reports the compass.
                state.lensYaw = yaw;
                if (view.heading !== state.lastSet.heading) {
                    state.programmaticUntil.heading = now + GESTURE_SUPPRESS_MS;
                }
                state.lastSet.heading = view.heading;
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
        if (opts.center && pose.lat !== undefined && pose.lng !== undefined) {
            const settled = viewFor(pose).view;
            if (
                settled.zoom !== view.zoom ||
                settled.offsetX !== view.offsetX ||
                settled.offsetY !== view.offsetY
            ) {
                liveMap.moveCamera({
                    center: cameraCenterFor({ lat: pose.lat, lng: pose.lng }, settled),
                });
            }
        }
    }

    // The camera easing this API lacks: a glide re-applies an interpolated
    // pose per frame, from the pose last applied (or, for a field not applied
    // since the user last took the camera, from what the map shows now). The
    // map shows a camera centre; its pose is the location under the
    // chevron's spot (anchorAt, against state.spot), so a re-follow after a
    // pan eases that location to the fix, pivoting on the chevron as drawn,
    // as MapLibre's padded easeTo does. The location is read through the view
    // the glide starts from — the owned zoom, heading, tilt and offset where
    // it has them — because moveCam derives the first frame's centre from
    // exactly that view (viewFor, which works at the zoom and tilt the map
    // shows where Google clamped the owned ones): read through any other
    // view, the first frame would jump. The heading read back is the travel
    // heading the map shows: its own heading plus the yaw it carries at that
    // offset and tilt, which viewFor takes off again.
    function readPose(owned: Partial<CameraPose>): CameraPose {
        const zoom = liveMap.getZoom() ?? 0;
        const tilt = liveMap.getTilt() ?? 0;
        const offsetX = owned.offsetX ?? state.spot.x * window.innerWidth;
        const offsetY = owned.offsetY ?? state.spot.y * window.innerHeight;
        const mapHeading = liveMap.getHeading() ?? 0;
        const heading = state.isVector
            ? normalizeBearing(
                  mapHeading + lensYawDeg(state.lens, offsetX, shownTilt(owned.tilt ?? tilt)),
              )
            : mapHeading;
        const { view } = viewFor({
            zoom: owned.zoom ?? zoom,
            heading: owned.heading ?? heading,
            tilt: owned.tilt ?? tilt,
            offsetX,
            offsetY,
        });
        const center = liveMap.getCenter();
        const anchor = center
            ? anchorAt({ lat: center.lat(), lng: center.lng() }, view)
            : { lat: 0, lng: 0 };
        return { ...anchor, zoom, heading, tilt, offsetX, offsetY };
    }
    const glide = createCameraGlide({ current: readPose, apply: moveCam });

    // Measure the tilted vector map's perspective (lens.ts) from the camera
    // the map is rendering: project the camera target and two ground points
    // ahead of and behind it along the map heading through [project], and
    // solve for the focal length and distance. Called from a probe overlay's
    // draw hook, which the map runs as it renders; re-measures only when the
    // tilt, the zoom or the viewport moved, since nothing else changes the
    // perspective (so a frame that changes none of them costs a few
    // comparisons). At tilt 0 there is nothing to measure and the lens is
    // unused, so the last measurement is kept for the next tilt.
    function measureLens(
        source: "webgl" | "canvas",
        camera: { center: GMLatLngObj; heading: number; tilt: number; zoom: number },
        project: (at: GMLatLng) => { x: number; y: number } | null,
    ): void {
        if (!state.isVector) return;
        const { tilt, zoom, heading } = camera;
        const width = window.innerWidth;
        const height = window.innerHeight;
        const last = state.lensAt;
        if (
            last.tilt === tilt &&
            last.zoom === zoom &&
            last.width === width &&
            last.height === height
        ) {
            return;
        }
        state.lensAt = { tilt, zoom, width, height };
        if (!(tilt > 0)) return;
        const target = { lat: camera.center.lat(), lng: camera.center.lng() };
        const d = lensProbeDistancePx(height);
        const probeAt = (offsetY: number) =>
            project(anchorAt(target, { zoom, heading, offsetX: 0, offsetY }));
        const at = project(target);
        const ahead = probeAt(-d);
        const behind = probeAt(d);
        const lens =
            at && ahead && behind
                ? calibrateLens({
                      tiltDeg: tilt,
                      probeDistancePx: d,
                      aheadUpPx: at.y - ahead.y,
                      behindDownPx: behind.y - at.y,
                      viewportHeightPx: height,
                  })
                : null;
        setLens(lens);
        // The host's MAP diagnostics show whether the lens is measured, so an
        // owner can tell on the device why the road does or does not lean.
        const detail = lens
            ? `measured,source=${source},fovy=${lensFovyDeg(lens, height).toFixed(1)}`
            : `unmeasured,source=${source}`;
        if (detail !== state.lensReport) {
            state.lensReport = detail;
            report("lens", detail);
        }
    }

    // A lens that appears, goes, or is re-measured differently (a viewport
    // resize) changes the yaw and the anchor under the chevron: re-place the
    // camera at once with the reflow motion rather than waiting for the next
    // fix — a parked car may not send one. The same camera measured again
    // changes nothing visible and leaves the camera alone (lensMoved).
    function setLens(next: LensCalibration | null): void {
        const previous = state.lens;
        state.lens = next;
        const moved = lensMoved(
            previous,
            next,
            state.spot.x * window.innerWidth,
            state.spot.y * window.innerHeight,
            liveMap.getTilt() ?? 0,
        );
        if (moved && state.following && state.lastFix) easeHome(REFLOW_MOTION);
    }

    // The lens probe, an overlay that draws nothing and only reads the
    // camera the map renders with. On a map with a Map ID it is a
    // WebGLOverlayView: its transformer is the documented camera matrix the
    // map itself draws with (fromLatLngAltitude returns the MVP matrix of a
    // frame at a point; that matrix's translation column is the point's clip
    // position). WebGLOverlayView may only be added to a vector map with a
    // Map ID, so a map without one falls back to an OverlayView, whose
    // MapCanvasProjection needs no Map ID; its perspective on a tilted map is
    // implied by the reference rather than stated, and calibrateLens refuses
    // a flat reading. Neither probe asks for a redraw: the map renders a
    // frame whenever the camera moves, which is when the lens can change.
    if (mapId !== "") {
        class WebGLLensProbe extends mapsLib.WebGLOverlayView {
            onAdd(): void {}
            onContextRestored(): void {}
            onDraw({ transformer }: GMWebGLDrawOptions): void {
                measureLens("webgl", transformer.getCameraParams(), (at) => {
                    const m = transformer.fromLatLngAltitude({ ...at, altitude: 0 });
                    const w = m[15];
                    if (!(w > 0)) return null;
                    return {
                        x: ((m[12] / w + 1) / 2) * window.innerWidth,
                        y: ((1 - m[13] / w) / 2) * window.innerHeight,
                    };
                });
            }
            onContextLost(): void {}
            onRemove(): void {}
        }
        new WebGLLensProbe().setMap(liveMap);
    } else {
        class LensProbe extends mapsLib.OverlayView {
            onAdd(): void {}
            draw(): void {
                const projection = this.getProjection();
                const center = liveMap.getCenter();
                if (!projection || !center) return;
                measureLens(
                    "canvas",
                    {
                        center,
                        heading: liveMap.getHeading() ?? 0,
                        tilt: liveMap.getTilt() ?? 0,
                        zoom: liveMap.getZoom() ?? 0,
                    },
                    (at) => projection.fromLatLngToContainerPixel(at),
                );
            }
            onRemove(): void {}
        }
        new LensProbe().setMap(liveMap);
    }

    // Pin the chevron at its spot (markerSpot: clear of the side cards and
    // dropped per markerPos) and glide the camera to hold the fix under it —
    // the OSM `markerEl.left/top` + camera `padding` parity, done without a
    // native padding API: the pose carries the fix as its anchor
    // plus the chevron's offset, and moveCam derives the camera centre (and
    // the lens's yaw) from both every frame. [mapBearing] is the follow
    // bearing (followOrientation; 0 for a raster map), before the yaw.
    //
    // The camera snaps (null) or glides per [pushMotion], refined by
    // spotMotion: a chevron that moves on screen — a layout reflow, or a
    // layout change that lands with a moved fix — glides with the
    // reflow motion, its CSS transition in lockstep with the camera; on a fix
    // the chevron stays put and the camera eases the ground underneath it. A
    // fix that arrives during such a glide leaves the chevron gliding and
    // moves the camera over the time the glide has left (followMotion,
    // markerTransitionStep), so the two land together — the reflow behaviour
    // both backends share. [reflowRemainingMs] is that time, read ONCE by the
    // caller and shared with the motion choice: the clock moves on between
    // two reads, and a second read would find the camera's carried-over
    // move outlasting the glide and clear the chevron's transition.
    function placeFollowCamera(
        fix: NonNullable<typeof state.lastFix>,
        mapBearing: number,
        pushMotion: CameraMotion | null,
        reflowRemainingMs: number,
    ): void {
        const width = window.innerWidth;
        const height = window.innerHeight;
        const spot = markerSpot(fix);
        const poseAt = (at: MarkerSpot): CameraPose => ({
            lat: fix.lat,
            lng: fix.lng,
            zoom: fix.zoom,
            heading: mapBearing,
            // A raster map never tilts: a 0 here keeps the tilt the glide owns
            // equal to what the map shows, should the map resolve to vector.
            tilt: state.isVector ? fix.tilt : 0,
            offsetX: at.x * width,
            offsetY: at.y * height,
        });
        const plan = spotMotion(state.spotShown ? state.spot : null, spot, pushMotion);
        if (plan.snapAt) glide.jump(poseAt(plan.snapAt));
        markerTransition.apply(markerTransitionStep(plan.motion, reflowRemainingMs));
        markerEl.style.left = `${(0.5 + spot.x) * 100}%`;
        markerEl.style.top = `${(0.5 + spot.y) * 100}%`;
        // Before the glide below reads the map back against it.
        state.spot = spot;
        state.spotShown = state.following;
        if (plan.motion === null) {
            glide.jump(poseAt(spot));
        } else {
            glide.to(poseAt(spot), plan.motion);
        }
    }

    // --- Camera-follow state machine -----------------------------------------

    // Place the camera back onto the last fix (a re-follow, a north-up flip,
    // a rendering-mode switch), re-syncing the chevron for the current mode;
    // a null [motion] snaps. The chevron takes the mode's turn at once, so it
    // reads correctly the instant the camera starts to move, before the next
    // fix arrives.
    function easeHome(motion: CameraMotion | null): void {
        const fix = state.lastFix;
        if (!fix) return;
        const orientation = orientationFor(fix.heading);
        syncChevron(fix.tilt, orientation.chevronTurn);
        placeFollowCamera(fix, orientation.mapBearing, motion, markerTransition.remainingMs());
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
            state.spotShown = false;
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
    // map's compass frozen at north. The compass reads the travel heading
    // (north-up: north), so the lens's yaw bias comes back off the map's own
    // heading here.
    const reportBearing = createBearingReporter(report);
    liveMap.addListener("heading_changed", () => {
        reportBearing(normalizeBearing((liveMap.getHeading() ?? 0) + state.lensYaw));
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
    // gm_authFailure, the WebGL pre-check, the missing container, and what
    // the boot module's init().catch reports: the importLibrary rejection
    // (backend-load-failed) or an exception thrown after it
    // (map-init-exception).
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
        // updateCamera push, and headingInteractionEnabled was passed false at
        // construction in every mode, so the upgraded map ignores rotation
        // gestures even when the Map ID's cloud configuration enables them.
        //
        // easeHome re-issues a camera move + chevron sync for the resolved
        // mode — as a snap: on the downgrade the raster map has ignored every
        // placement so far (they carried heading/tilt) and still sits at the
        // construction centre, which an ease would fly in from. The chevron
        // keeps its spot in either mode, so the snap lands with the fix under
        // it.
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
        // is a signal gap (which also restarts bearing smoothing from the raw
        // value).
        const previousFix = state.lastFix;
        const now = Date.now();
        const sinceLastFixMs = state.lastFixMs > 0 ? now - state.lastFixMs : 0;
        const signalGap = sinceLastFixMs > LOCATION_STALE_THRESHOLD_MS;
        if (signalGap) state.lastBearing = null;

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
            // Detached (free pan): leave the camera where the user panned,
            // but a pushed zoom change is the host's +/- button (head units
            // have no multitouch, so the zoom buttons are mandatory) — apply
            // it about the chevron's spot, as the OSM map zooms about its
            // padded centre: the glide holds the location under that spot
            // as its anchor while the zoom changes, so moveCam derives every
            // frame's centre around it (the chevron itself stays hidden).
            if (previousZoom > 0 && state.lastPushedZoom !== previousZoom) {
                const here = readPose({});
                glide.to(
                    { lat: here.lat, lng: here.lng, zoom: state.lastPushedZoom },
                    DETACHED_ZOOM_STEP_MOTION,
                );
            }
            return;
        }

        const reflowRemainingMs = markerTransition.remainingMs();
        const motion = followMotion({
            firstCamera: state.firstCamera,
            signalGap,
            // This page's lat/lng fix onto isPaddingOnlyReflow's lon/lat shape.
            reflow: isPaddingOnlyReflow(previousFix && { ...previousFix, lon: previousFix.lng }, {
                ...fix,
                lon: fix.lng,
            }),
            sinceLastFixMs,
            reflowRemainingMs,
        });
        state.firstCamera = false;

        // VECTOR turns the map to the smoothed heading of every fix in
        // heading-up, as the OSM map does, and tilts it; RASTER stays
        // north-up and flat. placeFollowCamera places the chevron and holds
        // the location under it, clear of the side cards — the OSM parity.
        const orientation = orientationFor(heading);
        syncChevron(fix.tilt, orientation.chevronTurn);
        markerEl.style.display = "block";
        placeFollowCamera(fix, orientation.mapBearing, motion, reflowRemainingMs);
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
        // Vector map: re-orient while following (the camera centre is derived
        // from the chevron's spot at each frame's heading, so the whole
        // placement is redone and the map turns about the chevron); a
        // detached camera keeps the user's rotation until re-attach. The
        // chevron flips with the camera — waiting for the next fix would leave
        // it pointing wrong for up to one GPS interval.
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
