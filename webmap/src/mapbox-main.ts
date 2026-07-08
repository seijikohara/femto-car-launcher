// Mapbox GL JS LIVE map page — the paid-tier backend. Mirrors the OSM page
// (main.ts) host-bridge contract so WebMapView.kt can drive either page with
// the same evaluateJavascript calls. camera.ts pure math is reused unchanged.
//
// Bundling note: mapbox-gl ships as a self-contained UMD bundle whose built-in
// worker uses `new URL('./worker', import.meta.url)`. Vite's module-worker
// transform rewrites that URL at build time — but only for ESM workers, not
// for workers embedded in a pre-built UMD. The UMD bundle is therefore loaded
// as a classic <script> (injected below) so its internal worker URL resolves
// against the final asset path at runtime, not against Vite's build graph.
// Vite still processes the CSS import and the `?url` asset import, emitting
// both as hashed local files with no CDN dependency.
import "mapbox-gl/dist/mapbox-gl.css";
// Types only — the runtime object comes from the UMD global set by the script.
import type * as MapboxGLTypes from "mapbox-gl";
// `?url` makes Vite emit the pre-built UMD as a hashed local asset; we inject
// it as a classic <script> so the self-contained worker loads without Vite's
// module-worker transform (which breaks at build.target chrome109).
import mapboxglUrl from "mapbox-gl/dist/mapbox-gl.js?url";
import {
	AUTO_REFOLLOW_MS,
	appliedBearing,
	easeDurationMs,
	isPaddingOnlyReflow,
	LAYOUT_REFLOW_MS,
	LOCATION_STALE_THRESHOLD_MS,
	linearEase,
	smoothedBearing,
} from "./camera";
import {
	mapboxStyleUrl,
	TRAFFIC_LAYER_ID,
	TRAFFIC_SOURCE_ID,
	TRAFFIC_SOURCE_SPEC,
	trafficLayerSpec,
} from "./mapbox-style";
import { createMarkerTransition } from "./marker-motion";
import {
	markerDrop,
	markerPadLeft,
	markerPadRight,
	markerPadTop,
	markerXFraction,
} from "./style";

// The Mapbox bridge extends the base femtoBridge with mapboxToken(), which is
// only present when the host has wired up the paid backend. Declared locally
// rather than in the global Window augmentation to avoid a type conflict with
// main.ts's narrower femtoBridge declaration (both compile in the same tsconfig
// scope; TS merges interface Window across files and requires compatible types).
interface MapboxFemtoBridge {
	onMapEvent(kind: string, detail: string): void;
	// Synchronous getter injected by the host; returns the Mapbox public-token
	// (`pk.*`) for this build. Returns empty string when unconfigured.
	mapboxToken(): string;
}

// Android -> JS bridge surface. Every function is feature-detected on the Kotlin
// side (`window.updateCamera && ...`), so these names are a compatibility
// contract — never rename without updating WebMapView.kt.
declare global {
	interface Window {
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
		setMapboxStyle: (
			styleId: string,
			lightPreset: "day" | "night",
			traffic: boolean,
		) => void;
		onHostResume: () => void;
		setFollow: (follow: boolean) => void;
		setNorthUp: (enabled: boolean) => void;
	}
}

// Typed accessor for the Mapbox-capable bridge. Uses a local cast instead of
// widening the global Window.femtoBridge type (which main.ts also declares).
function mapboxBridge(): MapboxFemtoBridge | undefined {
	return window.femtoBridge as MapboxFemtoBridge | undefined;
}

function log(msg: string): void {
	try {
		console.log(`[mapbox] ${msg}`);
	} catch {
		// Logging must never break the page.
	}
}

// JS -> Android event channel. "fatal" = definitive never-going-to-render facts;
// "error" = transient resource failures, log-only on the host; "follow" = camera
// follow state flips; "bearing" = throttled map bearing for the compass overlay.
// No kind triggers a backend switch — there is no auto-fallback.
function report(
	kind: "fatal" | "error" | "follow" | "bearing",
	detail: unknown,
): void {
	try {
		mapboxBridge()?.onMapEvent(kind, String(detail ?? ""));
	} catch {
		// The bridge may be absent outside the launcher (e.g. desktop dev server).
	}
}

// Pending bridge calls recorded by module-top-level stubs so that host pushes
// arriving before the UMD `onload` are not silently dropped. `onPageFinished`
// (which gates host pushes) can fire before the injected `<script>` `onload`,
// so the first `setMapboxStyle` push — which is rarely repeated — would be
// lost if the bridge functions were only wired inside `initMap`. The stubs
// record the latest call; `initMap` replays them after the real implementations
// are installed. Stubs are installed unconditionally so they work even when
// the token check exits `initMap` early.
const pending = {
	camera: null as Parameters<Window["updateCamera"]> | null,
	style: null as Parameters<Window["setMapboxStyle"]> | null,
	follow: null as boolean | null,
	northUp: null as boolean | null,
	resume: false,
};
window.updateCamera = (...a) => {
	pending.camera = a;
};
window.setMapboxStyle = (...a) => {
	pending.style = a;
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

// Mutable page state in one const holder (let/var are banned — see biome.json
// and no-let.grit). All camera pushes and style ops read+write through here.
const state = {
	// Typed as the MapboxGLTypes namespace Map; undefined until the UMD loads and
	// `initMap` constructs it.
	map: undefined as MapboxGLTypes.Map | undefined,
	firstCamera: true,
	lastErrorReportMs: 0,
	lastFixMs: 0,
	lastBearing: null as number | null,
	following: true,
	refollowTimer: 0 as ReturnType<typeof setTimeout> | 0,
	northUp: false,
	geoMarker: null as MapboxGLTypes.Marker | null,
	lastFix: null as {
		lon: number;
		lat: number;
		heading: number;
		zoom: number;
		tilt: number;
		markerPos: number;
		bottomSafe: number;
		rightSafe: number;
		leftSafe: number;
	} | null,
	lastPushedZoom: 0,
	styleLoaded: false,
};

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

// Verify WebGL is available before spending any budget on map construction.
// Mapbox requires WebGL 1 at minimum; report fatal immediately if absent.
(() => {
	const c = document.createElement("canvas");
	if (!(c.getContext("webgl2") || c.getContext("webgl"))) {
		log("no-webgl-context");
		report("fatal", "no-webgl-context");
	}
})();

// The UMD bundle sets `window.mapboxgl` when loaded as a classic script.
// Its shape is the library's default export (the `mapboxgl` namespace object),
// not the ES module namespace. `MapboxGLTypes.default` is that object's type.
type MapboxGLNamespace = typeof MapboxGLTypes.default & {
	Map: typeof MapboxGLTypes.Map;
	Marker: typeof MapboxGLTypes.Marker;
};
function getMapboxGL(): MapboxGLNamespace {
	return (window as unknown as { mapboxgl: MapboxGLNamespace }).mapboxgl;
}

// --- Chevron (self-marker) helpers -------------------------------------------

const markerEl = document.getElementById("self-marker") as HTMLElement;
const markerPath = markerEl.querySelector("path");
// Lockstep control for a layout reflow — see isPaddingOnlyReflow
// and marker-motion.ts.
const markerTransition = createMarkerTransition(markerEl, LAYOUT_REFLOW_MS);

// The heading-up chevron stays fixed on screen; only the camera moves. North-up
// mode keeps the map north-aligned and rotates the chevron to the travel bearing.
function syncChevronTransform(tilt: number, heading: number): void {
	const turn = state.northUp ? heading : 0;
	markerEl.style.transform = `translate(-50%, -50%) perspective(600px) rotateX(${tilt}deg) rotateZ(${turn}deg)`;
}

// While detached from follow, a geo-anchored clone of the chevron tracks the
// GPS fix so the user can still see where they are on the free-panned map.
function geoMarkerElement(): HTMLElement {
	// Clone the live chevron node so the geo-anchored copy has identical
	// structure (ripple span + SVG) without parsing HTML strings.
	const el = markerEl.cloneNode(true) as HTMLElement;
	// Strip the id: only one #self-marker may exist in the document.
	el.removeAttribute("id");
	el.style.width = "36px";
	el.style.height = "36px";
	// The screen-pinned position rules live on #self-marker; the clone uses
	// only the shared .self-marker class (handled by Mapbox Marker placement).
	el.style.position = "";
	el.style.left = "";
	el.style.top = "";
	el.style.display = "";
	return el;
}

function syncGeoMarker(): void {
	const fix = state.lastFix;
	const liveMap = state.map;
	if (!fix || !liveMap) return;
	const mapboxgl = getMapboxGL();
	if (!state.geoMarker) {
		state.geoMarker = new mapboxgl.Marker({
			element: geoMarkerElement(),
			rotationAlignment: "map",
			pitchAlignment: "map",
		});
		state.geoMarker.setLngLat([fix.lon, fix.lat]).addTo(liveMap);
	} else {
		state.geoMarker.setLngLat([fix.lon, fix.lat]);
	}
	state.geoMarker.setRotation(fix.heading);
	// Mirror the DOM chevron colour and stale state so they stay in sync while
	// the camera is detached.
	const el = state.geoMarker.getElement();
	const fill = markerPath?.getAttribute("fill");
	const clonePath = el.querySelector("path");
	if (fill && clonePath) clonePath.setAttribute("fill", fill);
	el.style.setProperty(
		"--marker-color",
		markerEl.style.getPropertyValue("--marker-color"),
	);
	el.classList.toggle("stale", markerEl.classList.contains("stale"));
}

// --- Traffic layer toggling --------------------------------------------------

function applyTraffic(on: boolean): void {
	const liveMap = state.map;
	if (!liveMap) return;
	const has = liveMap.getLayer(TRAFFIC_LAYER_ID) != null;
	if (on && !has) {
		if (liveMap.getSource(TRAFFIC_SOURCE_ID) == null) {
			liveMap.addSource(
				TRAFFIC_SOURCE_ID,
				TRAFFIC_SOURCE_SPEC as MapboxGLTypes.SourceSpecification,
			);
		}
		liveMap.addLayer(trafficLayerSpec() as MapboxGLTypes.LayerSpecification);
	} else if (!on && has) {
		liveMap.removeLayer(TRAFFIC_LAYER_ID);
	}
}

// --- Map construction (runs inside the UMD script's onload) -----------------

function initMap(): void {
	const mapboxgl = getMapboxGL();

	// A missing token means the paid feature cannot function; report a fatal so
	// the host shows an explanatory notice rather than a blank or broken map.
	const token = mapboxBridge()?.mapboxToken() ?? "";
	if (!token) {
		log("mapbox-token-missing");
		report("fatal", "mapbox-token-missing");
		return;
	}

	mapboxgl.accessToken = token;

	try {
		// attributionControl is intentionally not suppressed: Mapbox ToS require
		// the Mapbox logo and attribution text to remain visible at all times.
		// A compact control keeps the overlay small on head-unit displays.
		const liveMap = new mapboxgl.Map({
			container: "map",
			style: mapboxStyleUrl("standard"),
			center: [0, 0],
			zoom: 1,
			attributionControl: false,
		});
		liveMap.addControl(new mapboxgl.AttributionControl({ compact: true }));
		state.map = liveMap;

		// Log the first rendered frame once; detach immediately after to avoid
		// flooding logcat at ~60 lines/sec during GPS camera easing. The first
		// successful render also marks the style as loaded so any later error is
		// treated as transient, never as a token failure.
		const onFirstRender = (): void => {
			if (!liveMap.isStyleLoaded()) return;
			state.styleLoaded = true;
			log("rendered");
			liveMap.off("render", onFirstRender);
		};
		liveMap.on("render", onFirstRender);
		liveMap.on("load", () => {
			state.styleLoaded = true;
			log("load");
		});

		liveMap.on("webglcontextlost", () =>
			log("webglcontextlost (awaiting Mapbox restore)"),
		);
		liveMap.on("webglcontextrestored", () => log("webglcontextrestored"));

		liveMap.on("error", (e) => {
			const detail =
				(e as { error?: { message?: string } })?.error?.message ??
				"unknown map error";
			log(`error: ${detail}`);
			// An error before the style has ever loaded is almost always an
			// invalid/blank access token (or no network) — surface a fatal so the
			// host shows the token notice instead of a silent blank map. A slow
			// but valid load fires no error, so this never false-positives on it.
			// Errors after the style loaded are transient (flaky tiles): log-only.
			if (state.styleLoaded) {
				reportErrorThrottled(String(detail));
			} else {
				report("fatal", String(detail));
			}
		});

		// Staleness timer: grey the chevron when fixes stop arriving (tunnel).
		setInterval(() => {
			const stale =
				state.lastFixMs > 0 &&
				Date.now() - state.lastFixMs > LOCATION_STALE_THRESHOLD_MS;
			markerEl.classList.toggle("stale", stale);
			state.geoMarker?.getElement().classList.toggle("stale", stale);
		}, 1_000);

		// --- Camera-follow state machine -------------------------------------

		function easeHome(durationMs: number): void {
			const fix = state.lastFix;
			if (!fix) return;
			liveMap.easeTo({
				center: [fix.lon, fix.lat],
				bearing: appliedBearing(state.northUp, fix.heading),
				zoom: fix.zoom,
				pitch: fix.tilt,
				padding: {
					top: markerPadTop(
						fix.markerPos,
						fix.bottomSafe,
						liveMap.getContainer().clientHeight || 0,
					),
					bottom: 0,
					left: markerPadLeft(
						fix.leftSafe,
						liveMap.getContainer().clientWidth || 0,
					),
					right: markerPadRight(
						fix.rightSafe,
						liveMap.getContainer().clientWidth || 0,
					),
				},
				duration: durationMs,
				essential: true,
			});
		}

		function setFollowing(follow: boolean): void {
			if (state.following === follow) return;
			state.following = follow;
			report("follow", follow);
			if (follow) {
				if (state.refollowTimer) clearTimeout(state.refollowTimer);
				state.refollowTimer = 0;
				state.geoMarker?.remove();
				state.geoMarker = null;
				markerEl.style.display = "block";
				easeHome(600);
			} else {
				// Screen-fixed chevron points at arbitrary map while detached;
				// swap to the geo-anchored clone that tracks the real position.
				markerEl.style.display = "none";
				syncGeoMarker();
			}
		}

		function armRefollow(): void {
			if (state.refollowTimer) clearTimeout(state.refollowTimer);
			state.refollowTimer = setTimeout(
				() => setFollowing(true),
				AUTO_REFOLLOW_MS,
			);
		}

		// dragstart fires only for user drags; zoom/rotate/pitch start also fire
		// for camera API moves, so those gate on originalEvent (user input only).
		liveMap.on("dragstart", () => {
			setFollowing(false);
			if (state.refollowTimer) clearTimeout(state.refollowTimer);
		});
		for (const ev of ["zoomstart", "rotatestart", "pitchstart"] as const) {
			liveMap.on(ev, (e) => {
				if ((e as { originalEvent?: unknown }).originalEvent) {
					setFollowing(false);
					if (state.refollowTimer) clearTimeout(state.refollowTimer);
				}
			});
		}
		for (const ev of ["dragend", "zoomend", "rotateend", "pitchend"] as const) {
			liveMap.on(ev, () => {
				if (!state.following) armRefollow();
			});
		}

		// Throttled bearing reports for the host's compass overlay.
		const BEARING_REPORT_INTERVAL_MS = 150;
		const bearingReport = { lastMs: 0, lastSent: "" };
		liveMap.on("move", () => {
			const now = Date.now();
			if (now - bearingReport.lastMs < BEARING_REPORT_INTERVAL_MS) return;
			const bearing = liveMap.getBearing().toFixed(1);
			if (bearing === bearingReport.lastSent) return;
			bearingReport.lastMs = now;
			bearingReport.lastSent = bearing;
			report("bearing", bearing);
		});

		// --- Bridge functions ------------------------------------------------

		// Android -> JS: smooth heading-up camera follow. First fix jumps (no
		// fly-in from [0,0]); subsequent fixes ease with cadence-matched duration.
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
			// Captured before state.lastFix below is overwritten with this push:
			// compared against it to tell a genuine GPS fix (the center moves)
			// from a layout reflow (the center holds, only the safe-area
			// padding changes) — see isPaddingOnlyReflow.
			const previousFix = state.lastFix;
			const now = Date.now();
			const sinceLastFixMs = state.lastFixMs > 0 ? now - state.lastFixMs : 0;
			const signalGap = sinceLastFixMs > LOCATION_STALE_THRESHOLD_MS;
			if (signalGap) state.lastBearing = null;

			if (markerColor && markerPath)
				markerPath.setAttribute("fill", markerColor);
			if (markerColor)
				markerEl.style.setProperty("--marker-color", markerColor);
			state.lastFixMs = now;
			markerEl.classList.remove("stale");

			const heading = smoothedBearing(state.lastBearing, bearing || 0);
			state.lastBearing = heading;
			const previousZoom = state.lastPushedZoom;
			state.lastPushedZoom = Number.isFinite(zoom) ? zoom : 16;
			state.lastFix = {
				lon,
				lat,
				heading,
				zoom: Number.isFinite(zoom) ? zoom : 16,
				tilt: tilt || 0,
				markerPos: markerPos || 0,
				bottomSafe: bottomSafe || 0,
				rightSafe: rightSafe || 0,
				leftSafe: leftSafe || 0,
			};

			if (!state.following) {
				syncGeoMarker();
				// A pushed zoom change is the user's +/- button; apply it to the
				// free camera so the zoom buttons work while panning.
				if (previousZoom > 0 && state.lastPushedZoom !== previousZoom) {
					liveMap.easeTo({
						zoom: state.lastPushedZoom,
						duration: 250,
						essential: true,
					});
				}
				return;
			}

			// A dashboard layout change (dock position, card visibility, driver
			// side) re-pushes the SAME fix with only the padding changed; a
			// signal gap always takes the jumpTo path below regardless, so it
			// can never qualify as a reflow.
			const isReflow =
				!signalGap &&
				isPaddingOnlyReflow(previousFix, {
					lon,
					lat,
					markerPos: markerPos || 0,
					bottomSafe: bottomSafe || 0,
					rightSafe: rightSafe || 0,
					leftSafe: leftSafe || 0,
				});
			// Lockstep: arm the marker's CSS transition on a reflow so its
			// left/top write below glides instead of jumping; clear it otherwise
			// so a real fix keeps snapping the screen-pinned marker while the
			// camera eases the ground underneath it.
			markerTransition.setActive(isReflow);
			markerEl.style.left = `${(0.5 - markerXFraction(rightSafe) + markerXFraction(leftSafe)) * 100}%`;
			markerEl.style.top = `${50 + markerDrop(markerPos, bottomSafe) * 100}%`;
			syncChevronTransform(tilt || 0, heading);
			markerEl.style.display = "block";

			const opts = {
				center: [lon, lat] as [number, number],
				bearing: appliedBearing(state.northUp, heading),
				zoom: Number.isFinite(zoom) ? zoom : 16,
				pitch: tilt || 0,
				padding: {
					top: markerPadTop(
						markerPos,
						bottomSafe,
						liveMap.getContainer().clientHeight || 0,
					),
					bottom: 0,
					left: markerPadLeft(
						leftSafe,
						liveMap.getContainer().clientWidth || 0,
					),
					right: markerPadRight(
						rightSafe,
						liveMap.getContainer().clientWidth || 0,
					),
				},
			};
			if (state.firstCamera || signalGap) {
				state.firstCamera = false;
				liveMap.jumpTo(opts);
			} else if (isReflow) {
				// Fixed lockstep duration (not the cadence-matched one below) so
				// the camera lands exactly when the marker's CSS transition
				// finishes.
				liveMap.easeTo({
					...opts,
					duration: LAYOUT_REFLOW_MS,
					easing: linearEase,
					essential: true,
				});
			} else {
				// Cadence-matched linear easing: back-to-back eases compose into one
				// continuous glide rather than stuttering accelerate-decelerate arcs.
				liveMap.easeTo({
					...opts,
					duration: easeDurationMs(sinceLastFixMs),
					easing: linearEase,
					essential: true,
				});
			}
		};

		// Android -> JS: switch Mapbox base style, apply Standard lightPreset,
		// and restore the traffic layer after the style reloads its sources.
		window.setMapboxStyle = (styleId, lightPreset, traffic) => {
			liveMap.setStyle(mapboxStyleUrl(styleId));
			liveMap.once("style.load", () => {
				// Standard v3 fragment API; safe-no-op on non-Standard styles.
				liveMap.setConfigProperty("basemap", "lightPreset", lightPreset);
				applyTraffic(traffic);
			});
		};

		window.setFollow = (follow) => setFollowing(!!follow);

		window.setNorthUp = (enabled) => {
			state.northUp = !!enabled;
			// Re-orient the camera immediately while following; a detached camera
			// keeps the user's rotation until re-attach.
			const fix = state.lastFix;
			if (state.following && fix) {
				liveMap.easeTo({
					bearing: appliedBearing(state.northUp, fix.heading),
					duration: 400,
					essential: true,
				});
				syncChevronTransform(fix.tilt, fix.heading);
			}
		};

		// Host lifecycle resume: resize so the map re-measures the WebView after
		// a pause/resume cycle (guards a stale GL surface on Android).
		window.onHostResume = () => {
			liveMap.resize();
			liveMap.triggerRepaint();
		};

		// Replay any bridge calls that arrived via the module-top-level stubs
		// before this `onload` fired. Order mirrors the host's push sequence.
		if (pending.style) window.setMapboxStyle(...pending.style);
		if (pending.northUp != null) window.setNorthUp(pending.northUp);
		if (pending.follow != null) window.setFollow(pending.follow);
		if (pending.camera) window.updateCamera(...pending.camera);
		if (pending.resume) window.onHostResume();
	} catch (e) {
		log(`exception: ${e instanceof Error ? e.message : e}`);
		report(
			"fatal",
			`map-init-exception: ${e instanceof Error ? e.message : e}`.slice(0, 200),
		);
	}
}

// Inject the UMD bundle as a classic <script>. The script sets window.mapboxgl
// before onload fires, so initMap can safely call new mapboxgl.Map.
const s = document.createElement("script");
s.src = mapboxglUrl;
s.onload = initMap;
s.onerror = () => report("fatal", "mapbox-lib-load-failed");
document.head.appendChild(s);
