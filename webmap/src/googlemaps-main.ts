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
// Self-marker: the host drives a screen-pinned DOM chevron through
// updateCamera (markerColor / markerPos / safe-zone fractions), exactly like
// the OSM and Mapbox pages — NOT a geo-anchored map marker. The Google Maps
// camera API exposes no per-frame padding/focal-offset primitive (unlike
// MapLibre/Mapbox `padding`), so the chevron is pinned at the rendered
// location (screen centre) to keep it sitting on the user's position; the
// markerPos/safe-zone look-ahead offset the other two backends apply via
// camera padding is a documented divergence here.
import {
	AUTO_REFOLLOW_MS,
	appliedBearing,
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
interface GMCameraOptions {
	center?: GMLatLng;
	zoom?: number;
	heading?: number;
	tilt?: number;
}
interface GMMapsEventListener {
	remove(): void;
}
interface GMMap {
	moveCamera(opts: GMCameraOptions): void;
	setMapTypeId(id: string): void;
	getHeading(): number | undefined;
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
// "follow" = camera follow state flips; "bearing" = throttled map bearing for
// the compass overlay; "render" = the first painted frame. No kind triggers a
// backend switch — there is no auto-fallback.
function report(
	kind: "fatal" | "error" | "follow" | "bearing" | "render",
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
	// Set to true on the first tilesloaded event; gates fatal error reporting
	// (errors after first render are transient, not key/auth failures).
	rendered: false,
	lastErrorReportMs: 0,
	following: true,
	refollowTimer: 0 as ReturnType<typeof setTimeout> | 0,
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
	lastFix: null as {
		lat: number;
		lng: number;
		heading: number;
		zoom: number;
		tilt: number;
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
// and the map slides + rotates beneath it (car-nav style). Heading-up by
// default: the chevron points to the top of the frame and the map rotates to
// the travel bearing; north-up keeps the map north-aligned and rotates the
// chevron instead. rotateX lays it onto the tilted ground plane.
const markerEl = document.getElementById("self-marker") as HTMLElement;
const markerPath = markerEl.querySelector("path");

function syncChevronTransform(tilt: number, heading: number): void {
	const turn = state.northUp ? heading : 0;
	markerEl.style.transform = `translate(-50%, -50%) perspective(600px) rotateX(${tilt}deg) rotateZ(${turn}deg)`;
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
	// headingInteractionEnabled is false: heading is driven solely by the host
	// (heading-up vs north-up), never by user rotation gestures.
	const liveMap = new mapsLib.Map(mapEl as HTMLElement, {
		center: { lat: 0, lng: 0 },
		zoom: 1,
		heading: 0,
		tilt: 0,
		mapTypeId: "roadmap",
		disableDefaultUI: true,
		gestureHandling: "greedy",
		keyboardShortcuts: false,
		headingInteractionEnabled: false,
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
		moveCam({
			center: { lat: fix.lat, lng: fix.lng },
			zoom: fix.zoom,
			heading: appliedBearing(state.northUp, fix.heading),
			tilt: fix.tilt,
		});
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

	// Zoom / tilt change events carry no user-vs-programmatic flag, so gate on
	// the suppression window: a change outside it is a user gesture. These have
	// no "end" event, so re-arm the refollow timer immediately.
	for (const ev of ["zoom_changed", "tilt_changed"] as const) {
		liveMap.addListener(ev, () => {
			if (Date.now() <= state.programmaticUntil) return;
			setFollowing(false);
			armRefollow();
		});
	}

	// heading_changed both reports the bearing (throttled) for the host compass
	// overlay and detaches follow on a user rotation. Heading interaction is
	// disabled, but a programmatic heading push while following still reports
	// the bearing so the overlay tracks the heading-up rotation.
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

	// First tilesloaded marks the map as rendered. After this point map errors
	// are transient (flaky tiles), not fatal key/auth failures. Detach
	// immediately; the event fires repeatedly.
	const tilesListener = liveMap.addListener("tilesloaded", () => {
		if (state.rendered) return;
		state.rendered = true;
		log("rendered");
		report("render", "");
		tilesListener.remove();
	});

	// An error before first render is almost always an invalid API key or
	// network failure — surface a fatal so the host shows the key-entry notice.
	// Errors after the first render are transient (flaky tiles): log-only.
	liveMap.addListener("error", () => {
		if (state.rendered) {
			reportErrorThrottled("map-error");
		} else {
			report("fatal", "google-maps-load-error");
		}
	});

	// Staleness timer: grey the chevron when fixes stop arriving (a tunnel).
	setInterval(() => {
		const stale =
			state.lastFixMs > 0 &&
			Date.now() - state.lastFixMs > LOCATION_STALE_THRESHOLD_MS;
		markerEl.classList.toggle("stale", stale);
	}, 1_000);

	// --- Bridge functions ----------------------------------------------------

	// Android -> JS: smooth heading-up camera follow. moveCamera is immediate;
	// at the host's GPS cadence the frame-by-frame instant positioning reads as
	// continuous motion. The chevron is pinned on screen and tinted per fix; the
	// markerColor self-heals if the first push raced page load.
	window.updateCamera = (
		lat,
		lon,
		bearing,
		zoom,
		tilt,
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
			tilt: tilt || 0,
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
		moveCam({
			center: { lat, lng: lon },
			zoom: Number.isFinite(zoom) ? zoom : 16,
			heading: appliedBearing(state.northUp, heading),
			tilt: tilt || 0,
		});
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
		// Re-orient the camera immediately while following; a detached camera
		// keeps the user's rotation until re-attach.
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
