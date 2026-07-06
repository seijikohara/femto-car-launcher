// Google Maps JS API LIVE map page. Mirrors the OSM (main.ts) and Mapbox
// (mapbox-main.ts) host-bridge contract so WebMapView.kt can drive all three
// pages with the same evaluateJavascript calls.
//
// The Google Maps JS API is loaded at runtime from Google's CDN via the
// official inline bootstrap loader — it must never be bundled or self-hosted
// (Google Maps Platform ToS). The loader is injected programmatically so the
// API key comes from the Android bridge at runtime rather than being baked
// into the HTML at build time.
//
// Self-marker: in FOLLOW mode the host drives a screen-pinned DOM chevron
// through updateCamera (markerColor / markerPos / safe-zone fractions), like
// the OSM and Mapbox pages — NOT a geo-anchored map marker.
//
// Two render modes, chosen by whether the user supplied a Cloud Map ID
// (femtoBridge.googleMapsMapId()):
//   - VECTOR (Map ID present): full heading-up rotation, tilt, and 3D — parity
//     with the Mapbox page. The map rotates in heading-up; the chevron rotates
//     in north-up.
//   - RASTER (no Map ID): north-up only. A raster map cannot rotate or tilt, and
//     passing heading/tilt to moveCamera stops the camera from positioning, so
//     the map stays north-up and the chevron always rotates to the travel
//     bearing to convey heading.
//
// Like the OSM/Mapbox pages, the screen-pinned chevron sits left-of-centre (and
// drops with markerPos) to clear the right cards / bottom overlay, and the camera
// targets an off-centre point so the GPS location renders under the chevron.
// Google Maps has no camera `padding` (unlike MapLibre/Mapbox), so that off-centre
// target is computed from the flat-Mercator projection, un-rotated by the map
// heading — see offsetCenterFor. Tilt is not modelled, so a tilted vector camera
// offsets approximately; a raster (north-up, no tilt) camera offsets exactly.
//
// One divergence from the OSM/Mapbox pages remains: while detached (free pan) the
// chevron is hidden rather than swapped to a geo-anchored clone — see setFollowing
// for why (no mapId-free, non-deprecated geo-marker).
//
// A second, related divergence: this page has no preset-switch reflow lockstep
// timing (contrast camera.ts isPaddingOnlyReflow + marker-motion.ts, used by the
// OSM/Mapbox pages) because it needs none — see the moveCam doc below.
import {
	AUTO_REFOLLOW_MS,
	appliedBearing,
	LOCATION_STALE_THRESHOLD_MS,
	smoothedBearing,
} from "./camera";
// Shared self-marker offset model with the OSM/Mapbox pages (style.ts is the
// SSOT): how far left of centre the chevron sits to clear the right cards, and
// how far it drops with markerPos to clear the bottom overlay.
import { markerDrop, markerXFraction } from "./style";

// The Google Maps bridge extends the base femtoBridge with googleMapsApiKey()
// and googleMapsMapId(), present only when the host has wired up the Google Maps
// backend. Declared locally rather than in the global Window augmentation to
// avoid a type conflict with main.ts's narrower femtoBridge declaration (both
// compile in the same tsconfig scope; TS merges interface Window and requires
// compatible types).
interface GoogleMapsFemtoBridge {
	onMapEvent(kind: string, detail: string): void;
	// Synchronous getter injected by the host; returns the Google Maps API key.
	// Returns an empty string when unconfigured.
	googleMapsApiKey(): string;
	// Synchronous getter injected by the host; returns the Cloud Map ID the user
	// supplied (a non-empty value opts into a VECTOR map), or "" for a raster map.
	googleMapsMapId(): string;
}

// Android -> JS bridge surface. Every function is feature-detected on the
// Kotlin side (`window.updateCamera && ...`), so these names are a
// compatibility contract — never rename without updating WebMapView.kt.
declare global {
	interface Window {
		// Shared with OSM and Mapbox pages (identical signatures).
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
		setFollow: (follow: boolean) => void;
		setNorthUp: (enabled: boolean) => void;
		onHostResume: () => void;
		// Google Maps specific (not declared in main.ts / mapbox-main.ts).
		setGoogleMapsOptions: (mapType: string, traffic: boolean) => void;
		// Callback Google Maps invokes when authentication fails. Installed
		// before the bootstrap loader so it is in place before the API loads.
		gm_authFailure?: () => void;
	}
}

// Minimal type stubs for the Google Maps JS API (CDN-loaded at runtime, never
// bundled). These cover only the surface this page exercises.
interface GMLatLng {
	lat: number;
	lng: number;
}
// heading/tilt are sent only for a VECTOR map (Cloud Map ID present); a RASTER
// map rejects them (passing either stops moveCamera from positioning).
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
// Projection.fromPointToLatLng — distinct from the GMLatLng literal we pass into
// moveCamera.
interface GMLatLngObj {
	lat(): number;
	lng(): number;
}
// google.maps.Projection: the flat-Mercator world projection (heading/tilt
// independent), used to compute the off-centre camera target for the chevron's
// left/down placement.
interface GMProjection {
	fromLatLngToPoint(latLng: GMLatLng): GMPoint | null;
	fromPointToLatLng(pixel: GMPoint): GMLatLngObj | null;
}
interface GMMap {
	moveCamera(opts: GMCameraOptions): void;
	setMapTypeId(id: string): void;
	getHeading(): number | undefined;
	// Null until the projection is ready (first idle); offsetCenterFor falls back
	// to the un-offset centre until then.
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
	importLibrary(name: "maps"): Promise<GMMapsLibrary>;
	importLibrary(name: string): Promise<Record<string, unknown>>;
	// google.maps.Point constructor — Projection.fromPointToLatLng requires a
	// Point instance (it does not accept a literal).
	Point: new (
		x: number,
		y: number,
	) => GMPoint;
}

// Typed accessor for the Google Maps bridge (mirrors mapboxBridge() in
// mapbox-main.ts; avoids widening the global Window.femtoBridge type).
function gmBridge(): GoogleMapsFemtoBridge | undefined {
	return window.femtoBridge as GoogleMapsFemtoBridge | undefined;
}

// Typed accessor for the CDN-injected google.maps namespace.
function gmapsNS(): GMNamespace {
	return (window as unknown as { google: { maps: GMNamespace } }).google.maps;
}

function log(msg: string): void {
	try {
		console.log(`[googlemaps] ${msg}`);
	} catch {
		// Logging must never break the page.
	}
}

// JS -> Android event channel. "fatal" = definitive never-going-to-render
// facts; "error" = transient resource failures, log-only on the host;
// "follow" = camera follow state flips; "bearing" = throttled map bearing for
// the compass overlay (VECTOR mode only — a RASTER map is north-up and never
// rotates). No kind triggers a backend switch — there is no auto-fallback.
function report(
	kind: "fatal" | "error" | "follow" | "bearing",
	detail: unknown,
): void {
	try {
		gmBridge()?.onMapEvent(kind, String(detail ?? ""));
	} catch {
		// The bridge may be absent outside the launcher (e.g. desktop dev server).
	}
}

// Mutable page state in one const holder (let/var are banned — see biome.json
// and no-let.grit). All camera pushes and API ops read+write through here.
const state = {
	map: undefined as GMMap | undefined,
	trafficLayer: null as GMTrafficLayer | null,
	// True when the user supplied a Cloud Map ID: render a VECTOR map (heading-up
	// rotation + tilt). False = RASTER map (north-up only). Set once in initMap.
	isVector: false,
	// Set to true on the first tilesloaded event; gates fatal error reporting
	// (errors after first render are transient, not key/auth failures).
	rendered: false,
	lastErrorReportMs: 0,
	following: true,
	refollowTimer: 0 as ReturnType<typeof setTimeout> | 0,
	// North-up vs heading-up; only meaningful on a VECTOR map (a raster map is
	// always north-up). The chevron and the vector-map camera read this.
	northUp: false,
	lastBearing: null as number | null,
	lastFixMs: 0,
	lastPushedZoom: 0,
	// Google Maps fires camera-change events for BOTH programmatic moveCamera
	// calls and user gestures, with no originalEvent flag to tell them apart.
	// Each programmatic move opens a short suppression window; change events
	// inside it are treated as programmatic (no follow detach), and events
	// after it as user gestures. moveCamera is immediate (no animation), so its
	// events fire well within the window while user input between fixes does not.
	programmaticUntil: 0,
	// tilt is used only on a VECTOR map; a raster map ignores it. markerPos /
	// bottomSafe / rightSafe / leftSafe are the host's safe-zone fractions, kept
	// so a re-follow (easeHome) reproduces the same chevron/camera offset as the
	// live updateCamera push.
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

// Suppression window after each programmatic moveCamera (see state.programmaticUntil).
const GESTURE_SUPPRESS_MS = 60;

// Throttle per-frame error reports so a flaky tile server does not spray
// logcat (and the in-app diagnostics tail) with thousands of identical lines.
const ERROR_REPORT_INTERVAL_MS = 10_000;
function reportErrorThrottled(detail: string): void {
	const now = Date.now();
	if (now - state.lastErrorReportMs >= ERROR_REPORT_INTERVAL_MS) {
		state.lastErrorReportMs = now;
		report("error", detail.slice(0, 200));
	}
}

// A post-init exception inside a bridge call or an unhandled rejection would
// otherwise reach only the JS console, invisible on an adb-unreachable head
// unit. Route both through the throttled channel so the in-app diagnostics
// tail sees them.
window.addEventListener("error", (e) => {
	log(`uncaught: ${e.message}`);
	reportErrorThrottled(e.message || "uncaught error");
});
window.addEventListener("unhandledrejection", (e) => {
	const reason =
		e.reason instanceof Error ? e.reason.message : String(e.reason);
	log(`unhandledrejection: ${reason}`);
	reportErrorThrottled(reason);
});

// Google Maps requires WebGL; report fatal immediately if absent so the host
// shows a clear notice rather than a silent blank page.
(() => {
	const c = document.createElement("canvas");
	if (!(c.getContext("webgl2") || c.getContext("webgl"))) {
		log("no-webgl-context");
		report("fatal", "no-webgl-context");
	}
})();

// Self-location chevron: a fixed-on-screen DOM overlay (see #self-marker CSS).
// The camera centres the location under the chevron, so the chevron stays still
// while the map slides (and, on a vector map, rotates) beneath it (car-nav
// style). VECTOR: heading-up rotates the MAP and the chevron points up; north-up
// rotates the chevron; rotateX lays it onto the tilted ground plane. RASTER: the
// map is permanently north-up, so the chevron always rotates to the travel
// bearing and there is no tilt plane (no rotateX).
const markerEl = document.getElementById("self-marker") as HTMLElement;
const markerPath = markerEl.querySelector("path");

function syncChevronTransform(tilt: number, heading: number): void {
	if (state.isVector) {
		const turn = state.northUp ? heading : 0;
		markerEl.style.transform = `translate(-50%, -50%) perspective(600px) rotateX(${tilt}deg) rotateZ(${turn}deg)`;
		return;
	}
	markerEl.style.transform = `translate(-50%, -50%) rotateZ(${heading}deg)`;
}

// Pending bridge calls queued by module-top-level stubs so that host pushes
// arriving before initMap completes are not silently dropped. onPageFinished
// can fire before the async import chain resolves on a slow first load.
const pending = {
	camera: null as Parameters<Window["updateCamera"]> | null,
	options: null as [string, boolean] | null,
	follow: null as boolean | null,
	northUp: null as boolean | null,
	resume: false,
};
window.updateCamera = (...a) => {
	pending.camera = a;
};
window.setFollow = (f) => {
	pending.follow = f;
};
window.setNorthUp = (e) => {
	pending.northUp = e;
};
window.onHostResume = () => {
	pending.resume = true;
};
window.setGoogleMapsOptions = (type, traffic) => {
	pending.options = [type, traffic] as [string, boolean];
};

// Maps the GoogleMapType enum name strings from the Android bridge to the
// Google Maps JS API map type id strings.
const MAP_TYPE_IDS: Record<string, string> = {
	ROADMAP: "roadmap",
	SATELLITE: "satellite",
	HYBRID: "hybrid",
	TERRAIN: "terrain",
};

// --- Map construction -------------------------------------------------------

async function initMap(): Promise<void> {
	const key = gmBridge()?.googleMapsApiKey?.() ?? "";
	if (!key) {
		log("google-maps-no-key");
		report("fatal", "google-maps-no-key");
		return;
	}

	// A non-empty Cloud Map ID = the user opted into a VECTOR map (heading-up,
	// tilt, 3D). Blank = a flat north-up RASTER map.
	const mapId = gmBridge()?.googleMapsMapId?.() ?? "";
	const isVector = mapId !== "";
	state.isVector = isVector;

	// gm_authFailure is Google's global hook for invalid/revoked API keys.
	// Install it before injecting the bootstrap loader so it is in place before
	// any authentication attempt. Only report fatal before the first successful
	// render — errors after that are transient, not a permanent key failure.
	window.gm_authFailure = () => {
		log("gm_authFailure");
		if (!state.rendered) report("fatal", "google-maps-auth");
	};

	// Inject the official Google Maps JS API inline bootstrap loader with the
	// runtime key. The loader snippet is taken verbatim from Google's docs
	// (https://developers.google.com/maps/documentation/javascript/dynamic-loading).
	// The API must be loaded from Google's CDN — never bundled or self-hosted.
	const bootstrapScript = document.createElement("script");
	bootstrapScript.textContent = `(g=>{var h,a,k,p="The Google Maps JavaScript API",c="google",l="importLibrary",q="__ib__",m=document,b=window;b=b[c]||(b[c]={});var d=b.maps||(b.maps={}),r=new Set,e=new URLSearchParams,u=()=>h||(h=new Promise(async(f,n)=>{await (a=m.createElement("script"));e.set("libraries",[...r]+"");for(k in g)e.set(k.replace(/[A-Z]/g,t=>"_"+t[0].toLowerCase()),g[k]);e.set("callback",c+".maps."+q);a.src=\`https://maps.\${c}apis.com/maps/api/js?\`+e;d[q]=f;a.onerror=()=>h=n(Error(p+" could not load."));a.nonce=m.querySelector("script[nonce]")?.nonce||"";m.head.append(a)}));d[l]?console.warn(p+" only loads once. Ignoring:",g):d[l]=(f,...n)=>r.add(f)&&u().then(()=>d[l](f,...n))})({key:${JSON.stringify(key)},v:"weekly"});`;
	document.head.appendChild(bootstrapScript);

	// importLibrary is available immediately after the bootstrap snippet runs.
	const gm = gmapsNS();
	const mapsLib = await gm.importLibrary("maps");

	const mapEl = document.getElementById("map");
	if (!mapEl) {
		report("fatal", "google-maps-no-container");
		return;
	}

	// Size the container with EXPLICIT pixels before constructing the map. Google
	// Maps lays out its inner containers with height:100%, which only resolves
	// against a DEFINITE container height. An auto-height box — a
	// position:fixed;inset:0 element (as the OSM/Mapbox pages use, where MapLibre/
	// mapbox-gl measure the element themselves) or a height:100% box before <body>
	// has a height — leaves Google's inner divs at 0px tall and the map renders
	// permanently blank. window.innerWidth/Height are reliable at module-load time
	// even though <body> has not been laid out yet. Re-apply on resize so the map
	// tracks orientation changes (Google Maps observes the container size).
	const sizeContainer = () => {
		mapEl.style.width = `${window.innerWidth}px`;
		mapEl.style.height = `${window.innerHeight}px`;
	};
	sizeContainer();
	window.addEventListener("resize", sizeContainer);

	// disableDefaultUI suppresses the control buttons only; the Google logo and
	// attribution text render regardless and must remain visible at all times
	// (Google Maps ToS). Do not attempt to hide or reposition the attribution.
	//
	// VECTOR (Map ID present): enable host-driven heading-up rotation + tilt/3D;
	// headingInteractionEnabled is false because heading is driven solely by the
	// host (north-up vs heading-up), never by user rotation gestures. RASTER (no
	// Map ID): omit mapId/heading/tilt entirely — a raster map rejects heading/
	// tilt, and passing them stops moveCamera from positioning.
	const liveMap = new mapsLib.Map(mapEl as HTMLElement, {
		center: { lat: 0, lng: 0 },
		zoom: 1,
		mapTypeId: "roadmap",
		disableDefaultUI: true,
		gestureHandling: "greedy",
		keyboardShortcuts: false,
		...(isVector
			? { mapId, heading: 0, tilt: 0, headingInteractionEnabled: false }
			: {}),
	});
	state.map = liveMap;
	// Traffic layer is created once and toggled on/off via setMap (memoized).
	state.trafficLayer = new mapsLib.TrafficLayer();

	// A programmatic move opens the gesture-suppression window, then issues the
	// immediate (un-animated) camera move. Camera-change events fired by this
	// call land inside the window and are ignored by the gesture detacher.
	//
	// This is also why this page needs no preset-reflow lockstep timing (the
	// mechanism the OSM/Mapbox pages add — see camera.ts isPaddingOnlyReflow
	// and marker-motion.ts): moveCamera is documented as setting the camera
	// "immediately... without animation" (there is no promise-based easeTo
	// equivalent here; smooth motion at GPS cadence relies on frequent small
	// jumps, not a single interpolated call — see the updateCamera doc below).
	// So this call and the marker's left/top write in placeFollowCamera below
	// already land in the same synchronous tick on EVERY push, a genuine fix
	// or a preset-switch reflow alike: there is no separate "camera glide"
	// phase for the marker to fall behind, so both already move in lockstep
	// (a synchronized snap) by construction.
	function moveCam(opts: GMCameraOptions): void {
		state.programmaticUntil = Date.now() + GESTURE_SUPPRESS_MS;
		liveMap.moveCamera(opts);
	}

	// Compute the camera centre that renders `target` at screen offset (dxPx
	// right, dyPx down) from centre, via the flat-Mercator world projection. The
	// screen offset is un-rotated by the map heading so the shift stays
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
		// World units -> screen px scale: the 256-unit world is 2**zoom tiles wide,
		// so one world unit spans 2**zoom screen px.
		const scale = 2 ** zoom;
		const th = (headingDeg * Math.PI) / 180;
		const cos = Math.cos(th);
		const sin = Math.sin(th);
		// world = R(heading) . screen. Screen +x = right, +y = down; world +x =
		// east, +y = south; the two coincide at heading 0. Subtracting the world
		// offset from the target places the target at +screen offset from centre.
		const worldDx = (cos * dxPx - sin * dyPx) / scale;
		const worldDy = (sin * dxPx + cos * dyPx) / scale;
		const center = proj.fromPointToLatLng(
			new (gmapsNS().Point)(worldPt.x - worldDx, worldPt.y - worldDy),
		);
		return center ? { lat: center.lat(), lng: center.lng() } : null;
	}

	// Pin the chevron left-of-centre (and dropped per markerPos) and target the
	// camera at the matching off-centre point so the GPS location renders under
	// it — the OSM/Mapbox `markerEl.left/top` + camera `padding` parity, done
	// without a native padding API. headingDeg is the applied map heading (0 for
	// a raster map). When the projection is not yet ready the camera cannot
	// offset, so the chevron stays centred over the un-offset location.
	function placeFollowCamera(
		target: GMLatLng,
		zoom: number,
		tilt: number,
		headingDeg: number,
		markerPos: number,
		bottomSafe: number,
		rightSafe: number,
		leftSafe: number,
	): void {
		// Net horizontal shift: a right-card reserve shifts the marker left, a
		// left-card reserve shifts it right. Only one is ever non-zero.
		const mx = markerXFraction(rightSafe) - markerXFraction(leftSafe);
		const drop = markerDrop(markerPos, bottomSafe);
		const center =
			offsetCenterFor(
				target,
				-mx * window.innerWidth,
				drop * window.innerHeight,
				zoom,
				headingDeg,
			) ?? target;
		const offsetApplied = center !== target;
		markerEl.style.left = offsetApplied ? `${(0.5 - mx) * 100}%` : "50%";
		markerEl.style.top = offsetApplied ? `${(0.5 + drop) * 100}%` : "50%";
		moveCam(
			state.isVector
				? { center, zoom, heading: headingDeg, tilt }
				: { center, zoom },
		);
	}

	// --- Camera-follow state machine -----------------------------------------

	function easeHome(): void {
		const fix = state.lastFix;
		if (!fix) return;
		// Raster map re-shows the chevron at the last travel bearing so it reads
		// correctly the instant follow re-attaches, before the next fix arrives; a
		// vector map re-syncs the chevron from the next updateCamera push (the map
		// rotates). placeFollowCamera restores the off-centre target + chevron spot.
		if (!state.isVector) syncChevronTransform(0, fix.heading);
		placeFollowCamera(
			{ lat: fix.lat, lng: fix.lng },
			fix.zoom,
			fix.tilt,
			state.isVector ? appliedBearing(state.northUp, fix.heading) : 0,
			fix.markerPos,
			fix.bottomSafe,
			fix.rightSafe,
			fix.leftSafe,
		);
	}

	function setFollowing(follow: boolean): void {
		if (state.following === follow) return;
		state.following = follow;
		report("follow", follow);
		if (follow) {
			if (state.refollowTimer) clearTimeout(state.refollowTimer);
			state.refollowTimer = 0;
			markerEl.style.display = "block";
			easeHome();
		} else {
			// Detached (free pan): the screen-fixed chevron points at arbitrary
			// map, so hide it until the camera re-attaches to the location.
			//
			// DIVERGENCE from the OSM/Mapbox pages: those swap to a geo-anchored
			// clone (syncGeoMarker) so the user still sees their GPS position on the
			// panned map. Google Maps has no mapId-free, non-deprecated geo-marker —
			// AdvancedMarkerElement needs a Cloud-configured mapId, and the classic
			// google.maps.Marker is deprecated (barred by CLAUDE.md#no-suppress). So
			// the self-marker is simply hidden while detached. A geo-anchored
			// OverlayView (the only mapId-free, non-deprecated route, materially more
			// complex) is a documented follow-up.
			markerEl.style.display = "none";
		}
	}

	function armRefollow(): void {
		if (state.refollowTimer) clearTimeout(state.refollowTimer);
		state.refollowTimer = setTimeout(
			() => setFollowing(true),
			AUTO_REFOLLOW_MS,
		);
	}

	// dragstart fires only for user pans (not programmatic moveCamera). Detach
	// follow so the user can free-pan; re-attach AUTO_REFOLLOW_MS after the last
	// gesture or on an explicit host setFollow(true).
	liveMap.addListener("dragstart", () => {
		setFollowing(false);
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
	// refollow timer immediately. zoom_changed fires in both modes; tilt_changed
	// only on a vector map (a raster map cannot tilt — there it never fires).
	for (const ev of ["zoom_changed", "tilt_changed"] as const) {
		liveMap.addListener(ev, () => {
			if (Date.now() <= state.programmaticUntil) return;
			setFollowing(false);
			armRefollow();
		});
	}

	// VECTOR only: heading_changed reports the bearing (throttled) for the host
	// compass overlay and detaches follow on a user rotation. (A raster map is
	// north-up and never rotates, so the event cannot fire — skip the listener.)
	if (isVector) {
		const BEARING_REPORT_INTERVAL_MS = 150;
		const bearingReport = { lastMs: 0, lastSent: "" };
		liveMap.addListener("heading_changed", () => {
			const now = Date.now();
			if (now - bearingReport.lastMs >= BEARING_REPORT_INTERVAL_MS) {
				const bearing = (liveMap.getHeading() ?? 0).toFixed(1);
				if (bearing !== bearingReport.lastSent) {
					bearingReport.lastMs = now;
					bearingReport.lastSent = bearing;
					report("bearing", bearing);
				}
			}
			if (now > state.programmaticUntil) {
				setFollowing(false);
				armRefollow();
			}
		});
	}

	// First tilesloaded marks the map as rendered. The flag also closes the
	// gm_authFailure fatal path (an auth failure can only arrive before the
	// first render). Log to console for diagnostics; the host detects readiness
	// via onPageFinished, not a bridge event. Detach immediately; the event
	// fires repeatedly. google.maps.Map emits no general "error" event, so the
	// only fatal paths are the missing-key check, gm_authFailure, the WebGL
	// pre-check, and the bootstrap/importLibrary rejection caught by
	// initMap().catch().
	const tilesListener = liveMap.addListener("tilesloaded", () => {
		if (state.rendered) return;
		state.rendered = true;
		// Google silently downgrades a VECTOR map to RASTER when the device's WebGL
		// cannot host a vector map (e.g. a low-end head unit with no usable 3D
		// context). Detect the ACTUAL rendering type once tiles are in and downgrade
		// our state so updateCamera stops sending heading/tilt — a raster map rejects
		// them ("not supported on raster maps") and passing them stops the camera
		// from positioning. easeHome re-issues a raster camera move + chevron sync.
		if (state.isVector && liveMap.getRenderingType() === "RASTER") {
			log("vector-fallback-to-raster");
			state.isVector = false;
			easeHome();
		}
		log("rendered");
		tilesListener.remove();
	});

	// Staleness timer: grey the chevron when fixes stop arriving (a tunnel).
	setInterval(() => {
		const stale =
			state.lastFixMs > 0 &&
			Date.now() - state.lastFixMs > LOCATION_STALE_THRESHOLD_MS;
		markerEl.classList.toggle("stale", stale);
	}, 1_000);

	// --- Bridge functions ----------------------------------------------------

	// Android -> JS: smooth camera follow. moveCamera is immediate; at the host's
	// GPS cadence the frame-by-frame instant positioning reads as continuous
	// motion. The chevron is pinned on screen and tinted per fix; markerColor
	// self-heals if the first push raced page load. VECTOR drives heading + tilt
	// (heading-up rotates the map); RASTER sends center + zoom only.
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
		const now = Date.now();
		const sinceLastFixMs = state.lastFixMs > 0 ? now - state.lastFixMs : 0;
		const signalGap = sinceLastFixMs > LOCATION_STALE_THRESHOLD_MS;
		if (signalGap) state.lastBearing = null;

		if (markerColor && markerPath) markerPath.setAttribute("fill", markerColor);
		if (markerColor) markerEl.style.setProperty("--marker-color", markerColor);
		state.lastFixMs = now;
		markerEl.classList.remove("stale");

		const heading = smoothedBearing(state.lastBearing, bearing || 0);
		state.lastBearing = heading;
		const previousZoom = state.lastPushedZoom;
		const z = Number.isFinite(zoom) ? zoom : 16;
		state.lastPushedZoom = z;
		state.lastFix = {
			lat,
			lng: lon,
			heading,
			zoom: z,
			tilt: tilt || 0,
			markerPos,
			bottomSafe,
			rightSafe,
			leftSafe,
		};

		if (!state.following) {
			// Detached (free pan): leave the camera centre where the user panned,
			// but a pushed zoom change is the host's +/- button (head units have no
			// multitouch, so the zoom buttons are mandatory) — apply it around the
			// free camera's own centre.
			if (previousZoom > 0 && state.lastPushedZoom !== previousZoom) {
				moveCam({ zoom: state.lastPushedZoom });
			}
			return;
		}

		syncChevronTransform(tilt || 0, heading);
		markerEl.style.display = "block";
		// VECTOR drives heading-up rotation (chevron points up) + tilt/3D; RASTER is
		// north-up (headingDeg 0, no tilt). placeFollowCamera offsets both the
		// chevron and the camera target so the location sits left-of-centre, clear of
		// the right cards — the OSM/Mapbox parity.
		placeFollowCamera(
			{ lat, lng: lon },
			z,
			tilt || 0,
			state.isVector ? appliedBearing(state.northUp, heading) : 0,
			markerPos,
			bottomSafe,
			rightSafe,
			leftSafe,
		);
	};

	// Android -> JS: switch the map type and toggle the traffic overlay.
	// mapType is a GoogleMapType enum name: ROADMAP / SATELLITE / HYBRID / TERRAIN.
	window.setGoogleMapsOptions = (mapType, traffic) => {
		liveMap.setMapTypeId(MAP_TYPE_IDS[mapType] ?? "roadmap");
		state.trafficLayer?.setMap(traffic ? liveMap : null);
	};

	window.setFollow = (follow) => setFollowing(!!follow);

	window.setNorthUp = (enabled) => {
		state.northUp = !!enabled;
		// Raster maps are north-up only and cannot rotate, so this is a no-op for
		// the map there; the chevron always shows the bearing regardless.
		if (!state.isVector) return;
		// Vector map: re-orient the camera immediately while following; a detached
		// camera keeps the user's rotation until re-attach.
		const fix = state.lastFix;
		if (state.following && fix) {
			moveCam({ heading: appliedBearing(state.northUp, fix.heading) });
			syncChevronTransform(fix.tilt, fix.heading);
		}
	};

	// Host lifecycle resume: re-measure and repaint so the map recovers after a
	// pause/resume cycle (guards a stale GL surface on Android). Google Maps has
	// no resize() API; a window resize event triggers the same relayout path.
	window.onHostResume = () => {
		window.dispatchEvent(new Event("resize"));
	};

	// Replay pending calls that arrived via the module-top-level stubs before
	// this async init completed. Order mirrors the host's push sequence.
	if (pending.options)
		window.setGoogleMapsOptions(pending.options[0], pending.options[1]);
	if (pending.northUp != null) window.setNorthUp(pending.northUp);
	if (pending.follow != null) window.setFollow(pending.follow);
	if (pending.camera) window.updateCamera(...pending.camera);
	if (pending.resume) window.onHostResume();
}

initMap().catch((e) => {
	const msg = e instanceof Error ? e.message : String(e);
	log(`init-exception: ${msg}`);
	report("fatal", `google-maps-init-exception: ${msg}`.slice(0, 200));
});
