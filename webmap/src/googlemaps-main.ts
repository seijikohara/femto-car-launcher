// Google Maps JS API LIVE map page. Mirrors the OSM (main.ts) and Mapbox
// (mapbox-main.ts) host-bridge contract so WebMapView.kt can drive all three
// pages with the same evaluateJavascript calls.
//
// The Google Maps JS API is loaded at runtime from Google's CDN via the
// official inline bootstrap loader — it must never be bundled or self-hosted
// (Google Maps Platform ToS). The loader is injected programmatically so
// the API key comes from the Android bridge at runtime rather than being
// baked into the HTML at build time.
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

// Minimal type stubs for the Google Maps JS API (CDN-loaded at runtime,
// never bundled). These cover only the surface this page exercises.
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
interface GMAdvancedMarker {
	position: GMLatLng | null;
	map: GMMap | null;
}
interface GMAdvancedMarkerOptions {
	position: GMLatLng;
	map: GMMap;
	title?: string;
	content?: HTMLElement;
	zIndex?: number;
}
interface GMTrafficLayer {
	setMap(map: GMMap | null): void;
}
interface GMPolyline {
	setMap(map: GMMap | null): void;
	setPath(path: GMLatLng[]): void;
}
interface GMPolylineOptions {
	path?: GMLatLng[];
	geodesic?: boolean;
	strokeColor?: string;
	strokeOpacity?: number;
	strokeWeight?: number;
	map?: GMMap;
}
interface GMMapsLibrary {
	Map: new (el: HTMLElement, opts: Record<string, unknown>) => GMMap;
	TrafficLayer: new () => GMTrafficLayer;
	Polyline: new (opts: GMPolylineOptions) => GMPolyline;
}
interface GMMarkerLibrary {
	AdvancedMarkerElement: new (
		opts: GMAdvancedMarkerOptions,
	) => GMAdvancedMarker;
}
interface GMNamespace {
	importLibrary(name: "maps"): Promise<GMMapsLibrary>;
	importLibrary(name: "marker"): Promise<GMMarkerLibrary>;
	importLibrary(name: string): Promise<Record<string, unknown>>;
}

// JSON payload shape for setFeatures (overlays: self-position + route).
interface FeaturesPayload {
	position?: { lat: number; lng: number };
	markerColor?: string;
	route?: Array<{ lat: number; lng: number }>;
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
// facts; "error" = transient resource failures; "follow" = camera follow
// state flips; "bearing" = throttled map bearing for the compass overlay.
// No kind triggers a backend switch — there is no auto-fallback.
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

// Throttle per-frame error reports so a flaky tile server does not spray
// logcat with thousands of identical lines.
const ERROR_REPORT_INTERVAL_MS = 10_000;

window.addEventListener("error", (e) => {
	log(`uncaught: ${e.message}`);
});
window.addEventListener("unhandledrejection", (e) => {
	const reason =
		e.reason instanceof Error ? e.reason.message : String(e.reason);
	log(`unhandledrejection: ${reason}`);
});

// Google Maps requires WebGL; report fatal immediately if absent so the
// host shows a clear notice rather than a silent blank page.
(() => {
	const c = document.createElement("canvas");
	if (!(c.getContext("webgl2") || c.getContext("webgl"))) {
		log("no-webgl-context");
		report("fatal", "no-webgl-context");
	}
})();

// Pending bridge calls queued by module-top-level stubs so that host pushes
// arriving before initMap completes are not silently dropped. onPageFinished
// can fire before the async import chain resolves on a slow first load.
const pending = {
	camera: null as Parameters<Window["updateCamera"]> | null,
	options: null as [string, boolean] | null,
	features: null as string | null,
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
// setFeatures is Google Maps specific (OSM uses a 3-param variant); assign
// via the window object directly to avoid a type conflict with main.ts's
// Window.setFeatures declaration that shares the same tsconfig scope.
// The double-cast through unknown is required because Window.setFeatures from
// main.ts (buildings, terrain, buildingColor) is not directly castable to the
// 1-arg JSON variant — the signatures are incompatible for a direct cast.
(window as unknown as { setFeatures: (json: string) => void }).setFeatures = (
	json,
) => {
	pending.features = json;
};

// Mutable page state in one const holder (let/var are banned — see biome.json
// and no-let.grit). All camera pushes and API ops read+write through here.
const state = {
	map: undefined as GMMap | undefined,
	selfMarker: null as GMAdvancedMarker | null,
	routePolyline: null as GMPolyline | null,
	trafficLayer: null as GMTrafficLayer | null,
	// Set to true on the first tilesloaded event; gates fatal error reporting
	// (errors after first render are transient, not token/auth failures).
	rendered: false,
	lastErrorReportMs: 0,
	following: true,
	refollowTimer: 0 as ReturnType<typeof setTimeout> | 0,
	northUp: false,
	lastBearing: null as number | null,
	lastFixMs: 0,
	firstCamera: true,
	lastPushedZoom: 0,
	lastFix: null as {
		lat: number;
		lng: number;
		heading: number;
		zoom: number;
		tilt: number;
	} | null,
};

// Class constructors populated once after importLibrary resolves; held here
// so setFeatures and setGoogleMapsOptions can reach them after init.
const libs = {
	PolylineCtor: null as GMMapsLibrary["Polyline"] | null,
	AdvancedMarkerCtor: null as GMMarkerLibrary["AdvancedMarkerElement"] | null,
	TrafficLayerCtor: null as GMMapsLibrary["TrafficLayer"] | null,
};

// Maps the GoogleMapType enum name strings from the Android bridge to the
// Google Maps JS API map type id strings.
const MAP_TYPE_IDS: Record<string, string> = {
	ROADMAP: "roadmap",
	SATELLITE: "satellite",
	HYBRID: "hybrid",
	TERRAIN: "terrain",
};

function reportErrorThrottled(detail: string): void {
	const now = Date.now();
	if (now - state.lastErrorReportMs >= ERROR_REPORT_INTERVAL_MS) {
		state.lastErrorReportMs = now;
		report("error", detail.slice(0, 200));
	}
}

// --- Camera-follow state machine --------------------------------------------

function easeHome(): void {
	const fix = state.lastFix;
	const liveMap = state.map;
	if (!fix || !liveMap) return;
	liveMap.moveCamera({
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
		easeHome();
	}
}

function armRefollow(): void {
	if (state.refollowTimer) clearTimeout(state.refollowTimer);
	state.refollowTimer = setTimeout(() => setFollowing(true), AUTO_REFOLLOW_MS);
}

// --- Traffic layer toggle ---------------------------------------------------

function applyTraffic(on: boolean): void {
	const liveMap = state.map;
	if (!liveMap) return;
	if (on) {
		if (!state.trafficLayer) {
			state.trafficLayer = libs.TrafficLayerCtor
				? new libs.TrafficLayerCtor()
				: null;
		}
		state.trafficLayer?.setMap(liveMap);
	} else {
		state.trafficLayer?.setMap(null);
	}
}

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
	const markerLib = await gm.importLibrary("marker");

	libs.PolylineCtor = mapsLib.Polyline;
	libs.TrafficLayerCtor = mapsLib.TrafficLayer;
	libs.AdvancedMarkerCtor = markerLib.AdvancedMarkerElement;

	const mapEl = document.getElementById("map");
	if (!mapEl) {
		report("fatal", "google-maps-no-container");
		return;
	}

	// attributionControl is left at its default (visible): Google Maps ToS
	// require the Google logo and attribution text to remain visible at all
	// times. Do not attempt to suppress or reposition the attribution overlay.
	//
	// mapId "DEMO_MAP_ID" enables vector-map features including AdvancedMarker
	// support and heading control. Production deployments should supply a real
	// Map ID configured in Google Cloud Console.
	const liveMap = new mapsLib.Map(mapEl as HTMLElement, {
		center: { lat: 0, lng: 0 },
		zoom: 1,
		heading: 0,
		tilt: 0,
		mapTypeId: "roadmap",
		mapId: "DEMO_MAP_ID",
		disableDefaultUI: true,
		gestureHandling: "greedy",
		keyboardShortcuts: false,
	});
	state.map = liveMap;

	// First tilesloaded marks the map as rendered. After this point map errors
	// are transient (flaky tiles), not fatal key/auth failures — so no further
	// fatal reports are issued. Detach immediately; the event fires repeatedly.
	const tilesListener = liveMap.addListener("tilesloaded", () => {
		if (state.rendered) return;
		state.rendered = true;
		log("rendered");
		report("render", "");
		tilesListener.remove();
	});

	// Errors before first render are almost always an invalid API key or
	// network failure. Surface them as fatal so the host shows the key-entry
	// notice. Errors after the first render are transient; log-only.
	liveMap.addListener("error", () => {
		if (state.rendered) {
			reportErrorThrottled("map-error");
		} else {
			report("fatal", "google-maps-load-error");
		}
	});

	// Throttled bearing reports so the host compass overlay can track
	// orientation in either heading-up or north-up mode.
	const BEARING_REPORT_INTERVAL_MS = 150;
	const bearingReport = { lastMs: 0, lastSent: "" };
	liveMap.addListener("heading_changed", () => {
		const now = Date.now();
		if (now - bearingReport.lastMs < BEARING_REPORT_INTERVAL_MS) return;
		const raw = liveMap.getHeading() ?? 0;
		const bearing = raw.toFixed(1);
		if (bearing === bearingReport.lastSent) return;
		bearingReport.lastMs = now;
		bearingReport.lastSent = bearing;
		report("bearing", bearing);
	});

	// dragstart fires only for user drags (not for programmatic moveCamera
	// calls). Detach follow so the user can free-pan, then arm a refollow
	// timer so the camera reattaches after AUTO_REFOLLOW_MS of stillness.
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

	// --- Bridge functions ----------------------------------------------------

	// Android -> JS: smooth heading-up camera follow. First fix jumps (no
	// fly-in from [0,0]); subsequent fixes use moveCamera for instant
	// positioning — at 1 Hz GPS cadence, frame-by-frame instant updates
	// read as continuous motion without the camera lag of animation.
	window.updateCamera = (
		lat,
		lon,
		bearing,
		zoom,
		tilt,
		_markerPos,
		_bottomSafe,
		_rightSafe,
		_markerColor,
	) => {
		const now = Date.now();
		const sinceLastFixMs = state.lastFixMs > 0 ? now - state.lastFixMs : 0;
		const signalGap = sinceLastFixMs > LOCATION_STALE_THRESHOLD_MS;
		if (signalGap) state.lastBearing = null;
		state.lastFixMs = now;

		const heading = smoothedBearing(state.lastBearing, bearing || 0);
		state.lastBearing = heading;
		state.lastPushedZoom = Number.isFinite(zoom) ? zoom : 16;
		state.lastFix = {
			lat,
			lng: lon,
			heading,
			zoom: Number.isFinite(zoom) ? zoom : 16,
			tilt: tilt || 0,
		};

		if (!state.following) {
			// Detached: honour pushed zoom changes (the host's +/- buttons) but
			// leave the camera centre where the user panned.
			return;
		}

		liveMap.moveCamera({
			center: { lat, lng: lon },
			zoom: Number.isFinite(zoom) ? zoom : 16,
			heading: appliedBearing(state.northUp, heading),
			tilt: tilt || 0,
		});
		state.firstCamera = false;
	};

	// Android -> JS: place or update the self-location AdvancedMarkerElement
	// and the route Polyline. JSON payload: { position?, markerColor?, route? }.
	// Clears prior overlays before applying the new state.
	(window as unknown as { setFeatures: (json: string) => void }).setFeatures = (
		json,
	) => {
		try {
			const data = JSON.parse(json) as FeaturesPayload;

			// Self-position marker.
			if (data.position) {
				if (state.selfMarker) {
					state.selfMarker.position = data.position;
				} else if (libs.AdvancedMarkerCtor) {
					try {
						state.selfMarker = new libs.AdvancedMarkerCtor({
							position: data.position,
							map: liveMap,
							title: "Current location",
						});
					} catch (markerErr) {
						// AdvancedMarkerElement requires a mapId-enabled Map. Log and
						// continue — the camera still works without the marker overlay.
						log(
							`marker-init-failed: ${markerErr instanceof Error ? markerErr.message : markerErr}`,
						);
					}
				}
			}

			// Route polyline — clear the previous one first.
			if (state.routePolyline) {
				state.routePolyline.setMap(null);
				state.routePolyline = null;
			}
			if (data.route && data.route.length > 0 && libs.PolylineCtor) {
				state.routePolyline = new libs.PolylineCtor({
					path: data.route,
					geodesic: true,
					strokeColor: data.markerColor ?? "#4285F4",
					strokeOpacity: 1.0,
					strokeWeight: 4,
					map: liveMap,
				});
			}
		} catch (e) {
			log(`setFeatures-error: ${e instanceof Error ? e.message : e}`);
		}
	};

	// Android -> JS: switch the map type and toggle traffic overlay.
	// mapType is a GoogleMapType enum name: ROADMAP / SATELLITE / HYBRID / TERRAIN.
	window.setGoogleMapsOptions = (mapType, traffic) => {
		liveMap.setMapTypeId(MAP_TYPE_IDS[mapType] ?? "roadmap");
		applyTraffic(traffic);
	};

	window.setFollow = (follow) => setFollowing(!!follow);

	window.setNorthUp = (enabled) => {
		state.northUp = !!enabled;
		const fix = state.lastFix;
		if (state.following && fix) {
			liveMap.moveCamera({
				heading: appliedBearing(state.northUp, fix.heading),
			});
		}
	};

	// Host lifecycle resume: re-measure and repaint so the map recovers after
	// a pause/resume cycle (guards a stale GL surface on Android).
	window.onHostResume = () => {
		// Google Maps does not expose a resize() API; a window resize event
		// triggers the same internal relayout path.
		window.dispatchEvent(new Event("resize"));
	};

	// Replay pending calls that arrived via the module-top-level stubs before
	// this async init completed. Order mirrors the host's push sequence.
	if (pending.options)
		window.setGoogleMapsOptions(pending.options[0], pending.options[1]);
	if (pending.northUp != null) window.setNorthUp(pending.northUp);
	if (pending.follow != null) window.setFollow(pending.follow);
	if (pending.camera) window.updateCamera(...pending.camera);
	if (pending.features)
		(window as unknown as { setFeatures: (json: string) => void }).setFeatures(
			pending.features,
		);
	if (pending.resume) window.onHostResume();
}

initMap().catch((e) => {
	const msg = e instanceof Error ? e.message : String(e);
	log(`init-exception: ${msg}`);
	report("fatal", `google-maps-init-exception: ${msg}`.slice(0, 200));
});
