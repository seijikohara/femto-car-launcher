// Pure style-transformation logic, kept free of MapLibre runtime and DOM state
// so it is unit-testable (see style.test.ts); main.ts owns the page wiring.
import type { LayerSpecification, StyleSpecification } from "maplibre-gl";
// Recolour rule data — layer group names and the marker-drop cap live here so
// accent blending and the chevron geometry share one authoritative source.
import recolorData from "./map-recolor-data.json";
import {
    ROUTE_ALTERNATIVES_LAYER_ID,
    ROUTE_ALTERNATIVES_SOURCE_ID,
    ROUTE_LAYER_IDS,
    ROUTE_SOURCE_ID,
    type RouteAlternatives,
    type RouteLine,
    routeAlternativesLayer,
    routeAlternativesSource,
    routeInsertIndex,
    routeLayers,
    routeSource,
} from "./route";

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
// [buildingColor] is the theme-tracked 3D extrusion colour, applied for every
// scheme whose style does not name its own (see [STYLE_BUILDING_COLOR_KEY]), so
// the buildings stay subdued on any base style.
export interface MapFeatures {
    buildings: boolean;
    terrain: boolean;
    accent: AccentColors | null;
    buildingColor: string;
    // The raster-DEM TileJSON to inject for terrain; the host supplies its
    // build-time endpoint, and the default below stands in for tests and a
    // page loaded outside the launcher.
    terrainUrl?: string;
    // The host's route line (route.ts), or none.
    route?: RouteLine | null;
    // The other routes on the host's choice card (route.ts), or none.
    routeAlternatives?: RouteAlternatives | null;
}

// A style's own 3D building colour, in its metadata: the Standard scheme's fixed
// palette names one, so its buildings match its ground instead of the theme.
export const STYLE_BUILDING_COLOR_KEY = "femto:building-color";

function styleBuildingColorOrNull(style: StyleSpecification): string | null {
    const metadata: unknown = style.metadata;
    if (typeof metadata !== "object" || metadata === null) return null;
    const value: unknown = Reflect.get(metadata, STYLE_BUILDING_COLOR_KEY);
    return typeof value === "string" && value !== "" ? value : null;
}

// Subdued, see-through buildings: the extrusion must never outshout the roads
// (the car-nav priority), so it renders semi-transparent in its theme colour.
export const BUILDING_EXTRUSION_OPACITY = 0.5;

// DEM = Mapterhorn (free, no key); the provider's required credit is shown by
// the host's Compose attribution overlay when terrain is active. The host's
// MAP_TERRAIN_TILEJSON_URL build field is the shipped value; this mirrors its
// default.
export const DEFAULT_TERRAIN_TILEJSON_URL = "https://tiles.mapterhorn.com/tilejson.json";

// The id of the raster-DEM source injectFeatures adds while terrain is on.
// Its tiles come from the DEM host, not the tile host, so the no-tile
// watchdog (load-outcome.ts) must not count them.
export const TERRAIN_SOURCE_ID = "terrainSource";

// The sources the page injects whose tiles say nothing about the tile host:
// the terrain DEM comes from its own host, and the route lines' tiles are cut
// on the device from the host's GeoJSON. The no-tile watchdog
// (load-outcome.ts) ignores them, or a page without map data would report
// its success signal from any of them.
export const NON_TILE_HOST_SOURCE_IDS: readonly string[] = [
    TERRAIN_SOURCE_ID,
    ROUTE_SOURCE_ID,
    ROUTE_ALTERNATIVES_SOURCE_ID,
];

// The origin the bundled styles and the hosted style URLs are written against.
// The launcher may serve the same layout from another host (a self-hosted
// mirror, or the fallback rotation after a failed load); rewriteHost re-points
// every request under this origin at the configured one.
export const UPSTREAM_TILE_HOST = "https://tiles.openfreemap.org";

// Re-point a request URL at the configured tile host: a URL under [from] is
// rewritten to [to]; anything else passes through. Identity when the two hosts
// agree, so the default configuration adds no work per request. The match stops
// at an origin boundary — a bare prefix test would also rewrite a lookalike
// origin (tiles.openfreemap.org.example.test), and once a user mirror serves its
// own style JSON the request URLs are no longer ones we authored.
export function rewriteHost(url: string, from: string, to: string): string {
    if (from === to || !url.startsWith(from)) return url;
    const rest = url.slice(from.length);
    return rest === "" || rest.startsWith("/") || rest.startsWith("?") ? to + rest : url;
}

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
// marker.
export function markerDrop(markerPos: number, bottomSafe: number): number {
    const maxDrop = Math.max(0, Math.min(MAX_MARKER_DROP, 0.5 - (bottomSafe || 0)));
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
// width, to clear what covers the map from one side edge (the floating cards, a
// side dock). Half the safe zone lands the marker mid-way across the exposed
// strip; capped at 0.35 so a wide reserve never pushes it past the opposite
// quarter. Independent of markerPos; the caller applies the direction (LEFT for
// a right reserve, RIGHT for a left one), and with a reserve on each side the two
// shifts add up to the middle of what is left between them. Reused for both
// [markerPadRight] and [markerPadLeft].
export function markerXFraction(safe: number): number {
    // Clamp both ends, mirroring the Kotlin coerceIn(0f, 0.35f): a negative safe
    // fraction (a layout/rounding glitch upstream) must not shift the marker past
    // centre the wrong way, only toward the exposed side of the map.
    return Math.max(0, Math.min(0.35, (safe || 0) / 2));
}

// Right camera padding (px) that shifts the focal point left so the location sits
// left of centre, clear of the right reserve; the 2x compensates the
// half-of-padding geometry.
export function markerPadRight(rightSafe: number, containerWidth: number): number {
    return 2 * markerXFraction(rightSafe) * containerWidth;
}

// Left camera padding (px) that shifts the focal point right so the location sits
// right of centre, clear of the left reserve; the horizontal mirror of
// [markerPadRight]. Both paddings apply together when each side has a reserve.
export function markerPadLeft(leftSafe: number, containerWidth: number): number {
    return 2 * markerXFraction(leftSafe) * containerWidth;
}

// The host's self-marker layout inputs, pushed with every fix (updateCamera):
// markerPos, and the bottom / right / left safe-area fractions.
export interface MarkerLayout {
    markerPos: number;
    bottomSafe: number;
    rightSafe: number;
    leftSafe: number;
}

// Where the screen-pinned chevron sits, as fractions of the map from its
// centre: x of the width (positive = right), y of the height (positive =
// down). The camera brings the fix to this spot.
export interface MarkerSpot {
    x: number;
    y: number;
}

// The OSM placement: mid-way across the strip the side reserves leave
// (markerXFraction, away from each reserved side) and dropped per
// markerPos (markerDrop). MapLibre's camera padding moves the perspective's
// vanishing point along with it, so the road ahead runs straight up through
// the chevron wherever it sits.
export function markerSpot(layout: MarkerLayout): MarkerSpot {
    return {
        x: markerXFraction(layout.leftSafe) - markerXFraction(layout.rightSafe),
        y: markerDrop(layout.markerPos, layout.bottomSafe),
    };
}

// The first vector source id in a style (the OpenMapTiles source), so 3D
// buildings work across positron / the bundled dark.json without hard-coding.
export function vectorSourceId(style: StyleSpecification): string | undefined {
    const sources = style.sources ?? {};
    return Object.keys(sources).find((id) => sources[id].type === "vector");
}

// The vector-tile layer a style layer draws from; a background layer has none.
function sourceLayerOf(layer: LayerSpecification): string | undefined {
    return "source-layer" in layer ? layer["source-layer"] : undefined;
}

// Road classification by layer id, shared between the bundled light/dark styles.
// "subtle" (the low-zoom motorway representation) classifies as minor: in the dark
// base its colour is identical to highway_minor, and as "major" it would render a
// casing-less dark hairline on the dark background. Railways, piers, oneway
// arrows, and aeroways fall outside the highway_/tunnel_ prefixes and keep their
// base colours.
export function roadClassOrNull(layer: LayerSpecification): "casing" | "minor" | "major" | null {
    const sourceLayer = sourceLayerOf(layer);
    if (layer.type !== "line" || sourceLayer !== "transportation") return null;
    if (!layer.id.startsWith("highway_") && !layer.id.startsWith("tunnel_")) return null;
    if (layer.id.includes("casing")) return "casing";
    if (recolorData.roadMinorKeywords.some((k) => layer.id.includes(k))) return "minor";
    return "major";
}

// Style-spec paint maps are per-layer-type unions; this helper funnels the few
// dynamic recolour writes through one narrow cast.
function setPaint(layer: LayerSpecification, key: string, value: unknown): void {
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
    const injectedLayerIds: readonly string[] = [
        "femto-3d-buildings",
        ROUTE_ALTERNATIVES_LAYER_ID,
        ...ROUTE_LAYER_IDS,
    ];
    nextStyle.layers = nextStyle.layers.filter((l) => !injectedLayerIds.includes(l.id));
    delete nextStyle.sources[ROUTE_SOURCE_ID];
    delete nextStyle.sources[ROUTE_ALTERNATIVES_SOURCE_ID];
    // Only strip terrain if WE injected it (terrainSource present), so a base
    // style that ever ships its own terrain is left intact.
    if (nextStyle.sources[TERRAIN_SOURCE_ID]) {
        delete nextStyle.sources[TERRAIN_SOURCE_ID];
        delete nextStyle.terrain;
    }
    // ACCENT scheme: recolour background / water / land / building fills, the
    // transportation lines, and label text with the accent palette. Roads keep
    // their base widths, so the motorway/major/minor hierarchy survives.
    const accent = features.accent;
    if (accent) {
        for (const l of nextStyle.layers) {
            const sourceLayer = sourceLayerOf(l);
            const roadClass = roadClassOrNull(l);
            if (l.type === "background") setPaint(l, "background-color", accent.background);
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
            else if (roadClass === "casing") setPaint(l, "line-color", accent.roadCasing);
            else if (roadClass === "minor") setPaint(l, "line-color", accent.roadMinor);
            else if (roadClass === "major") setPaint(l, "line-color", accent.roadMajor);
            else if (l.type === "symbol") {
                // Text-less symbol layers (oneway arrows) ignore the text-* keys.
                // The halo width is only seeded where the base omits it; existing
                // widths are the base's tuning, kept as-is.
                setPaint(l, "text-color", accent.label);
                setPaint(l, "text-halo-color", accent.background);
                const paint = (l as { paint?: Record<string, unknown> }).paint;
                if (paint?.["text-halo-width"] === undefined) setPaint(l, "text-halo-width", 1);
            }
        }
    }
    const src = vectorSourceId(nextStyle);
    // An empty colour would fail MapLibre's style validation, so buildings wait
    // for the first features push (which always carries the theme colour).
    const buildingColor = styleBuildingColorOrNull(nextStyle) ?? features.buildingColor;
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
                "fill-extrusion-color": buildingColor,
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
            nextStyle.layers.splice(nextStyle.layers.indexOf(firstSymbol), 0, buildings);
        else nextStyle.layers.push(buildings);
    }
    // After the buildings, so the line stays visible where it passes behind an
    // extrusion; routeInsertIndex says why it does not share their position.
    // The alternatives go first: being no symbol layer, they move the route's
    // insert position past themselves, so the route is drawn over them.
    const alternatives = features.routeAlternatives;
    if (alternatives) {
        nextStyle.sources[ROUTE_ALTERNATIVES_SOURCE_ID] = routeAlternativesSource(alternatives);
        nextStyle.layers.splice(
            routeInsertIndex(nextStyle.layers),
            0,
            routeAlternativesLayer(alternatives),
        );
    }
    const route = features.route;
    if (route) {
        nextStyle.sources[ROUTE_SOURCE_ID] = routeSource(route);
        nextStyle.layers.splice(routeInsertIndex(nextStyle.layers), 0, ...routeLayers(route));
    }
    if (features.terrain) {
        nextStyle.sources[TERRAIN_SOURCE_ID] = {
            type: "raster-dem",
            url: features.terrainUrl || DEFAULT_TERRAIN_TILEJSON_URL,
        };
        nextStyle.terrain = { source: TERRAIN_SOURCE_ID, exaggeration: 1.0 };
    }
    return nextStyle;
}
