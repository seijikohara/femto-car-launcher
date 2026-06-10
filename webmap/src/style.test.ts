import type { StyleSpecification } from "maplibre-gl";
import { describe, expect, it } from "vitest";
import {
	injectFeatures,
	MAPTERHORN_DEM_URL,
	MAX_MARKER_DROP,
	markerPadTop,
	vectorSourceId,
} from "./style";

const OFF = { buildings: false, terrain: false, accent: null };

function baseStyle(): StyleSpecification {
	return {
		version: 8,
		sources: {
			openmaptiles: { type: "vector", url: "https://example.test/tiles.json" },
			rasterdem: { type: "raster-dem", url: "https://example.test/dem.json" },
		},
		layers: [
			{ id: "bg", type: "background" },
			{
				id: "water",
				type: "fill",
				source: "openmaptiles",
				"source-layer": "water",
			},
			{
				id: "park",
				type: "fill",
				source: "openmaptiles",
				"source-layer": "park",
			},
			{
				id: "road",
				type: "fill",
				source: "openmaptiles",
				"source-layer": "transportation",
			},
			{
				id: "labels",
				type: "symbol",
				source: "openmaptiles",
				"source-layer": "place",
			},
		],
	} as StyleSpecification;
}

describe("vectorSourceId", () => {
	it("returns the first vector source id", () => {
		expect(vectorSourceId(baseStyle())).toBe("openmaptiles");
	});

	it("returns undefined when the style has no vector source", () => {
		const style = baseStyle();
		style.sources = { rasterOnly: { type: "raster", tiles: [] } };
		expect(vectorSourceId(style)).toBeUndefined();
	});
});

describe("injectFeatures: 3D buildings", () => {
	it("inserts the extrusion layer beneath the first symbol layer", () => {
		const style = injectFeatures(baseStyle(), { ...OFF, buildings: true });
		const ids = style.layers.map((l) => l.id);
		expect(ids.indexOf("femto-3d-buildings")).toBe(ids.indexOf("labels") - 1);
	});

	it("appends the extrusion layer when the style has no symbol layer", () => {
		const style = baseStyle();
		style.layers = style.layers.filter((l) => l.type !== "symbol");
		const out = injectFeatures(style, { ...OFF, buildings: true });
		expect(out.layers.at(-1)?.id).toBe("femto-3d-buildings");
	});

	it("does not inject without a vector source", () => {
		const style = baseStyle();
		style.sources = {};
		const out = injectFeatures(style, { ...OFF, buildings: true });
		expect(out.layers.some((l) => l.id === "femto-3d-buildings")).toBe(false);
	});

	it("is idempotent across re-application", () => {
		const once = injectFeatures(baseStyle(), { ...OFF, buildings: true });
		const twice = injectFeatures(once, { ...OFF, buildings: true });
		expect(
			twice.layers.filter((l) => l.id === "femto-3d-buildings"),
		).toHaveLength(1);
	});

	it("removes a previously injected layer when toggled off", () => {
		const on = injectFeatures(baseStyle(), { ...OFF, buildings: true });
		const off = injectFeatures(on, OFF);
		expect(off.layers.some((l) => l.id === "femto-3d-buildings")).toBe(false);
	});
});

describe("injectFeatures: terrain", () => {
	it("injects the DEM source and terrain when enabled", () => {
		const style = injectFeatures(baseStyle(), { ...OFF, terrain: true });
		expect(style.sources.terrainSource).toEqual({
			type: "raster-dem",
			url: MAPTERHORN_DEM_URL,
		});
		expect(style.terrain).toEqual({
			source: "terrainSource",
			exaggeration: 1.0,
		});
	});

	it("strips our terrain when toggled off but keeps foreign DEM sources", () => {
		const on = injectFeatures(baseStyle(), { ...OFF, terrain: true });
		const off = injectFeatures(on, OFF);
		expect(off.sources.terrainSource).toBeUndefined();
		expect(off.terrain).toBeUndefined();
		expect(off.sources.rasterdem).toBeDefined();
	});
});

describe("injectFeatures: accent recolour", () => {
	const accent = { background: "#101010", water: "#202020", land: "#303030" };

	it("recolours background, water, and land fills", () => {
		const style = injectFeatures(baseStyle(), { ...OFF, accent });
		const paintOf = (id: string) =>
			(
				style.layers.find((l) => l.id === id) as {
					paint?: Record<string, unknown>;
				}
			).paint;
		expect(paintOf("bg")?.["background-color"]).toBe(accent.background);
		expect(paintOf("water")?.["fill-color"]).toBe(accent.water);
		expect(paintOf("park")?.["fill-color"]).toBe(accent.land);
	});

	it("leaves road fills untouched", () => {
		const style = injectFeatures(baseStyle(), { ...OFF, accent });
		const road = style.layers.find((l) => l.id === "road") as {
			paint?: Record<string, unknown>;
		};
		expect(road.paint?.["fill-color"]).toBeUndefined();
	});

	it("applies no recolour for a plain (non-accent) style", () => {
		const style = injectFeatures(baseStyle(), OFF);
		const bg = style.layers.find((l) => l.id === "bg") as {
			paint?: Record<string, unknown>;
		};
		expect(bg.paint).toBeUndefined();
	});
});

describe("markerPadTop", () => {
	it("keeps the focal point centred at markerPos 0", () => {
		expect(markerPadTop(0, 480)).toBe(0);
	});

	it("drops the focal point by 2 * MAX_MARKER_DROP of the height at markerPos 100", () => {
		expect(markerPadTop(100, 480)).toBeCloseTo(2 * MAX_MARKER_DROP * 480);
	});

	it("clamps out-of-range positions", () => {
		expect(markerPadTop(250, 480)).toBe(markerPadTop(100, 480));
		expect(markerPadTop(-5, 480)).toBe(0);
	});
});
