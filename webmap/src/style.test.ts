import type { LayerSpecification, StyleSpecification } from "maplibre-gl";
import { describe, expect, it } from "vitest";
import {
	BUILDING_EXTRUSION_OPACITY,
	clampMarkerPos,
	injectFeatures,
	MAPTERHORN_DEM_URL,
	MAX_MARKER_DROP,
	markerDrop,
	markerPadRight,
	markerPadTop,
	markerXFraction,
	roadClassOrNull,
	vectorSourceId,
} from "./style";

const OFF = {
	buildings: false,
	terrain: false,
	accent: null,
	buildingColor: "",
};

function roadLine(id: string): LayerSpecification {
	return {
		id,
		type: "line",
		source: "openmaptiles",
		"source-layer": "transportation",
	} as LayerSpecification;
}

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
				id: "building",
				type: "fill",
				source: "openmaptiles",
				"source-layer": "building",
			},
			roadLine("highway_motorway_casing"),
			roadLine("highway_motorway_inner"),
			roadLine("highway_major_subtle"),
			roadLine("highway_minor"),
			roadLine("highway_path"),
			roadLine("tunnel_motorway_casing"),
			roadLine("railway_minor"),
			roadLine("road_pier"),
			{
				id: "aeroway-runway",
				type: "line",
				source: "openmaptiles",
				"source-layer": "aeroway",
			},
			{
				id: "labels",
				type: "symbol",
				source: "openmaptiles",
				"source-layer": "place",
			},
			{
				id: "labels_haloed",
				type: "symbol",
				source: "openmaptiles",
				"source-layer": "place",
				paint: { "text-halo-width": 1.4 },
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
	const BUILDINGS = { ...OFF, buildings: true, buildingColor: "#404040" };

	it("inserts the extrusion layer beneath the first symbol layer", () => {
		const style = injectFeatures(baseStyle(), BUILDINGS);
		const ids = style.layers.map((l) => l.id);
		expect(ids.indexOf("femto-3d-buildings")).toBe(ids.indexOf("labels") - 1);
	});

	it("appends the extrusion layer when the style has no symbol layer", () => {
		const style = baseStyle();
		style.layers = style.layers.filter((l) => l.type !== "symbol");
		const out = injectFeatures(style, BUILDINGS);
		expect(out.layers.at(-1)?.id).toBe("femto-3d-buildings");
	});

	it("paints the extrusion with the pushed theme colour, semi-transparent", () => {
		const style = injectFeatures(baseStyle(), BUILDINGS);
		const paint = (
			style.layers.find((l) => l.id === "femto-3d-buildings") as {
				paint?: Record<string, unknown>;
			}
		).paint;
		expect(paint?.["fill-extrusion-color"]).toBe(BUILDINGS.buildingColor);
		expect(paint?.["fill-extrusion-opacity"]).toBe(BUILDING_EXTRUSION_OPACITY);
	});

	it("does not inject without a vector source", () => {
		const style = baseStyle();
		style.sources = {};
		const out = injectFeatures(style, BUILDINGS);
		expect(out.layers.some((l) => l.id === "femto-3d-buildings")).toBe(false);
	});

	it("does not inject before the theme colour arrives", () => {
		const out = injectFeatures(baseStyle(), {
			...OFF,
			buildings: true,
			buildingColor: "",
		});
		expect(out.layers.some((l) => l.id === "femto-3d-buildings")).toBe(false);
	});

	it("is idempotent across re-application", () => {
		const once = injectFeatures(baseStyle(), BUILDINGS);
		const twice = injectFeatures(once, BUILDINGS);
		expect(
			twice.layers.filter((l) => l.id === "femto-3d-buildings"),
		).toHaveLength(1);
	});

	it("removes a previously injected layer when toggled off", () => {
		const on = injectFeatures(baseStyle(), BUILDINGS);
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
	const accent = {
		background: "#101010",
		water: "#202020",
		land: "#303030",
		roadMajor: "#404040",
		roadMinor: "#505050",
		roadCasing: "#606060",
		building: "#707070",
		label: "#808080",
	};

	function paintOf(style: StyleSpecification, id: string) {
		return (
			style.layers.find((l) => l.id === id) as {
				paint?: Record<string, unknown>;
			}
		).paint;
	}

	it("recolours background, water, land, and building fills", () => {
		const style = injectFeatures(baseStyle(), { ...OFF, accent });
		expect(paintOf(style, "bg")?.["background-color"]).toBe(accent.background);
		expect(paintOf(style, "water")?.["fill-color"]).toBe(accent.water);
		expect(paintOf(style, "park")?.["fill-color"]).toBe(accent.land);
		expect(paintOf(style, "building")?.["fill-color"]).toBe(accent.building);
	});

	it("recolours roads by class: casing, minor (incl. path/subtle), major", () => {
		const style = injectFeatures(baseStyle(), { ...OFF, accent });
		expect(paintOf(style, "highway_motorway_casing")?.["line-color"]).toBe(
			accent.roadCasing,
		);
		expect(paintOf(style, "tunnel_motorway_casing")?.["line-color"]).toBe(
			accent.roadCasing,
		);
		expect(paintOf(style, "highway_minor")?.["line-color"]).toBe(
			accent.roadMinor,
		);
		expect(paintOf(style, "highway_path")?.["line-color"]).toBe(
			accent.roadMinor,
		);
		expect(paintOf(style, "highway_major_subtle")?.["line-color"]).toBe(
			accent.roadMinor,
		);
		expect(paintOf(style, "highway_motorway_inner")?.["line-color"]).toBe(
			accent.roadMajor,
		);
	});

	it("recolours labels with a background halo, seeding a missing halo width", () => {
		const style = injectFeatures(baseStyle(), { ...OFF, accent });
		const paint = paintOf(style, "labels");
		expect(paint?.["text-color"]).toBe(accent.label);
		expect(paint?.["text-halo-color"]).toBe(accent.background);
		expect(paint?.["text-halo-width"]).toBe(1);
	});

	it("keeps an existing halo width", () => {
		const style = injectFeatures(baseStyle(), { ...OFF, accent });
		expect(paintOf(style, "labels_haloed")?.["text-halo-width"]).toBe(1.4);
	});

	it("leaves railways, piers, and aeroways untouched", () => {
		const style = injectFeatures(baseStyle(), { ...OFF, accent });
		expect(paintOf(style, "railway_minor")?.["line-color"]).toBeUndefined();
		expect(paintOf(style, "road_pier")?.["line-color"]).toBeUndefined();
		expect(paintOf(style, "aeroway-runway")?.["line-color"]).toBeUndefined();
	});

	it("applies no recolour for a plain (non-accent) style", () => {
		const style = injectFeatures(baseStyle(), OFF);
		const bg = style.layers.find((l) => l.id === "bg") as {
			paint?: Record<string, unknown>;
		};
		expect(bg.paint).toBeUndefined();
		expect(paintOf(style, "building")?.["fill-color"]).toBeUndefined();
	});
});

describe("roadClassOrNull", () => {
	it("classifies highway_/tunnel_ transportation lines", () => {
		expect(roadClassOrNull(roadLine("highway_motorway_casing"))).toBe("casing");
		expect(roadClassOrNull(roadLine("highway_minor"))).toBe("minor");
		expect(roadClassOrNull(roadLine("highway_path"))).toBe("minor");
		expect(roadClassOrNull(roadLine("highway_major_subtle"))).toBe("minor");
		expect(roadClassOrNull(roadLine("highway_major_inner"))).toBe("major");
		expect(roadClassOrNull(roadLine("tunnel_motorway_inner"))).toBe("major");
	});

	it("returns null outside the highway_/tunnel_ prefixes", () => {
		expect(roadClassOrNull(roadLine("railway_minor"))).toBeNull();
		expect(roadClassOrNull(roadLine("road_pier"))).toBeNull();
	});

	it("returns null for non-line or non-transportation layers", () => {
		expect(
			roadClassOrNull({
				id: "highway_minor",
				type: "fill",
				"source-layer": "transportation",
			} as LayerSpecification),
		).toBeNull();
		expect(
			roadClassOrNull({
				id: "highway_minor",
				type: "line",
				"source-layer": "aeroway",
			} as LayerSpecification),
		).toBeNull();
	});
});

describe("markerDrop", () => {
	it("keeps the marker centred at markerPos 0", () => {
		expect(markerDrop(0, 0)).toBe(0);
	});

	it("drops by MAX_MARKER_DROP at markerPos 100 when the overlay leaves room", () => {
		expect(markerDrop(100, 0)).toBeCloseTo(MAX_MARKER_DROP);
	});

	it("clamps the drop so the marker stays above the bottom overlay", () => {
		// bottomSafe 0.4 caps the centre at 0.5 - 0.4 = 0.1, below MAX_MARKER_DROP.
		expect(markerDrop(100, 0.4)).toBeCloseTo(0.1);
		expect(markerDrop(50, 0.4)).toBeCloseTo(0.05);
	});

	it("never drops when the overlay leaves no room", () => {
		expect(markerDrop(100, 0.5)).toBe(0);
		expect(markerDrop(100, 0.8)).toBe(0);
	});

	it("clamps out-of-range positions", () => {
		expect(markerDrop(250, 0)).toBe(markerDrop(100, 0));
		expect(markerDrop(-5, 0)).toBe(0);
	});
});

describe("markerPadTop", () => {
	it("keeps the focal point centred at markerPos 0", () => {
		expect(markerPadTop(0, 0, 480)).toBe(0);
	});

	it("drops the focal point by 2 * MAX_MARKER_DROP of the height at markerPos 100", () => {
		expect(markerPadTop(100, 0, 480)).toBeCloseTo(2 * MAX_MARKER_DROP * 480);
	});

	it("clamps out-of-range positions", () => {
		expect(markerPadTop(250, 0, 480)).toBe(markerPadTop(100, 0, 480));
		expect(markerPadTop(-5, 0, 480)).toBe(0);
	});

	it("tracks the overlay-clamped drop", () => {
		expect(markerPadTop(100, 0.4, 480)).toBeCloseTo(2 * 0.1 * 480);
	});
});

describe("markerXFraction", () => {
	it("keeps the marker centred when no right cards are present", () => {
		expect(markerXFraction(0)).toBe(0);
	});

	it("shifts the marker half the right-safe fraction left of centre", () => {
		expect(markerXFraction(0.4)).toBeCloseTo(0.2);
	});

	it("caps the shift so the marker stays within the left quarter", () => {
		expect(markerXFraction(0.9)).toBeCloseTo(0.35);
	});

	it("clamps a negative right-safe fraction to centre, mirroring the Kotlin coerceIn", () => {
		expect(markerXFraction(-0.2)).toBe(0);
	});
});

describe("markerPadRight", () => {
	it("keeps the focal point centred when no right cards are present", () => {
		expect(markerPadRight(0, 1000)).toBe(0);
	});

	it("pads the right by 2 * the marker shift of the width", () => {
		expect(markerPadRight(0.3, 1000)).toBeCloseTo(2 * 0.15 * 1000);
	});
});

describe("clampMarkerPos", () => {
	it("passes in-range values through", () => {
		expect(clampMarkerPos(0)).toBe(0);
		expect(clampMarkerPos(50)).toBe(50);
		expect(clampMarkerPos(100)).toBe(100);
	});

	it("clamps out-of-range values to 0..100", () => {
		expect(clampMarkerPos(-5)).toBe(0);
		expect(clampMarkerPos(250)).toBe(100);
	});

	it("treats a missing (NaN / falsy) value as 0", () => {
		expect(clampMarkerPos(Number.NaN)).toBe(0);
	});
});
