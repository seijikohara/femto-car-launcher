// LIVE map page wiring, bundled into assets/web/ and hosted in the launcher's
// WebView (see WebMapView.kt for the host side of every contract in this file).
// Pure style logic lives in style.ts; this module owns the DOM, the MapLibre
// instance, and the Android bridge.
import maplibregl from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";
import {
	type AccentColors,
	injectFeatures,
	MAX_MARKER_DROP,
	markerPadTop,
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
			markerColor: string,
		) => void;
		setStyleUrl: (url: string, bg: string, water: string, land: string) => void;
		setFeatures: (buildings: boolean, terrain: boolean) => void;
		onHostResume: () => void;
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

// JS -> Android error channel (window.femtoBridge, injected by the host via
// addJavascriptInterface). "fatal" = definitive never-going-to-render facts
// (no WebGL context, map construction threw); "error" = transient resource
// failures (tile / style / DEM fetch), log-only on the host. Neither kind
// triggers a backend switch — the no-fallback rule above still holds.
function report(kind: "fatal" | "error", detail: unknown): void {
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
	styleFadeGen: 0,
	firstCamera: true,
	lastErrorReportMs: 0,
};

function applyStyle(): void {
	if (state.map && state.currentStyleUrl) {
		state.map.setStyle(state.currentStyleUrl, {
			transformStyle: (_previous, next) =>
				injectFeatures(next, {
					buildings: state.buildings,
					terrain: state.terrain,
					accent: state.accentColors,
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
	const el = document.getElementById("style-fade") as HTMLElement;
	const img = el.querySelector("img") as HTMLImageElement;
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
				if (gen !== state.styleFadeGen) {
					liveMap.off("render", check);
					return;
				}
				if (liveMap.isStyleLoaded()) {
					liveMap.off("render", check);
					fadeOut();
				}
			};
			liveMap.on("render", check);
			setTimeout(fadeOut, STYLE_FADE_MAX_WAIT_MS);
			liveMap.triggerRepaint();
		}, 0);
	});
	liveMap.triggerRepaint();
}

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
	liveMap.on("render", () => {
		if (liveMap.isStyleLoaded()) log("rendered");
	});
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

	// Tile / style / DEM fetch failures surface here. A flaky link can fire
	// this once per tile, so reports to the host are throttled to one per
	// ERROR_REPORT_INTERVAL_MS; the host only logs them (transient by
	// definition — never UI, never a backend switch).
	const ERROR_REPORT_INTERVAL_MS = 10000;
	liveMap.on("error", (e) => {
		const detail = e?.error?.message || "unknown map error";
		log(`error: ${detail}`);
		const now = Date.now();
		if (now - state.lastErrorReportMs >= ERROR_REPORT_INTERVAL_MS) {
			state.lastErrorReportMs = now;
			report("error", String(detail).slice(0, 200));
		}
	});

	// Self-location chevron: a fixed-on-screen DOM overlay (see #self-marker CSS).
	// The camera brings the location to the chevron's spot, so the chevron stays
	// still and the map slides + rotates beneath it (car-nav style) rather than a
	// geo-anchored marker sliding to catch the eased camera. It is heading-UP: the
	// chevron always points to the top of the frame and the map rotates to the
	// travel bearing; rotateX lays it onto the tilted ground plane.
	const markerEl = document.getElementById("self-marker") as HTMLElement;
	const markerPath = markerEl.querySelector("path");

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
		markerColor,
	) => {
		if (!state.map) return;
		const heading = bearing || 0;
		if (markerColor && markerPath) markerPath.setAttribute("fill", markerColor);
		const pos = Math.max(0, Math.min(100, markerPos || 0));
		markerEl.style.top = `${50 + (pos / 100) * MAX_MARKER_DROP * 100}%`;
		markerEl.style.transform = `translate(-50%, -50%) perspective(600px) rotateX(${tilt || 0}deg)`;
		markerEl.style.display = "block";
		const opts = {
			center: [lon, lat] as [number, number],
			bearing: heading,
			zoom: zoom || 16,
			pitch: tilt || 0,
			padding: {
				top: markerPadTop(markerPos, liveMap.getContainer().clientHeight || 0),
				bottom: 0,
				left: 0,
				right: 0,
			},
		};
		if (state.firstCamera) {
			state.firstCamera = false;
			liveMap.jumpTo(opts);
		} else {
			liveMap.easeTo({ ...opts, duration: 1000, essential: true });
		}
	};
	// Android -> JS: switch the base style and (for the ACCENT scheme) its
	// recolour palette; empty colour args mean a plain, non-accent style.
	window.setStyleUrl = (url, bg, water, land) => {
		if (!url) return;
		state.currentStyleUrl = url;
		state.accentColors = bg
			? { background: bg, water: water, land: land }
			: null;
		applyStyleWithFade();
	};
	// Android -> JS: flip the LIVE feature toggles, then re-apply the style so
	// transformStyle injects (or omits) the matching layers/sources.
	window.setFeatures = (buildings, terrain) => {
		state.buildings = !!buildings;
		state.terrain = !!terrain;
		applyStyleWithFade();
	};
	// Host (Android) calls this on lifecycle resume so the map re-measures and
	// repaints after the WebView was paused / not visible (guards a stale GL
	// surface): map.resize() + triggerRepaint().
	window.onHostResume = () => {
		if (state.map) {
			liveMap.resize();
			liveMap.triggerRepaint();
		}
	};
} catch (e) {
	log(`exception:${e instanceof Error ? e.message : e}`);
	// The map object never came up; this page will stay blank forever.
	report("fatal", `map-init-exception: ${e instanceof Error ? e.message : e}`);
}
