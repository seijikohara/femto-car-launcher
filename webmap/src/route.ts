// The route line of the OSM page (#389 phase 3): the host's route payload, the
// GeoJSON source and the casing + main line layers that draw it, and the live
// update of a loaded style. Kept free of the MapLibre runtime and the DOM, like
// style.ts, so it is unit-testable (route.test.ts). A style swap re-adds the
// line through injectFeatures (style.ts); this module only describes it.
// The route never moves the camera: the follow camera owns it (follow-camera.ts).
import type {
    ExpressionSpecification,
    GeoJSONSourceSpecification,
    LayerSpecification,
    LineLayerSpecification,
} from "maplibre-gl";

export const ROUTE_SOURCE_ID = "femto-route";
export const ROUTE_CASING_LAYER_ID = "femto-route-casing";
export const ROUTE_LINE_LAYER_ID = "femto-route-line";

// Bottom to top: the casing is the wider line under the main one.
export const ROUTE_LAYER_IDS = [ROUTE_CASING_LAYER_ID, ROUTE_LINE_LAYER_ID] as const;

// A decoded route the page can draw: GeoJSON [lon, lat] vertices in travel
// order, at least two, plus the host's colours.
export interface RouteLine {
    coordinates: [number, number][];
    lineColor: string;
    casingColor: string;
}

// The host sends theme colours as CSS hex (toCssHex in MapScheme.kt). Anything
// else would make MapLibre's layer validation emit a map `error` — noise the
// load-outcome watchdogs would read — so it is refused here instead.
const HEX_COLOR = /^#(?:[0-9a-f]{3,4}|[0-9a-f]{6}|[0-9a-f]{8})$/i;

function isLatitude(value: unknown): value is number {
    return typeof value === "number" && Number.isFinite(value) && Math.abs(value) <= 90;
}

function isLongitude(value: unknown): value is number {
    return typeof value === "number" && Number.isFinite(value) && Math.abs(value) <= 180;
}

// Decode setRoute's arguments: a flat [lat, lon, lat, lon, …] array (the
// order updateCamera takes its fix in) and two hex colours. A payload the page
// cannot draw — not an array, an odd length, fewer than two points, a
// non-finite or out-of-range coordinate, a colour that is not hex — yields
// null, which clears the route: a partial or guessed line would be worse than
// none.
export function decodeRoute(
    latLons: unknown,
    lineColor: unknown,
    casingColor: unknown,
): RouteLine | null {
    if (!Array.isArray(latLons) || latLons.length < 4 || latLons.length % 2 !== 0) return null;
    if (typeof lineColor !== "string" || !HEX_COLOR.test(lineColor)) return null;
    if (typeof casingColor !== "string" || !HEX_COLOR.test(casingColor)) return null;
    const values: unknown[] = latLons;
    const valid = values.every((value, i) =>
        i % 2 === 0 ? isLatitude(value) : isLongitude(value),
    );
    if (!valid) return null;
    const numbers = values.filter((value): value is number => typeof value === "number");
    const coordinates = Array.from({ length: numbers.length / 2 }, (_, i): [number, number] => [
        numbers[2 * i + 1],
        numbers[2 * i],
    ]);
    return { coordinates, lineColor, casingColor };
}

export function routeSource(route: RouteLine): GeoJSONSourceSpecification {
    return {
        type: "geojson",
        data: {
            type: "Feature",
            properties: {},
            geometry: { type: "LineString", coordinates: route.coordinates },
        },
    };
}

// Line width in px at z11, z14 and z17, interpolated exponentially like the
// base styles' road widths, so the route keeps its weight against the roads
// through a zoom change. Tuned for the dashboard's z11–z17 range: thick enough
// at z11 to follow at a glance across the minor roads, and at z16–z17 about a
// major road's width, so the line reads without burying the road under it.
function zoomWidth(z11: number, z14: number, z17: number): ExpressionSpecification {
    return ["interpolate", ["exponential", 1.4], ["zoom"], 11, z11, 14, z14, 17, z17];
}

// The casing and the main line, bottom to top; the casing shows about 1.5–2.5 px
// either side of the main line. Round caps and joins keep the sharp turns of a
// road-snapped polyline from spiking at high zoom.
export function routeLayers(route: RouteLine): [LineLayerSpecification, LineLayerSpecification] {
    const layout: LineLayerSpecification["layout"] = { "line-cap": "round", "line-join": "round" };
    return [
        {
            id: ROUTE_CASING_LAYER_ID,
            type: "line",
            source: ROUTE_SOURCE_ID,
            layout,
            paint: { "line-width": zoomWidth(7, 10, 15), "line-color": route.casingColor },
        },
        {
            id: ROUTE_LINE_LAYER_ID,
            type: "line",
            source: ROUTE_SOURCE_ID,
            layout,
            paint: { "line-width": zoomWidth(4, 6, 10), "line-color": route.lineColor },
        },
    ];
}

// The loaded-map surface showRoute drives; maplibregl.Map satisfies it
// structurally (the same pattern as GlFollowMap in follow-camera.ts).
export interface RouteMap {
    getSource(id: string): unknown;
    addSource(id: string, source: GeoJSONSourceSpecification): unknown;
    removeSource(id: string): unknown;
    getLayer(id: string): { type: string } | undefined;
    addLayer(layer: LayerSpecification, beforeId?: string): unknown;
    removeLayer(id: string): unknown;
    getLayersOrder(): string[];
}

// Where the route's layers go in a style's layer list: directly after its last
// non-symbol layer, so the line sits above every road, bridge and building
// fill and below the trailing block of labels; on top when the style has no
// label after its last drawn layer. Not beneath the first symbol layer, which
// the 3D buildings use: the Dark Matter base (dark.json, standard-dark.json)
// opens with water labels, so that position would put the route under every
// road and building of the dark map.
export function routeInsertIndex(layers: readonly { type: string }[]): number {
    // A reduce, not findLastIndex: that is ES2023, past the tsconfig lib.
    return layers.reduce((last, l, i) => (l.type === "symbol" ? last : i), -1) + 1;
}

// Replace the route on a loaded style: remove the current line (layers before
// their source, which MapLibre requires), then, for a route, add it at
// routeInsertIndex — the place injectFeatures gives it on a style swap.
// Removal is guarded by presence, since MapLibre reports removing a missing
// layer as a map error. Throws, before adding anything, while the style is
// still loading; the caller then relies on the next style push's
// injectFeatures, which reads the same state (see syncRoute in osm.ts).
export function showRoute(map: RouteMap, route: RouteLine | null): void {
    for (const id of ROUTE_LAYER_IDS) {
        if (map.getLayer(id)) map.removeLayer(id);
    }
    if (map.getSource(ROUTE_SOURCE_ID)) map.removeSource(ROUTE_SOURCE_ID);
    if (!route) return;
    const order = map.getLayersOrder();
    const types = order.map((id) => ({ type: map.getLayer(id)?.type ?? "" }));
    // Undefined (the index past the end) adds on top.
    const beforeId: string | undefined = order[routeInsertIndex(types)];
    map.addSource(ROUTE_SOURCE_ID, routeSource(route));
    for (const layer of routeLayers(route)) map.addLayer(layer, beforeId);
}
