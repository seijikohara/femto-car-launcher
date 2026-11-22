// Regenerate the Standard map scheme's two bundled styles from the bundled bases.
//
// The Standard scheme keeps the Positron (light) and Dark Matter (dark) bases and paints them
// with a fixed palette that borrows OpenMapTiles' readability: a road hierarchy in muted warm
// tones, blue water and green parks. The palettes below are the source of truth; the styles
// this writes are generated output, so change a colour here and run the script again. It also
// stores the 3D building colour in each style's metadata, where the page reads it.
//
// Usage: node scripts/build-standard-map-styles.mjs

import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

const mapDir = (name) => fileURLToPath(new URL(`../app/src/main/assets/map/${name}`, import.meta.url));

const PALETTES = {
    light: {
        name: "Femto Standard Light (OpenFreeMap)",
        // A pure white ground keeps the full-screen map clean; a tinted ground reads as dull.
        background: "#ffffff",
        residential: "#ffffff",
        park: "#e3f0da",
        wood: "#d8eacb",
        water: "#cde2f4",
        waterway: "#b2d0eb",
        building: "#f1f1ef",
        buildingOutline: "#e1e1de",
        building3d: "#e6e6e4",
        path: "#ececec",
        // Roads are drawn darker than the ground, because white roads vanish into a white ground.
        minor: "#e4e4e4",
        major: { trunk: "#fbdca5", primary: "#fde7bd", secondary: "#f4f4f4", tertiary: "#f4f4f4" },
        majorCasing: "#b9b9b9",
        motorway: "#f8c987",
        motorwayCasing: "#cc8d43",
        subtleMajor: "#d7d7d7",
        subtleMotorway: "#e8b675",
        rail: "#d0d0d0",
        railDash: "#ffffff",
        boundary: "#b0b0b0",
        label: "#202020",
        labelHalo: "#ffffff",
        roadLabel: "#555555",
        waterLabel: "#3f6587",
    },
    dark: {
        name: "Femto Standard Dark (OpenFreeMap)",
        background: "#121417",
        residential: "#15181b",
        park: "#18251c",
        wood: "#19271e",
        water: "#152b42",
        waterway: "#1e3c5a",
        building: "#1d2024",
        buildingOutline: "#272b30",
        building3d: "#2e3238",
        path: "#43474d",
        // Every road at least 3:1 against the ground: roads near 2:1 vanished on the head unit's
        // panel before, which is why the accent scheme's dark roads sit so high.
        minor: "#60656c",
        major: { trunk: "#9a7c48", primary: "#8a7550", secondary: "#70757c", tertiary: "#6a6f76" },
        majorCasing: "#0e1012",
        motorway: "#b0844a",
        motorwayCasing: "#0e1012",
        subtleMajor: "#4f5359",
        subtleMotorway: "#8a6a3c",
        rail: "#3a3d42",
        railDash: "#121417",
        boundary: "#4a4e55",
        label: "#c8cbd0",
        labelHalo: "#121417",
        roadLabel: "#9a9fa5",
        waterLabel: "#7398bd",
    },
};

// The major roads' fill by OpenMapTiles road class, falling back to the tertiary colour.
const majorFill = (major) => [
    "match",
    ["get", "class"],
    ...Object.entries(major).flat(),
    major.tertiary,
];

// The palette role a base layer takes, or null to keep the base's own colours (aeroways,
// piers, ice, shields, one-way arrows).
const paintFor = (layer, p) => {
    const { id, type } = layer;
    const source = layer["source-layer"];
    if (type === "background") return { "background-color": p.background };
    if (source === "water" && type === "fill") return { "fill-color": p.water };
    if (source === "waterway" && type === "line") return { "line-color": p.waterway };
    if (source === "park" || id === "landuse_park") return { "fill-color": p.park };
    if (id === "landcover_wood") return { "fill-color": p.wood };
    if (id === "landuse_residential") return { "fill-color": p.residential };
    if (source === "building" && type === "fill") {
        return { "fill-color": p.building, "fill-outline-color": p.buildingOutline };
    }
    if (id === "highway_path") return { "line-color": p.path };
    if (id === "highway_minor") return { "line-color": p.minor };
    if (id === "highway_major_casing") return { "line-color": p.majorCasing };
    if (id === "highway_major_inner") return { "line-color": majorFill(p.major) };
    if (id === "highway_major_subtle") return { "line-color": p.subtleMajor };
    if (id.includes("motorway") && id.includes("casing")) return { "line-color": p.motorwayCasing };
    if (id.includes("motorway") && id.endsWith("_inner")) return { "line-color": p.motorway };
    if (id === "highway_motorway_subtle") return { "line-color": p.subtleMotorway };
    if (source === "transportation" && id.startsWith("railway")) {
        return { "line-color": id.includes("dash") ? p.railDash : p.rail };
    }
    if (source === "boundary") return { "line-color": p.boundary };
    if (type !== "symbol") return null;
    if (source === "water_name" || id === "waterway_line_label") {
        return { "text-color": p.waterLabel, "text-halo-color": p.labelHalo };
    }
    if (source === "transportation_name" && !id.includes("shield")) {
        return { "text-color": p.roadLabel, "text-halo-color": p.labelHalo };
    }
    if (source === "place" || source === "aerodrome_label") {
        return { "text-color": p.label, "text-halo-color": p.labelHalo };
    }
    return null;
};

const build = (base, p) => ({
    ...base,
    name: p.name,
    metadata: { ...base.metadata, "femto:building-color": p.building3d },
    layers: base.layers.map((layer) => {
        const paint = paintFor(layer, p);
        return paint ? { ...layer, paint: { ...layer.paint, ...paint } } : layer;
    }),
});

for (const [mode, palette] of Object.entries(PALETTES)) {
    const base = JSON.parse(await readFile(mapDir(`${mode}.json`), "utf8"));
    const out = mapDir(`standard-${mode}.json`);
    await writeFile(out, JSON.stringify(build(base, palette)));
    console.log(`wrote ${out}`);
}
