// Pure style-transformation logic, kept free of MapLibre runtime and DOM state
// so it is unit-testable (see style.test.ts); main.ts owns the page wiring.
import type { LayerSpecification, StyleSpecification } from "maplibre-gl";
// Recolour rule data — layer group names and the marker-drop cap live here so
// accent blending and the chevron geometry share one authoritative source.
import recolorData from "./map-recolor-data.json";

export interface AccentColors {
	background: string;
	water: string;
	land: string;
	roadMajor: string;
	roadMinor: string;
	roadCasing: string;
	building: string;
	// Label text colour; the halo reuses background. The bundled bases' label
	// colours are tuned to their own backgrounds and go illegible on the
	// Material surface, so ACCENT recolours them like every other layer group.
	label: string;
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

const ACCENT_LAND = recolorData.accentLandLayers;

// The lowest the self-marker drops below centre, as a fraction of map height,
// at markerPos = 100. From the shared map-recolor-data.json.
export const MAX_MARKER_DROP = recolorData.maxMarkerDrop;

// Clamp a host-pushed markerPos into the valid 0..100 range (treating a missing
// value as 0). The single SSOT for the clamp shared by markerPadTop and the
// DOM-chevron `top` placement in main.ts.
export function clampMarkerPos(markerPos: number): number {
	return Math.max(0, Math.min(100, markerPos || 0));
}

// Marker drop below centre as a fraction of map height. Capped at MAX_MARKER_DROP
// and additionally clamped so the chevron stays above the bottom speed overlay:
// the lowest the centre may sit is 0.5 - bottomSafe, where bottomSafe is the
// overlay's measured footprint (plus marker clearance) the host pushes. A short
// map pane or a tall overlay shrinks the usable range instead of burying the
// marker. Mirrors markerDropFraction in MapSnapshot.kt — keep in sync.
export function markerDrop(markerPos: number, bottomSafe: number): number {
	const maxDrop = Math.max(
		0,
		Math.min(MAX_MARKER_DROP, 0.5 - (bottomSafe || 0)),
	);
	return (clampMarkerPos(markerPos) / 100) * maxDrop;
}

// Top camera padding (px) that places the centred location at the height the
// marker sits at. padTop shifts the focal point down so the location (and its
// marker) sits lower in the frame; the 2x compensates the half-of-padding
// geometry so the location lands exactly under the chevron.
export function markerPadTop(
	markerPos: number,
	bottomSafe: number,
	containerHeight: number,
): number {
	return 2 * markerDrop(markerPos, bottomSafe) * containerHeight;
}

// Magnitude of the marker's horizontal shift from centre as a fraction of map
// width, to clear the floating cards on one side. Half the safe zone lands the
// marker mid-way across the exposed strip; capped at 0.35 so a wide card set never
// pushes it past the opposite quarter. Independent of markerPos; the caller applies
// the direction (LEFT for a right-card reserve, RIGHT for a left-card reserve).
// Reused for both [markerPadRight] and [markerPadLeft].
export function markerXFraction(safe: number): number {
	// Clamp both ends, mirroring the Kotlin coerceIn(0f, 0.35f): a negative safe
	// fraction (a layout/rounding glitch upstream) must not shift the marker past
	// centre the wrong way, only toward the exposed side of the map.
	return Math.max(0, Math.min(0.35, (safe || 0) / 2));
}

// Right camera padding (px) that shifts the focal point left so the location sits
// left of centre, clear of the right cards; the 2x compensates the half-of-padding
// geometry.
export function markerPadRight(
	rightSafe: number,
	containerWidth: number,
): number {
	return 2 * markerXFraction(rightSafe) * containerWidth;
}

// Left camera padding (px) that shifts the focal point right so the location sits
// right of centre, clear of the left cards; the horizontal mirror of
// [markerPadRight] for a left-side (driver's-left) card reserve.
export function markerPadLeft(
	leftSafe: number,
	containerWidth: number,
): number {
	return 2 * markerXFraction(leftSafe) * containerWidth;
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
	if (recolorData.roadMinorKeywords.some((k) => layer.id.includes(k)))
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
	// ACCENT scheme: recolour background / water / land / building fills, the
	// transportation lines, and label text with the accent palette. Roads keep
	// their base widths, so the motorway/major/minor hierarchy survives.
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
			else if (l.type === "symbol") {
				// Text-less symbol layers (oneway arrows) ignore the text-* keys.
				// The halo width is only seeded where the base omits it; existing
				// widths are the base's tuning, kept as-is.
				setPaint(l, "text-color", accent.label);
				setPaint(l, "text-halo-color", accent.background);
				const paint = (l as { paint?: Record<string, unknown> }).paint;
				if (paint?.["text-halo-width"] === undefined)
					setPaint(l, "text-halo-width", 1);
			}
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
