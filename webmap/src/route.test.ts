import type { GeoJSONSourceSpecification, LayerSpecification } from "maplibre-gl";
import { describe, expect, it } from "vite-plus/test";
import {
    decodeRoute,
    decodeRouteAlternatives,
    ROUTE_ALTERNATIVES_LAYER_ID,
    ROUTE_ALTERNATIVES_SOURCE_ID,
    ROUTE_CASING_LAYER_ID,
    ROUTE_LINE_LAYER_ID,
    ROUTE_SOURCE_ID,
    type RouteAlternatives,
    type RouteLine,
    type RouteMap,
    routeInsertIndex,
    showRoute,
    showRouteAlternatives,
} from "./route";

const LINE = "#3367D6";
const CASING = "#FFFFFF";

// Two points near Tokyo Station, as the host sends them: lat, lon pairs.
const PAYLOAD = [35.681236, 139.767125, 35.6895, 139.6917];

function route(): RouteLine {
    const decoded = decodeRoute(PAYLOAD, LINE, CASING);
    if (decoded === null) throw new Error("the fixture payload must decode");
    return decoded;
}

describe("decodeRoute", () => {
    it("turns the flat lat, lon payload into GeoJSON lon, lat vertices", () => {
        expect(decodeRoute(PAYLOAD, LINE, CASING)).toEqual({
            coordinates: [
                [139.767125, 35.681236],
                [139.6917, 35.6895],
            ],
            lineColor: LINE,
            casingColor: CASING,
        });
    });

    it("rejects fewer than two points", () => {
        expect(decodeRoute([], LINE, CASING)).toBeNull();
        expect(decodeRoute([35.68, 139.76], LINE, CASING)).toBeNull();
    });

    it("rejects an odd-length payload", () => {
        expect(decodeRoute([...PAYLOAD, 35.7], LINE, CASING)).toBeNull();
    });

    it("rejects a non-finite coordinate", () => {
        for (const bad of [Number.NaN, Number.POSITIVE_INFINITY, Number.NEGATIVE_INFINITY]) {
            expect(decodeRoute([35.68, bad, 35.69, 139.69], LINE, CASING)).toBeNull();
        }
    });

    it("rejects a coordinate out of range", () => {
        expect(decodeRoute([91, 139.76, 35.69, 139.69], LINE, CASING)).toBeNull();
        expect(decodeRoute([35.68, 181, 35.69, 139.69], LINE, CASING)).toBeNull();
    });

    it("rejects anything but an array of numbers", () => {
        expect(decodeRoute("35.68,139.76,35.69,139.69", LINE, CASING)).toBeNull();
        expect(decodeRoute(null, LINE, CASING)).toBeNull();
        expect(decodeRoute(["35.68", 139.76, 35.69, 139.69], LINE, CASING)).toBeNull();
    });

    it("rejects a colour that is not CSS hex", () => {
        expect(decodeRoute(PAYLOAD, "", CASING)).toBeNull();
        expect(decodeRoute(PAYLOAD, LINE, "red")).toBeNull();
        expect(decodeRoute(PAYLOAD, "#12345", CASING)).toBeNull();
    });
});

// A loaded style the way showRoute sees it: an ordered layer list and the
// sources, recording nothing beyond the resulting state.
function fakeMap(initialLayers: LayerSpecification[]): RouteMap & {
    layers: LayerSpecification[];
    sources: Record<string, GeoJSONSourceSpecification>;
} {
    const fake = {
        layers: [...initialLayers],
        sources: {} as Record<string, GeoJSONSourceSpecification>,
        getSource: (id: string): unknown => fake.sources[id],
        addSource: (id: string, source: GeoJSONSourceSpecification): void => {
            if (fake.sources[id]) throw new Error(`source ${id} already exists`);
            fake.sources[id] = source;
        },
        removeSource: (id: string): void => {
            if (fake.layers.some((l) => "source" in l && l.source === id))
                throw new Error(`source ${id} is in use`);
            delete fake.sources[id];
        },
        getLayer: (id: string): LayerSpecification | undefined =>
            fake.layers.find((l) => l.id === id),
        addLayer: (layer: LayerSpecification, beforeId?: string): void => {
            if (fake.layers.some((l) => l.id === layer.id))
                throw new Error(`layer ${layer.id} already exists`);
            const at =
                beforeId === undefined ? -1 : fake.layers.findIndex((l) => l.id === beforeId);
            if (at < 0) fake.layers.push(layer);
            else fake.layers.splice(at, 0, layer);
        },
        removeLayer: (id: string): void => {
            fake.layers = fake.layers.filter((l) => l.id !== id);
        },
        getLayersOrder: (): string[] => fake.layers.map((l) => l.id),
    };
    return fake;
}

// Shaped like the Dark Matter base: a water label early on, under the roads
// and buildings, then the trailing block of labels.
const LABELED: LayerSpecification[] = [
    { id: "bg", type: "background" },
    { id: "water_name", type: "symbol", source: "openmaptiles", "source-layer": "water_name" },
    { id: "building", type: "fill", source: "openmaptiles", "source-layer": "building" },
    { id: "road", type: "line", source: "openmaptiles", "source-layer": "transportation" },
    { id: "road-label", type: "symbol", source: "openmaptiles", "source-layer": "transportation" },
    { id: "place-label", type: "symbol", source: "openmaptiles", "source-layer": "place" },
];

describe("routeInsertIndex", () => {
    it("points right after the last non-symbol layer", () => {
        expect(routeInsertIndex(LABELED)).toBe(4);
    });

    it("points past the end when no label follows the last drawn layer", () => {
        const unlabeled = LABELED.filter((l) => l.type !== "symbol");
        expect(routeInsertIndex(unlabeled)).toBe(unlabeled.length);
        expect(routeInsertIndex([])).toBe(0);
    });
});

describe("showRoute", () => {
    it("adds the casing under the line, above every road and building, below the trailing labels", () => {
        const map = fakeMap(LABELED);
        showRoute(map, route());
        expect(map.getLayersOrder()).toEqual([
            "bg",
            "water_name",
            "building",
            "road",
            ROUTE_CASING_LAYER_ID,
            ROUTE_LINE_LAYER_ID,
            "road-label",
            "place-label",
        ]);
        expect(map.sources[ROUTE_SOURCE_ID]).toBeDefined();
    });

    it("adds the route on top when the style has no label layer", () => {
        const map = fakeMap(LABELED.filter((l) => l.type !== "symbol"));
        showRoute(map, route());
        expect(map.getLayersOrder().slice(-2)).toEqual([
            ROUTE_CASING_LAYER_ID,
            ROUTE_LINE_LAYER_ID,
        ]);
    });

    it("paints the host's colours", () => {
        const map = fakeMap(LABELED);
        showRoute(map, route());
        expect(map.getLayer(ROUTE_LINE_LAYER_ID)).toMatchObject({ paint: { "line-color": LINE } });
        expect(map.getLayer(ROUTE_CASING_LAYER_ID)).toMatchObject({
            paint: { "line-color": CASING },
        });
    });

    it("replaces a shown route instead of adding a second one", () => {
        const map = fakeMap(LABELED);
        showRoute(map, route());
        const next = decodeRoute([35.0, 135.0, 35.1, 135.1], LINE, CASING);
        showRoute(map, next);
        expect(map.getLayersOrder().filter((id) => id.startsWith("femto-route"))).toHaveLength(2);
        expect(map.sources[ROUTE_SOURCE_ID]).toMatchObject({
            data: { geometry: { coordinates: next?.coordinates } },
        });
    });

    it("clearing removes both layers and the source", () => {
        const map = fakeMap(LABELED);
        showRoute(map, route());
        showRoute(map, null);
        expect(map.getLayersOrder()).toEqual(LABELED.map((l) => l.id));
        expect(map.sources[ROUTE_SOURCE_ID]).toBeUndefined();
    });

    it("adds nothing while the style is still loading", () => {
        // MapLibre refuses new sources until the style has loaded.
        const map = fakeMap(LABELED);
        map.addSource = () => {
            throw new Error("Style is not done loading.");
        };
        expect(() => showRoute(map, route())).toThrow("Style is not done loading.");
        expect(map.getLayersOrder()).toEqual(LABELED.map((l) => l.id));
        expect(map.sources[ROUTE_SOURCE_ID]).toBeUndefined();
    });

    it("clearing a map without a route touches nothing", () => {
        const map = fakeMap(LABELED);
        showRoute(map, null);
        expect(map.getLayersOrder()).toEqual(LABELED.map((l) => l.id));
    });
});

const MUTED = "#79747E";

// Two routes, as the host sends them: one flat lat, lon array each.
const ALTERNATIVES_PAYLOAD = [
    [35.681236, 139.767125, 35.6895, 139.6917],
    [35.681236, 139.767125, 35.67, 139.7, 35.6895, 139.6917],
];

function alternatives(): RouteAlternatives {
    const decoded = decodeRouteAlternatives(ALTERNATIVES_PAYLOAD, MUTED);
    if (decoded === null) throw new Error("the fixture payload must decode");
    return decoded;
}

describe("decodeRouteAlternatives", () => {
    it("turns each flat lat, lon array into a GeoJSON line", () => {
        expect(decodeRouteAlternatives(ALTERNATIVES_PAYLOAD, MUTED)).toEqual({
            lines: [
                [
                    [139.767125, 35.681236],
                    [139.6917, 35.6895],
                ],
                [
                    [139.767125, 35.681236],
                    [139.7, 35.67],
                    [139.6917, 35.6895],
                ],
            ],
            color: MUTED,
        });
    });

    it("rejects an empty list", () => {
        expect(decodeRouteAlternatives([], MUTED)).toBeNull();
    });

    it("rejects the whole payload when one route cannot be drawn", () => {
        expect(
            decodeRouteAlternatives([ALTERNATIVES_PAYLOAD[0], [35.68, 139.76]], MUTED),
        ).toBeNull();
        expect(
            decodeRouteAlternatives([ALTERNATIVES_PAYLOAD[0], [91, 139.76, 35.69, 139.69]], MUTED),
        ).toBeNull();
    });

    it("rejects anything but a list of arrays", () => {
        expect(decodeRouteAlternatives(ALTERNATIVES_PAYLOAD[0], MUTED)).toBeNull();
        expect(decodeRouteAlternatives(null, MUTED)).toBeNull();
    });

    it("rejects a colour that is not CSS hex", () => {
        expect(decodeRouteAlternatives(ALTERNATIVES_PAYLOAD, "grey")).toBeNull();
    });
});

describe("showRouteAlternatives", () => {
    it("draws the alternatives under the route's casing", () => {
        const map = fakeMap(LABELED);
        showRoute(map, route());
        showRouteAlternatives(map, alternatives());
        expect(map.getLayersOrder()).toEqual([
            "bg",
            "water_name",
            "building",
            "road",
            ROUTE_ALTERNATIVES_LAYER_ID,
            ROUTE_CASING_LAYER_ID,
            ROUTE_LINE_LAYER_ID,
            "road-label",
            "place-label",
        ]);
        expect(map.sources[ROUTE_ALTERNATIVES_SOURCE_ID]).toMatchObject({
            data: { geometry: { type: "MultiLineString" } },
        });
    });

    it("keeps the route above the alternatives when the route comes after them", () => {
        const map = fakeMap(LABELED);
        showRouteAlternatives(map, alternatives());
        showRoute(map, route());
        const order = map.getLayersOrder();
        expect(order.indexOf(ROUTE_ALTERNATIVES_LAYER_ID)).toBeLessThan(
            order.indexOf(ROUTE_CASING_LAYER_ID),
        );
        expect(order.indexOf(ROUTE_LINE_LAYER_ID)).toBe(order.indexOf("road-label") - 1);
    });

    it("paints the host's colour", () => {
        const map = fakeMap(LABELED);
        showRouteAlternatives(map, alternatives());
        expect(map.getLayer(ROUTE_ALTERNATIVES_LAYER_ID)).toMatchObject({
            paint: { "line-color": MUTED },
        });
    });

    it("clearing removes the layer and the source and leaves the route", () => {
        const map = fakeMap(LABELED);
        showRoute(map, route());
        showRouteAlternatives(map, alternatives());
        showRouteAlternatives(map, null);
        expect(map.getLayer(ROUTE_ALTERNATIVES_LAYER_ID)).toBeUndefined();
        expect(map.sources[ROUTE_ALTERNATIVES_SOURCE_ID]).toBeUndefined();
        expect(map.getLayer(ROUTE_LINE_LAYER_ID)).toBeDefined();
    });

    it("replaces shown alternatives instead of adding a second set", () => {
        const map = fakeMap(LABELED);
        showRouteAlternatives(map, alternatives());
        showRouteAlternatives(map, alternatives());
        expect(
            map.getLayersOrder().filter((id) => id === ROUTE_ALTERNATIVES_LAYER_ID),
        ).toHaveLength(1);
    });
});
