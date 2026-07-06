// LIVE map page wiring, bundled into assets/web/ and hosted in the launcher's
// WebView (see WebMapView.kt for the host side of every contract in this file).
// Pure style logic lives in style.ts; this module owns the DOM, the MapLibre
// instance, and the Android bridge.
import maplibregl from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";
import {
	AUTO_REFOLLOW_MS,
	appliedBearing,
	easeDurationMs,
	isPaddingOnlyReflow,
	LOCATION_STALE_THRESHOLD_MS,
	linearEase,
	PRESET_REFLOW_MS,
	smoothedBearing,
} from "./camera";
import { createMarkerTransition } from "./marker-motion";
import {
	type AccentColors,
	injectFeatures,
	markerDrop,
	markerPadLeft,
	markerPadRight,
	markerPadTop,
	markerXFraction,
} from "./style";

// Android -> JS surface, called by the host through evaluateJavascript. Every
// function is feature-detected on the Kotlin side (`window.updateCamera && ...`),
// so the names below are a compatibility contract — never rename without
// updating WebMapView.kt.
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
		setFeatures: (
			buildings: boolean,
			terrain: boolean,
			buildingColor: string,
		) => void;
		onHostResume: () => void;
		setFollow: (follow: boolean) => void;
		setNorthUp: (enabled: boolean) => void;
	}
}

// Diagnostic logging only (visible via chrome://inspect or the debug build's
// WebChromeClient). There is NO host fallback path: the chosen render backend
// is kept as-is, and MapLibre's own context-loss restore handles transient
// WebGL drops, so the page never asks the host to switch away.
function log(msg: string): void {
	try {
		console.log(`[map] ${msg}`);
	} catch {
		// Logging must never break the page.
	}
}

// JS -> Android event channel (window.femtoBridge, injected by the host via
// addJavascriptInterface). "fatal" = definitive never-going-to-render facts
// (no WebGL context, map construction threw); "error" = transient resource
// failures (tile / style / DEM fetch), log-only on the host; "follow" =
// camera-follow state flips; "bearing" = throttled camera bearing for the
// compass overlay. No kind triggers a backend switch — the no-fallback rule
// above still holds.
function report(
	kind: "fatal" | "error" | "follow" | "bearing",
	detail: unknown,
): void {
	try {
		window.femtoBridge?.onMapEvent(kind, String(detail ?? ""));
	} catch {
		// The bridge may be absent outside the launcher (e.g. desktop dev server).
	}
}

// Mutable page state in one const holder (let/var are banned — see biome.json
// and no-let.grit). Camera pushes, style swaps, and error throttling all read
// and write through here.
const state = {
	map: undefined as maplibregl.Map | undefined,
	currentStyleUrl: "https://tiles.openfreemap.org/styles/positron",
	// Set by setStyleUrl for the ACCENT scheme, or null for a plain style.
	accentColors: null as AccentColors | null,
	buildings: false,
	terrain: false,
	// Theme-tracked 3D extrusion colour, set by setFeatures alongside the toggle.
	buildingColor: "",
	styleFadeGen: 0,
	firstCamera: true,
	lastErrorReportMs: 0,
	// Wall-clock ms of the last camera push; 0 until the first fix. The stale
	// timer ages this to decide when the chevron greys out, and updateCamera
	// measures the inter-fix interval from it to match its ease duration.
	lastFixMs: 0,
	// The bearing last applied to the camera, for jitter smoothing; null until
	// the first fix (and reset after a signal gap) so those adopt the raw value.
	lastBearing: null as number | null,
	// Camera-follow state: true (default) keeps the camera glued to the fixes;
	// a user gesture detaches it (free pan), and AUTO_REFOLLOW_MS after the
	// last gesture — or an explicit setFollow(true) from the host — re-attaches.
	following: true,
	refollowTimer: 0 as ReturnType<typeof setTimeout> | 0,
	// North-up pins the map to north and rotates the chevron to the heading
	// instead of rotating the map; pushed by the host from the persisted setting.
	northUp: false,
	// While detached the screen-fixed chevron is wrong (it points at arbitrary
	// map), so a geo-anchored clone tracks the real GPS position instead.
	geoMarker: null as maplibregl.Marker | null,
	// The latest pushed fix, kept so a re-attach can ease the camera home and
	// the geo marker can be (re)placed without waiting for the next fix.
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
	// The zoom last pushed by the host; a change while detached is the user's
	// +/- button, applied to the free camera around its own centre.
	lastPushedZoom: 0,
};

function applyStyle(): void {
	if (state.map && state.currentStyleUrl) {
		state.map.setStyle(state.currentStyleUrl, {
			transformStyle: (_previous, next) =>
				injectFeatures(next, {
					buildings: state.buildings,
					terrain: state.terrain,
					accent: state.accentColors,
					buildingColor: state.buildingColor,
				}),
		});
	}
}

// Cross-fade a style swap: capture the outgoing style's last frame from the
// GL canvas, pin it over the map, apply the new style, then fade the capture
// out once the new style has loaded and presented a frame. A timeout guard
// fades anyway so a style that never loads (offline) cannot pin the stale
// capture; the generation counter lets a rapid second swap cancel the first
// swap's pending callbacks.
const STYLE_FADE_MS = 500;
const STYLE_FADE_MAX_WAIT_MS = 4000;
function applyStyleWithFade(): void {
	const liveMap = state.map;
	if (!liveMap) return;
	// Nothing rendered yet (initial load): swap without a fade.
	if (!liveMap.isStyleLoaded()) {
		applyStyle();
		return;
	}
	state.styleFadeGen += 1;
	const gen = state.styleFadeGen;
	const el = document.getElementById("style-fade");
	const img = el?.querySelector("img");
	if (!el || !(img instanceof HTMLImageElement)) {
		// #style-fade and its <img> are committed in map.html; a missing node is
		// a build error, not a runtime state. This function has no surrounding
		// try, so skip the cross-fade (apply directly) rather than throw.
		log("style-fade nodes missing; applying style without fade");
		applyStyle();
		return;
	}
	// The GL buffer is only valid synchronously inside a render event, so the
	// capture happens there (no preserveDrawingBuffer cost) — but the style
	// swap itself is deferred OUT of the render loop: a setStyle issued
	// mid-render leaves the presented frame stale on a static camera, so the
	// new scheme only appeared as the camera moved.
	liveMap.once("render", () => {
		if (gen !== state.styleFadeGen) return;
		try {
			img.src = liveMap.getCanvas().toDataURL("image/jpeg", 0.85);
			el.style.transition = "none";
			el.style.opacity = "1";
			el.style.display = "block";
		} catch (e) {
			log(`style-fade capture failed: ${e instanceof Error ? e.message : e}`);
		}
		setTimeout(() => {
			if (gen !== state.styleFadeGen) return;
			applyStyle();
			const fade = { done: false };
			const fadeOut = (): void => {
				if (fade.done || gen !== state.styleFadeGen) return;
				fade.done = true;
				// Detach here too: the MAX_WAIT timeout path reaches fadeOut without
				// going through `check`, so the render listener would otherwise leak
				// for the page lifetime when a style never loads (offline).
				liveMap.off("render", check);
				el.style.transition = `opacity ${STYLE_FADE_MS}ms ease`;
				el.style.opacity = "0";
				setTimeout(() => {
					if (gen === state.styleFadeGen) el.style.display = "none";
				}, STYLE_FADE_MS + 100);
			};
			// Fade once the swapped-in style has loaded and a frame with it has
			// been rendered (the same readiness signal the host uses); "idle"
			// would be nicer but never fires while the GPS camera keeps easing.
			const check = (): void => {
				// Superseded swap, or the new style has presented a frame: fadeOut()
				// runs the off("render", check) teardown in both cases.
				if (gen !== state.styleFadeGen || liveMap.isStyleLoaded()) fadeOut();
			};
			liveMap.on("render", check);
			setTimeout(fadeOut, STYLE_FADE_MAX_WAIT_MS);
			liveMap.triggerRepaint();
		}, 0);
	});
	liveMap.triggerRepaint();
}

// Tile / style / DEM fetch failures (and any uncaught page error) can fire per
// frame on a flaky link, so reports to the host are throttled to one per
// interval. Date.now is the page clock; the host only logs these.
const ERROR_REPORT_INTERVAL_MS = 10000;
function reportErrorThrottled(detail: string): void {
	const now = Date.now();
	if (now - state.lastErrorReportMs >= ERROR_REPORT_INTERVAL_MS) {
		state.lastErrorReportMs = now;
		report("error", detail.slice(0, 200));
	}
}

// A post-init exception inside a bridge call (updateCamera / setStyleUrl / ...)
// or an unhandled rejection would otherwise reach only the JS console, invisible
// on an adb-unreachable head unit. Route both through the throttled channel so
// the in-app diagnostics tail sees them.
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

(() => {
	const c = document.createElement("canvas");
	if (!(c.getContext("webgl2") || c.getContext("webgl"))) {
		log("no-webgl-context");
		report("fatal", "no-webgl-context");
	}
})();

try {
	const liveMap = new maplibregl.Map({
		container: "map",
		style: "https://tiles.openfreemap.org/styles/positron",
		center: [0, 0],
		zoom: 1,
		attributionControl: false,
	});
	state.map = liveMap;
	// Log the first rendered frame once, then detach: "render" fires on every
	// painted frame, so a persistent listener spews ~60 lines/sec into logcat
	// (and the in-app diagnostics tail) during the GPS camera ease. Diagnostics
	// only — the host detects readiness via onPageFinished, not this log.
	const onFirstRender = (): void => {
		if (!liveMap.isStyleLoaded()) return;
		log("rendered");
		liveMap.off("render", onFirstRender);
	};
	liveMap.on("render", onFirstRender);
	liveMap.on("load", () => log("load"));

	// WebGL context loss is usually TRANSIENT on mobile / WebView GPUs, and
	// MapLibre auto-recovers: it preventDefault()s the loss, saves the style,
	// and on webglcontextrestored rebuilds the painter and re-renders (see
	// map.ts _contextLost / _contextRestored, re-fired as Map events). We rely
	// on that built-in restore and do not fall back to another backend.
	liveMap.on("webglcontextlost", () =>
		log("webglcontextlost (awaiting MapLibre restore)"),
	);
	liveMap.on("webglcontextrestored", () => log("webglcontextrestored"));

	// Tile / style / DEM fetch failures surface here; the host only logs them
	// (transient by definition — never UI, never a backend switch).
	liveMap.on("error", (e) => {
		const detail = e?.error?.message || "unknown map error";
		log(`error: ${detail}`);
		reportErrorThrottled(String(detail));
	});

	// Self-location chevron: a fixed-on-screen DOM overlay (see #self-marker CSS).
	// The camera brings the location to the chevron's spot, so the chevron stays
	// still and the map slides + rotates beneath it (car-nav style) rather than a
	// geo-anchored marker sliding to catch the eased camera. It is heading-UP: the
	// chevron always points to the top of the frame and the map rotates to the
	// travel bearing; rotateX lays it onto the tilted ground plane.
	const markerEl = document.getElementById("self-marker") as HTMLElement;
	const markerPath = markerEl.querySelector("path");
	// Lockstep control for a preset-switch reflow — see isPaddingOnlyReflow
	// and marker-motion.ts.
	const markerTransition = createMarkerTransition(markerEl, PRESET_REFLOW_MS);

	// Grey the chevron and stop its ripple once fixes stop arriving (signal lost
	// in a tunnel): the host pushes a fix per update and goes quiet on loss, so
	// the page itself ages the last push. The .stale CSS class greyscales the
	// chevron and hides the ripple; updateCamera clears it on the next fix.
	setInterval(() => {
		const stale =
			state.lastFixMs > 0 &&
			Date.now() - state.lastFixMs > LOCATION_STALE_THRESHOLD_MS;
		markerEl.classList.toggle("stale", stale);
		// The geo-anchored clone shown while detached must grey out too.
		state.geoMarker?.getElement().classList.toggle("stale", stale);
	}, 1000);

	// --- Camera-follow state machine -----------------------------------------
	// A user gesture detaches the follow (free pan); the geo-anchored marker
	// keeps tracking the GPS position, and the camera re-attaches after
	// AUTO_REFOLLOW_MS of stillness or an explicit host setFollow(true).

	function geoMarkerElement(): HTMLElement {
		const el = document.createElement("div");
		// The shared .self-marker class carries the ripple, colour, and stale
		// styling; the screen-pinning rules stay on the #self-marker id only.
		el.className = "self-marker";
		el.innerHTML = markerEl.innerHTML;
		el.style.width = "36px";
		el.style.height = "36px";
		return el;
	}

	function syncGeoMarker(): void {
		const fix = state.lastFix;
		if (!fix) return;
		if (!state.geoMarker) {
			state.geoMarker = new maplibregl.Marker({
				element: geoMarkerElement(),
				rotationAlignment: "map",
				pitchAlignment: "map",
			});
			state.geoMarker.setLngLat([fix.lon, fix.lat]).addTo(liveMap);
		} else {
			state.geoMarker.setLngLat([fix.lon, fix.lat]);
		}
		state.geoMarker.setRotation(fix.heading);
		// Mirror the DOM chevron (the single source for marker colour and
		// staleness) so an accent change or a signal loss while detached
		// reaches the clone too.
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

	// North-up keeps the map pinned to north and rotates the chevron to the
	// heading instead; heading-up rotates the map and the chevron points up.
	// rotateX lays the chevron onto the tilted ground plane.
	function syncChevronTransform(tilt: number, heading: number): void {
		const turn = state.northUp ? heading : 0;
		markerEl.style.transform = `translate(-50%, -50%) perspective(600px) rotateX(${tilt}deg) rotateZ(${turn}deg)`;
	}

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
			// Ease home in one continuous transition; the per-fix cadence easing
			// resumes from the next push.
			easeHome(600);
		} else {
			// The screen-fixed chevron points at arbitrary map while detached;
			// the geo-anchored clone tracks the real position instead.
			markerEl.style.display = "none";
			syncGeoMarker();
		}
	}
	window.setFollow = (follow) => setFollowing(!!follow);
	window.setNorthUp = (enabled) => {
		state.northUp = !!enabled;
		// Re-orient immediately while following; a detached camera keeps the
		// user's rotation until re-attach. The chevron flips with the camera —
		// waiting for the next fix would leave it pointing wrong for up to one
		// GPS interval.
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

	function armRefollow(): void {
		if (state.refollowTimer) clearTimeout(state.refollowTimer);
		state.refollowTimer = setTimeout(
			() => setFollowing(true),
			AUTO_REFOLLOW_MS,
		);
	}
	// dragstart fires only for user drags; zoom/rotate/pitch start also fire for
	// camera API moves, so those gate on originalEvent (user input only).
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

	// Report the camera bearing (throttled) so the host's compass overlay can
	// track the map orientation in either mode. Dedupe on the rounded payload,
	// not the raw float — getBearing() rarely returns bit-identical values, so
	// a float compare would re-send visually identical bearings.
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

	// Android -> JS: smooth heading-up camera follow (easeTo interpolates between
	// sparse GPS fixes). The first fix jumps (no fly-in from [0,0]); the rest ease.
	// The chevron is pinned on screen (not geo-anchored), so only the camera moves
	// and the chevron stays put while the map slides + rotates beneath it. The map
	// rotates to the travel bearing, so the fixed chevron always reads as forward;
	// rotateX lays it onto the tilted ground plane to match the map pitch. The
	// chevron's screen height tracks markerPos, matching the camera padding so the
	// location renders right under it. [markerColor] (Material primary) self-heals
	// if the first push raced page load.
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
		// compared against it to tell a genuine GPS fix (the center moves) from
		// a preset-switch reflow (the center holds, only the safe-area padding
		// changes) — see isPaddingOnlyReflow.
		const previousFix = state.lastFix;
		// Measure the inter-fix interval BEFORE refreshing lastFixMs: the ease
		// duration matches it so each ease finishes as the next fix lands.
		const now = Date.now();
		const sinceLastFixMs = state.lastFixMs > 0 ? now - state.lastFixMs : 0;
		// A gap past the stale threshold means the position was lost (a tunnel);
		// easing across it would glide through geometry, so snap instead and
		// restart bearing smoothing from the raw value.
		const signalGap = sinceLastFixMs > LOCATION_STALE_THRESHOLD_MS;
		if (signalGap) state.lastBearing = null;
		// A fresh fix: re-colour the chevron, feed the ripple the same colour, and
		// clear any stale greyout from a prior signal gap.
		if (markerColor && markerPath) markerPath.setAttribute("fill", markerColor);
		if (markerColor) markerEl.style.setProperty("--marker-color", markerColor);
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
			// Detached (free pan): the camera stays the user's, the geo-anchored
			// marker tracks the fixes — except a pushed ZOOM change is the user's
			// +/- button, applied to the free camera around its own centre.
			syncGeoMarker();
			if (previousZoom > 0 && state.lastPushedZoom !== previousZoom) {
				liveMap.easeTo({
					zoom: state.lastPushedZoom,
					duration: 250,
					essential: true,
				});
			}
			return;
		}
		// A preset switch re-pushes the SAME fix with only the padding changed;
		// a signal gap always takes the jumpTo path below regardless, so it can
		// never qualify as a reflow.
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
		// Lockstep: arm the marker's CSS transition on a reflow so its left/top
		// write below glides instead of jumping; clear it otherwise so a real
		// fix keeps snapping the screen-pinned marker while the camera eases
		// the ground underneath it.
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
				left: markerPadLeft(leftSafe, liveMap.getContainer().clientWidth || 0),
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
			// Fixed lockstep duration (not the cadence-matched one below) so the
			// camera lands exactly when the marker's CSS transition finishes.
			liveMap.easeTo({
				...opts,
				duration: PRESET_REFLOW_MS,
				easing: linearEase,
				essential: true,
			});
		} else {
			// Cadence-matched duration + linear easing: back-to-back segments
			// compose into one continuous glide instead of the fixed-1000 ms
			// cubic eases that restarted (accelerate-decelerate) on every fix.
			liveMap.easeTo({
				...opts,
				duration: easeDurationMs(sinceLastFixMs),
				easing: linearEase,
				essential: true,
			});
		}
	};
	// Android -> JS: switch the base style and (for the ACCENT scheme) its
	// recolour palette; an empty bg means a plain, non-accent style.
	window.setStyleUrl = (
		url,
		bg,
		water,
		land,
		roadMajor,
		roadMinor,
		roadCasing,
		building,
		label,
	) => {
		if (!url) return;
		state.currentStyleUrl = url;
		state.accentColors = bg
			? {
					background: bg,
					water: water,
					land: land,
					roadMajor: roadMajor,
					roadMinor: roadMinor,
					roadCasing: roadCasing,
					building: building,
					label: label,
				}
			: null;
		applyStyleWithFade();
	};
	// Android -> JS: flip the LIVE feature toggles (plus the theme-tracked
	// extrusion colour), then re-apply the style so transformStyle injects
	// (or omits) the matching layers/sources.
	window.setFeatures = (buildings, terrain, buildingColor) => {
		state.buildings = !!buildings;
		state.terrain = !!terrain;
		state.buildingColor = buildingColor || "";
		applyStyleWithFade();
	};
	// Host (Android) calls this on lifecycle resume so the map re-measures and
	// repaints after the WebView was paused / not visible (guards a stale GL
	// surface): map.resize() + triggerRepaint().
	window.onHostResume = () => {
		// liveMap is captured and never nulled (the page never destroys the map),
		// so no guard is needed.
		liveMap.resize();
		liveMap.triggerRepaint();
	};
} catch (e) {
	log(`exception:${e instanceof Error ? e.message : e}`);
	// The map object never came up; this page will stay blank forever.
	report(
		"fatal",
		`map-init-exception: ${e instanceof Error ? e.message : e}`.slice(0, 200),
	);
}
