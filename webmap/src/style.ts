// Pure style-transformation logic, kept free of MapLibre runtime and DOM state
// so it is unit-testable (see style.test.ts); main.ts owns the page wiring.
import type { LayerSpecification, StyleSpecification } from "maplibre-gl";

export interface AccentColors {
	background: string;
	water: string;
	land: string;
	roadMajor: string;
	roadMinor: string;
	roadCasing: string;
	building: string;
}

// The feature merge is parameterised on the page state instead of reading it,
// so a test can exercise every combination without touching globals.
// [buildingColor] is the theme-tracked 3D extrusion colour, applied for EVERY
// scheme (not just ACCENT) so the buildings stay subdued on any base style.
export interface MapFeatures {
	buildings: boolean;
	terrain: boolean;
	accent: AccentColors | null;
	buildingColor: string;
}

// Subdued, see-through buildings: the extrusion must never outshout the roads
// (the car-nav priority), so it renders semi-transparent in its theme colour.
export const BUILDING_EXTRUSION_OPACITY = 0.5;

// DEM = Mapterhorn (free, no key); the provider's required credit is shown by
// the host's Compose attribution overlay when terrain is active.
export const MAPTERHORN_DEM_URL = "https://tiles.mapterhorn.com/tilejson.json";

const ACCENT_LAND = ["landcover", "landuse", "park"];

// The lowest the self-marker drops below centre, as a fraction of map height,
// at markerPos = 100 (mirrors MAX_MARKER_DROP in MapPanel.kt).
export const MAX_MARKER_DROP = 0.32;

// Top camera padding (px) that places the centred location at the height the
// marker-position setting asks for. padTop shifts the focal point down, so the
// location (and its marker) sits lower in the frame; markerPos 0 keeps it
// centred, 100 drops it MAX_MARKER_DROP of the height toward the speed panel.
export function markerPadTop(
	markerPos: number,
	containerHeight: number,
): number {
	const pos = Math.max(0, Math.min(100, markerPos || 0));
	const drop = (pos / 100) * MAX_MARKER_DROP;
	return 2 * drop * containerHeight;
}

// The first vector source id in a style (the OpenMapTiles source), so 3D
// buildings work across positron / the bundled dark.json without hard-coding.
export function vectorSourceId(style: StyleSpecification): string | undefined {
	const sources = style.sources ?? {};
	return Object.keys(sources).find((id) => sources[id].type === "vector");
}

// Road classification by layer id, shared between the bundled light/dark styles.
// "subtle" (the low-zoom motorway representation) classifies as minor: in the dark
// base its colour is identical to highway_minor, and as "major" it would render a
// casing-less dark hairline on the dark background. Railways, piers, oneway
// arrows, and aeroways fall outside the highway_/tunnel_ prefixes and keep their
// base colours. Mirrors roadColorOrNull in MapStyleRecolor.kt — keep in sync.
export function roadClassOrNull(
	layer: LayerSpecification,
): "casing" | "minor" | "major" | null {
	const sourceLayer = (layer as { "source-layer"?: string })["source-layer"];
	if (layer.type !== "line" || sourceLayer !== "transportation") return null;
	if (!layer.id.startsWith("highway_") && !layer.id.startsWith("tunnel_"))
		return null;
	if (layer.id.includes("casing")) return "casing";
	if (["minor", "path", "subtle"].some((k) => layer.id.includes(k)))
		return "minor";
	return "major";
}

// Style-spec paint maps are per-layer-type unions; this helper funnels the few
// dynamic recolour writes through one narrow cast.
function setPaint(
	layer: LayerSpecification,
	key: string,
	value: unknown,
): void {
	const mutable = layer as { paint?: Record<string, unknown> };
	mutable.paint = mutable.paint ?? {};
	mutable.paint[key] = value;
}

// MapLibre transformStyle body: merge the active feature layers/sources into a
// freshly-loaded base style. OFF features are simply not injected, so toggling
// off and re-setting the style removes them cleanly.
export function injectFeatures(
	nextStyle: StyleSpecification,
	features: MapFeatures,
): StyleSpecification {
	nextStyle.sources = nextStyle.sources ?? {};
	nextStyle.layers = nextStyle.layers ?? [];
	// Idempotent: strip any of OUR previously-injected layers/sources first, so a
	// re-applied style (toggle change, or a setStyle that races the initial load
	// and reuses the current style) never produces a duplicate-id error.
	nextStyle.layers = nextStyle.layers.filter(
		(l) => l.id !== "femto-3d-buildings",
	);
	// Only strip terrain if WE injected it (terrainSource present), so a base
	// style that ever ships its own terrain is left intact.
	if (nextStyle.sources.terrainSource) {
		delete nextStyle.sources.terrainSource;
		delete nextStyle.terrain;
	}
	// ACCENT scheme: recolour background / water / land / building fills and the
	// transportation lines with the accent palette. Roads keep their base widths,
	// so the motorway/major/minor hierarchy survives; labels stay untouched.
	// Mirrors the Kotlin recolorAccent in MapStyleRecolor.kt — keep the layer
	// groups and the road classification in sync.
	const accent = features.accent;
	if (accent) {
		for (const l of nextStyle.layers) {
			const sourceLayer = (l as { "source-layer"?: string })["source-layer"];
			const roadClass = roadClassOrNull(l);
			if (l.type === "background")
				setPaint(l, "background-color", accent.background);
			else if (l.type === "fill" && sourceLayer === "water")
				setPaint(l, "fill-color", accent.water);
			else if (
				l.type === "fill" &&
				sourceLayer !== undefined &&
				ACCENT_LAND.includes(sourceLayer)
			)
				setPaint(l, "fill-color", accent.land);
			else if (l.type === "fill" && sourceLayer === "building")
				setPaint(l, "fill-color", accent.building);
			else if (roadClass === "casing")
				setPaint(l, "line-color", accent.roadCasing);
			else if (roadClass === "minor")
				setPaint(l, "line-color", accent.roadMinor);
			else if (roadClass === "major")
				setPaint(l, "line-color", accent.roadMajor);
		}
	}
	const src = vectorSourceId(nextStyle);
	// An empty colour would fail MapLibre's style validation, so buildings wait
	// for the first features push (which always carries the theme colour).
	if (features.buildings && features.buildingColor && src) {
		// Insert beneath the first label (symbol) layer so labels stay on top.
		const firstSymbol = nextStyle.layers.find((l) => l.type === "symbol");
		const buildings = {
			id: "femto-3d-buildings",
			source: src,
			"source-layer": "building",
			type: "fill-extrusion",
			minzoom: 14,
			filter: ["!=", ["get", "hide_3d"], true],
			paint: {
				"fill-extrusion-color": features.buildingColor,
				"fill-extrusion-height": [
					"interpolate",
					["linear"],
					["zoom"],
					14,
					0,
					16,
					["get", "render_height"],
				],
				"fill-extrusion-base": [
					"interpolate",
					["linear"],
					["zoom"],
					14,
					0,
					16,
					["get", "render_min_height"],
				],
				"fill-extrusion-opacity": BUILDING_EXTRUSION_OPACITY,
			},
		} as LayerSpecification;
		if (firstSymbol)
			nextStyle.layers.splice(
				nextStyle.layers.indexOf(firstSymbol),
				0,
				buildings,
			);
		else nextStyle.layers.push(buildings);
	}
	if (features.terrain) {
		nextStyle.sources.terrainSource = {
			type: "raster-dem",
			url: MAPTERHORN_DEM_URL,
		};
		nextStyle.terrain = { source: "terrainSource", exaggeration: 1.0 };
	}
	return nextStyle;
}
