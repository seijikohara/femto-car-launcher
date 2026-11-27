// Regenerate the snapshot of regions that have a Shortbread vector-tile file on download.geofabrik.de.
//
// The region index does not list those files, so the only way to know is to ask for each one. A
// region's file is either beside its extract or, for an extract in a directory named after the
// region, beside that directory: the host moved every extract into such a directory in November
// 2026 and left the files where they were. This asks both places with HEAD requests, one at a time,
// and writes the ids that answered 200 with the file's size. The app offers only these regions and
// confirms a choice with its own HEAD requests to the same places (RegionCatalog.kt), so a stale
// snapshot hides a new file or offers a removed one; it never downloads the wrong thing.
//
// Usage: node scripts/update-shortbread-regions.mjs [output.json]

import { writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

const HOST_PREFIX = "https://download.geofabrik.de/";
const INDEX_URL = `${HOST_PREFIX}index-v1-nogeom.json`;
const PBF_SUFFIX = "-latest.osm.pbf";
const SHORTBREAD_SUFFIX = "-shortbread-1.0.mbtiles";
const DEFAULT_OUTPUT = fileURLToPath(
    new URL("../app/src/main/assets/map/offline/shortbread-regions.json", import.meta.url),
);
const PAUSE_MS = 100;
const ATTEMPTS = 3;

const pause = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

// "…/japan/kanto/kanto" is a region in a directory named after it, and "…/japan/kanto" is the same
// name beside that directory. A name at the host's root has no such place.
const besideOwnDirectoryOrNull = (stem) => {
    const directory = stem.slice(0, stem.lastIndexOf("/"));
    const name = (path) => path.slice(path.lastIndexOf("/") + 1);
    return directory.length > HOST_PREFIX.length && name(directory) === name(stem) ? directory : null;
};

// The places a region's file can be, in the order the app asks them.
const shortbreadUrls = (properties) => {
    const pbf = properties.urls?.pbf;
    if (typeof pbf !== "string" || !pbf.startsWith(HOST_PREFIX) || !pbf.endsWith(PBF_SUFFIX)) return [];
    const stem = pbf.slice(0, -PBF_SUFFIX.length);
    return [stem, besideOwnDirectoryOrNull(stem)]
        .filter((place) => place !== null)
        .map((place) => place + SHORTBREAD_SUFFIX);
};

// A status other than 200 or 404 says nothing about whether the file exists, so it is retried and
// then fails the run: a snapshot written from a half-answered probe would silently drop regions.
const sizeOrNull = async (url) => {
    for (let attempt = 1; attempt <= ATTEMPTS; attempt += 1) {
        const response = await fetch(url, { method: "HEAD", redirect: "error" }).catch(() => null);
        if (response?.status === 200) {
            const size = Number(response.headers.get("content-length"));
            if (Number.isSafeInteger(size) && size > 0) return size;
        }
        if (response?.status === 404) return null;
        await pause(1000 * attempt);
    }
    throw new Error(`no usable answer for ${url}`);
};

// The first place that has the file gives its size, as the app takes the first place that answers.
const firstSizeOrNull = async (urls) => {
    for (const url of urls) {
        const size = await sizeOrNull(url);
        await pause(PAUSE_MS);
        if (size !== null) return size;
    }
    return null;
};

const output = process.argv[2] ?? DEFAULT_OUTPUT;
const index = await (await fetch(INDEX_URL)).json();
const regions = {};
const skipped = [];
for (const { properties } of index.features) {
    const urls = shortbreadUrls(properties);
    if (urls.length === 0) {
        skipped.push(properties.id);
        continue;
    }
    const size = await firstSizeOrNull(urls);
    if (size !== null) regions[properties.id] = size;
}

const sorted = Object.fromEntries(Object.entries(regions).sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0)));
const snapshot = {
    generated: new Date().toISOString().slice(0, 10),
    source: INDEX_URL,
    regions: sorted,
};
await writeFile(output, `${JSON.stringify(snapshot, null, 2)}\n`);
console.log(
    `${Object.keys(sorted).length} of ${index.features.length} regions have a Shortbread file` +
        (skipped.length > 0 ? `; no file URL could be derived for: ${skipped.join(", ")}` : ""),
);
