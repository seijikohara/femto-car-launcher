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
// the OSM and Mapbox pages — NOT a geo-anchored map marker. Two deliberate
// divergences from those pages:
//   1. RASTER + north-up only. Without a Cloud Map ID the map is a RASTER map,
//      which cannot rotate (no heading-up) or tilt/3D — and passing heading or
//      tilt to moveCamera makes the camera refuse to move at all. So the map is
//      always north-up and the chevron rotates to the travel bearing to convey
//      heading. Full heading-up / tilt / 3D would require a VECTOR map, i.e.
//      the user also supplying a Cloud Map ID — a documented possible follow-up.
//   2. No per-frame camera padding (unlike MapLibre/Mapbox `padding`), so the
//      chevron pins at the rendered location (screen centre); the markerPos /
//      safe-zone look-ahead offset is not applied to the camera.
import {
	AUTO_REFOLLOW_MS,
	LOCATION_STALE_THRESHOLD_MS,
	smoothedBearing,
} from "./camera";

// The Google Maps bridge extends the base femtoBridge with googleMapsApiKey(),
// which is only present when the host has wired up the Google Maps backend.
// Declared locally rather than in the global Window augmentation to avoid a
// type conflict with main.ts's narrower femtoBridge declaration (both compile
// in the same tsconfig scope; TS merges interface Window and requires
// compatible types).
interface GoogleMapsFemtoBridge {
	onMapEvent(kind: string, detail: string): void;
	// Synchronous getter injected by the host; returns the Google Maps API key.
	// Returns an empty string when unconfigured.
	googleMapsApiKey(): string;
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
// Deliberately omits heading/tilt: this page only ever drives a RASTER map,
// which rejects them (passing either stops moveCamera from positioning). The
// narrow type structurally prevents reintroducing that bug.
interface GMCameraOptions {
	center?: GMLatLng;
	zoom?: number;
}
interface GMMapsEventListener {
	remove(): void;
}
interface GMMap {
	moveCamera(opts: GMCameraOptions): void;
	setMapTypeId(id: string): void;
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
// "follow" = camera follow state flips. No "bearing" kind: the raster map is
// permanently north-up, so there is no map rotation to report (the chevron
// conveys heading). No kind triggers a backend switch — there is no auto-fallback.
function report(kind: "fatal" | "error" | "follow", detail: unknown): void {
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
	// Set to true on the first tilesloaded event; gates fatal error reporting
	// (errors after first render are transient, not key/auth failures).
	rendered: false,
	lastErrorReportMs: 0,
	following: true,
	refollowTimer: 0 as ReturnType<typeof setTimeout> | 0,
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
	// No tilt/heading fields: a raster map is north-up and top-down only, so the
	// camera never carries them (only center + zoom). The chevron conveys heading.
	lastFix: null as {
		lat: number;
		lng: number;
		heading: number;
		zoom: number;
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
// while the map slides beneath it (car-nav style). The raster map is permanently
// north-up (it cannot rotate), so the chevron ALWAYS rotates to the travel
// bearing to convey heading. No rotateX: a raster map is top-down (tilt is
// always 0), so there is no tilted ground plane to lay the chevron onto.
const markerEl = document.getElementById("self-marker") as HTMLElement;
const markerPath = markerEl.querySelector("path");

function syncChevronTransform(heading: number): void {
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

	// disableDefaultUI suppresses the control buttons only; the Google logo and
	// attribution text render regardless and must remain visible at all times
	// (Google Maps ToS). Do not attempt to hide or reposition the attribution.
	// No heading/tilt: without a Cloud Map ID this is a RASTER map, which is
	// north-up and top-down only — heading/tilt are unsupported, and passing them
	// to moveCamera makes the camera refuse to move (the chevron conveys heading).
	const liveMap = new mapsLib.Map(mapEl as HTMLElement, {
		center: { lat: 0, lng: 0 },
		zoom: 1,
		mapTypeId: "roadmap",
		disableDefaultUI: true,
		gestureHandling: "greedy",
		keyboardShortcuts: false,
	});
	state.map = liveMap;
	// Traffic layer is created once and toggled on/off via setMap (memoized).
	state.trafficLayer = new mapsLib.TrafficLayer();

	// A programmatic move opens the gesture-suppression window, then issues the
	// immediate (un-animated) camera move. Camera-change events fired by this
	// call land inside the window and are ignored by the gesture detacher.
	function moveCam(opts: GMCameraOptions): void {
		state.programmaticUntil = Date.now() + GESTURE_SUPPRESS_MS;
		liveMap.moveCamera(opts);
	}

	// --- Camera-follow state machine -----------------------------------------

	function easeHome(): void {
		const fix = state.lastFix;
		if (!fix) return;
		// Raster map: center + zoom only (never heading/tilt). Re-show the chevron
		// at the last travel bearing so it reads correctly the instant follow
		// re-attaches, before the next fix arrives.
		moveCam({ center: { lat: fix.lat, lng: fix.lng }, zoom: fix.zoom });
		syncChevronTransform(fix.heading);
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
	// refollow timer immediately. On the north-up raster map zoom_changed is the
	// only one that fires (the map cannot tilt or rotate); tilt_changed is kept
	// as a harmless guard in case a future vector-map config is ever enabled.
	for (const ev of ["zoom_changed", "tilt_changed"] as const) {
		liveMap.addListener(ev, () => {
			if (Date.now() <= state.programmaticUntil) return;
			setFollowing(false);
			armRefollow();
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

	// Android -> JS: north-up camera follow on the raster map. moveCamera is
	// immediate; at the host's GPS cadence the frame-by-frame instant positioning
	// reads as continuous motion. The map stays north-up and the chevron rotates
	// to the travel bearing; the chevron is tinted per fix, and markerColor
	// self-heals if the first push raced page load. tilt is ignored (raster).
	window.updateCamera = (
		lat,
		lon,
		bearing,
		zoom,
		_tilt,
		_markerPos,
		_bottomSafe,
		_rightSafe,
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
		state.lastPushedZoom = Number.isFinite(zoom) ? zoom : 16;
		state.lastFix = {
			lat,
			lng: lon,
			heading,
			zoom: Number.isFinite(zoom) ? zoom : 16,
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

		// Chevron always shows the travel bearing (the raster map is north-up).
		syncChevronTransform(heading);
		markerEl.style.display = "block";
		// Raster map: center + zoom only — never heading/tilt (passing them stops
		// the camera from moving at all).
		moveCam({
			center: { lat, lng: lon },
			zoom: Number.isFinite(zoom) ? zoom : 16,
		});
	};

	// Android -> JS: switch the map type and toggle the traffic overlay.
	// mapType is a GoogleMapType enum name: ROADMAP / SATELLITE / HYBRID / TERRAIN.
	window.setGoogleMapsOptions = (mapType, traffic) => {
		liveMap.setMapTypeId(MAP_TYPE_IDS[mapType] ?? "roadmap");
		state.trafficLayer?.setMap(traffic ? liveMap : null);
	};

	window.setFollow = (follow) => setFollowing(!!follow);

	// Android -> JS: kept because the host calls it for every backend, but a no-op
	// here. A raster map (no Cloud Map ID) is north-up only and cannot rotate, so
	// there is no heading-up mode to toggle; the chevron always shows the bearing.
	window.setNorthUp = (_enabled) => {};

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
