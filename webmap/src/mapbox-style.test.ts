import { describe, expect, it } from "vite-plus/test";
import { mapboxStyleUrl, styleApplyMode, TRAFFIC_LAYER_ID, trafficLayerSpec } from "./mapbox-style";

describe("mapboxStyleUrl", () => {
    it("maps known style ids to mapbox:// urls", () => {
        expect(mapboxStyleUrl("standard")).toBe("mapbox://styles/mapbox/standard");
        expect(mapboxStyleUrl("satellite-streets-v12")).toBe(
            "mapbox://styles/mapbox/satellite-streets-v12",
        );
        expect(mapboxStyleUrl("streets-v12")).toBe("mapbox://styles/mapbox/streets-v12");
    });
    it("falls back to standard for an unknown id", () => {
        expect(mapboxStyleUrl("nope")).toBe("mapbox://styles/mapbox/standard");
    });
});

describe("styleApplyMode", () => {
    const standard = mapboxStyleUrl("standard");
    it("applies now when the URL is unchanged and the style is loaded", () => {
        expect(styleApplyMode(standard, standard, true)).toBe("apply-now");
    });
    it("awaits load when the URL is unchanged but the style is not loaded", () => {
        expect(styleApplyMode(standard, standard, false)).toBe("await-load");
    });
    it("awaits load on a changed URL regardless of loaded state", () => {
        const streets = mapboxStyleUrl("streets-v12");
        expect(styleApplyMode(streets, standard, true)).toBe("await-load");
        expect(styleApplyMode(streets, standard, false)).toBe("await-load");
    });
    it("awaits load when no style URL has been applied yet", () => {
        expect(styleApplyMode(standard, undefined, false)).toBe("await-load");
    });
});

describe("trafficLayerSpec", () => {
    it("is a line layer bound to the traffic source-layer in the middle slot", () => {
        const spec = trafficLayerSpec();
        expect(spec.id).toBe(TRAFFIC_LAYER_ID);
        expect(spec.type).toBe("line");
        expect(spec["source-layer"]).toBe("traffic");
        expect(spec.slot).toBe("middle");
    });
});
