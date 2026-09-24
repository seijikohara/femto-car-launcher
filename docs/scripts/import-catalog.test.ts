// docs/scripts/import-catalog.test.ts
import {
    existsSync,
    mkdirSync,
    mkdtempSync,
    readFileSync,
    rmSync,
    writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import sharp from "sharp";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import {
    THUMB_WIDTH,
    importCatalog,
    missingImages,
    parseArgs,
    toSiteEntry,
    type GeneratorManifest,
    type SiteManifest,
} from "./import-catalog.ts";

const axes = [
    {
        id: "geometry",
        label: "Geometry",
        values: [{ id: "floor-800x480", label: "Floor 800×480" }],
    },
    {
        id: "scale",
        label: "Display size",
        values: [
            { id: "small", label: "Small" },
            { id: "medium", label: "Medium" },
        ],
    },
];

const manifest: GeneratorManifest = {
    schemaVersion: 1,
    generatedAt: "2026-09-20T12:00:00Z",
    gitSha: "abc1234",
    axes,
    entries: [
        {
            id: "floor-800x480__small",
            file: "img/floor-800x480__small.png",
            widthDp: 800,
            heightDp: 480,
            values: { geometry: "floor-800x480", scale: "small" },
        },
        {
            id: "floor-800x480__medium",
            file: "img/floor-800x480__medium.png",
            widthDp: 800,
            heightDp: 480,
            values: { geometry: "floor-800x480", scale: "medium" },
        },
    ],
};

let input: string;
let output: string;

const png = (path: string, width: number, height: number) =>
    sharp({ create: { width, height, channels: 3, background: "#3be0ae" } })
        .png()
        .toFile(path);

// Hoisted to module scope (oxlint unicorn/consistent-function-scoping): it
// captures nothing from the test body, so there is no reason to recreate it
// per run.
const existsOnlySmallImage = (path: string) =>
    path.endsWith("floor-800x480__small.png");

beforeEach(async () => {
    input = mkdtempSync(join(tmpdir(), "catalog-in-"));
    output = mkdtempSync(join(tmpdir(), "catalog-out-"));
    mkdirSync(join(input, "img"));
    writeFileSync(join(input, "manifest.json"), JSON.stringify(manifest));
    await png(join(input, "img/floor-800x480__small.png"), 40, 24);
    await png(join(input, "img/floor-800x480__medium.png"), 40, 24);
});

afterEach(() => {
    rmSync(input, { recursive: true, force: true });
    rmSync(output, { recursive: true, force: true });
});

describe("importCatalog", () => {
    it("writes full and thumbnail WebP files and an augmented manifest, in manifest order", async () => {
        const logs: string[] = [];
        expect(
            await importCatalog(input, output, (line) => logs.push(line)),
        ).toBe("imported");
        expect(existsSync(join(output, "full/floor-800x480__small.webp"))).toBe(
            true,
        );
        expect(
            existsSync(join(output, "thumb/floor-800x480__medium.webp")),
        ).toBe(true);
        const site = JSON.parse(
            readFileSync(join(output, "manifest.json"), "utf8"),
        ) as SiteManifest;
        expect(site.schemaVersion).toBe(1);
        expect(site.gitSha).toBe("abc1234");
        expect(site.thumbWidth).toBe(THUMB_WIDTH);
        expect(site.axes).toEqual(axes);
        expect(site.entries.map((entry) => entry.id)).toEqual([
            "floor-800x480__small",
            "floor-800x480__medium",
        ]);
        expect(site.entries[0]).toEqual({
            id: "floor-800x480__small",
            widthDp: 800,
            heightDp: 480,
            values: { geometry: "floor-800x480", scale: "small" },
            full: "full/floor-800x480__small.webp",
            thumb: "thumb/floor-800x480__small.webp",
            widthPx: 40,
            heightPx: 24,
        });
        expect(logs.at(-1)).toContain("2 entries");
    });

    it("never enlarges a thumbnail beyond the source", async () => {
        await importCatalog(input, output);
        const meta = await sharp(
            join(output, "thumb/floor-800x480__small.webp"),
        ).metadata();
        expect(meta.width).toBe(40);
    });

    it("is a no-op when the input directory has no manifest", async () => {
        rmSync(join(input, "manifest.json"));
        const logs: string[] = [];
        expect(
            await importCatalog(input, output, (line) => logs.push(line)),
        ).toBe("skipped");
        expect(existsSync(join(output, "manifest.json"))).toBe(false);
        expect(logs[0]).toContain("no manifest");
    });

    it("fails when a manifest entry has no image", async () => {
        rmSync(join(input, "img/floor-800x480__medium.png"));
        await expect(importCatalog(input, output)).rejects.toThrow(
            /floor-800x480__medium/,
        );
    });

    it("rejects an unknown manifest schema version", async () => {
        writeFileSync(
            join(input, "manifest.json"),
            JSON.stringify({ ...manifest, schemaVersion: 2 }),
        );
        await expect(importCatalog(input, output)).rejects.toThrow(
            /schemaVersion 2/,
        );
    });
});

describe("parseArgs", () => {
    it("defaults both the input and output directories when given no args", () => {
        expect(parseArgs([])).toEqual({
            inputDir: "../app/build/outputs/catalog",
            outputDir: "public/catalog",
        });
    });

    it("takes an explicit output directory as the second positional", () => {
        expect(
            parseArgs([
                "../nightly-src/app/build/outputs/catalog",
                "public/catalog/nightly",
            ]),
        ).toEqual({
            inputDir: "../nightly-src/app/build/outputs/catalog",
            outputDir: "public/catalog/nightly",
        });
    });

    it("drops the `--` that `pnpm run` forwards verbatim, keeping the positionals after it", () => {
        expect(
            parseArgs([
                "--",
                "../nightly-src/app/build/outputs/catalog",
                "public/catalog/nightly",
            ]),
        ).toEqual({
            inputDir: "../nightly-src/app/build/outputs/catalog",
            outputDir: "public/catalog/nightly",
        });
    });
});

describe("pure helpers", () => {
    it("missingImages lists the ids whose file is absent", () => {
        expect(missingImages(manifest, "/in", existsOnlySmallImage)).toEqual([
            "floor-800x480__medium",
        ]);
    });

    it("toSiteEntry swaps the PNG path for the WebP pair and records pixel size", () => {
        expect(
            toSiteEntry(manifest.entries[1]!, { width: 800, height: 480 }),
        ).toEqual({
            id: "floor-800x480__medium",
            widthDp: 800,
            heightDp: 480,
            values: { geometry: "floor-800x480", scale: "medium" },
            full: "full/floor-800x480__medium.webp",
            thumb: "thumb/floor-800x480__medium.webp",
            widthPx: 800,
            heightPx: 480,
        });
    });
});
