import { describe, expect, it } from "vitest";
import {
	mapboxStyleUrl,
	TRAFFIC_LAYER_ID,
	trafficLayerSpec,
} from "./mapbox-style";

describe("mapboxStyleUrl", () => {
	it("maps known style ids to mapbox:// urls", () => {
		expect(mapboxStyleUrl("standard")).toBe("mapbox://styles/mapbox/standard");
		expect(mapboxStyleUrl("satellite-streets-v12")).toBe(
			"mapbox://styles/mapbox/satellite-streets-v12",
		);
		expect(mapboxStyleUrl("streets-v12")).toBe(
			"mapbox://styles/mapbox/streets-v12",
		);
	});
	it("falls back to standard for an unknown id", () => {
		expect(mapboxStyleUrl("nope")).toBe("mapbox://styles/mapbox/standard");
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
