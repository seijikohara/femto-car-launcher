// docs/scripts/import-catalog.ts
// Turn the generator's catalog directory (manifest.json + img/<id>.png, see
// .claude/rules/docs.md) into what the site ships: lossy WebP at full size and
// a 480 px thumbnail per entry, plus a manifest that points at them. Runs under
// Node 24's built-in type stripping (`node scripts/import-catalog.ts`).
import {
    existsSync,
    mkdirSync,
    readFileSync,
    rmSync,
    writeFileSync,
} from "node:fs";
import { join } from "node:path";
import sharp from "sharp";
import { CATALOG_SCHEMA_VERSION } from "../src/catalog/schema.ts";

export interface AxisValue {
    id: string;
    label: string;
}

export interface Axis {
    id: string;
    label: string;
    values: AxisValue[];
}

export interface GeneratorEntry {
    id: string;
    file: string;
    widthDp: number;
    heightDp: number;
    values: Record<string, string>;
}

export interface GeneratorManifest {
    schemaVersion: number;
    generatedAt: string;
    gitSha: string;
    axes: Axis[];
    entries: GeneratorEntry[];
}

export interface SiteEntry {
    id: string;
    widthDp: number;
    heightDp: number;
    values: Record<string, string>;
    full: string;
    thumb: string;
    widthPx: number;
    heightPx: number;
}

export interface SiteManifest {
    schemaVersion: typeof CATALOG_SCHEMA_VERSION;
    generatedAt: string;
    gitSha: string;
    thumbWidth: number;
    axes: Axis[];
    entries: SiteEntry[];
}

// Lossy WebP: 960 lossless frames would weigh ~800 MB against GitHub Pages'
// 1 GB site limit; UI screenshots at q85 stay visually clean at ~150 KB.
export const FULL_QUALITY = 85;
export const THUMB_WIDTH = 480;
export const THUMB_QUALITY = 75;
// sharp is libvips-backed and multi-threaded per call; a handful of concurrent
// files keeps the CPU busy without exhausting file handles.
const CONCURRENCY = 4;

export const missingImages = (
    manifest: GeneratorManifest,
    inputDir: string,
    exists: (path: string) => boolean,
): string[] =>
    manifest.entries
        .filter((entry) => !exists(join(inputDir, entry.file)))
        .map((entry) => entry.id);

export const toSiteEntry = (
    entry: GeneratorEntry,
    size: { width: number; height: number },
): SiteEntry => ({
    id: entry.id,
    widthDp: entry.widthDp,
    heightDp: entry.heightDp,
    values: entry.values,
    full: `full/${entry.id}.webp`,
    thumb: `thumb/${entry.id}.webp`,
    widthPx: size.width,
    heightPx: size.height,
});

// A manual worker pool: each of `limit` workers pulls the next index off a
// shared queue so slow conversions don't leave other workers idle, and
// results land at their original index regardless of finish order.
const mapWithConcurrency = async <T, R>(
    items: readonly T[],
    limit: number,
    fn: (item: T) => Promise<R>,
): Promise<R[]> => {
    const queue = items.map((_, index) => index);
    const results: [number, R][] = [];
    const worker = async (): Promise<void> => {
        for (
            let index = queue.shift();
            index !== undefined;
            index = queue.shift()
        ) {
            results.push([index, await fn(items[index] as T)]);
        }
    };
    const workerCount = Math.min(limit, items.length);
    await Promise.all(Array.from({ length: workerCount }, worker));
    return results.toSorted(([a], [b]) => a - b).map(([, result]) => result);
};

const convert = async (
    inputDir: string,
    outputDir: string,
    entry: GeneratorEntry,
): Promise<SiteEntry> => {
    const source = sharp(join(inputDir, entry.file));
    const { width, height } = await source.metadata();
    if (width === undefined || height === undefined)
        throw new Error(
            `import-catalog: ${entry.file} has no readable dimensions`,
        );
    await source
        .clone()
        .webp({ quality: FULL_QUALITY })
        .toFile(join(outputDir, `full/${entry.id}.webp`));
    await source
        .clone()
        .resize({ width: THUMB_WIDTH, withoutEnlargement: true })
        .webp({ quality: THUMB_QUALITY })
        .toFile(join(outputDir, `thumb/${entry.id}.webp`));
    return toSiteEntry(entry, { width, height });
};

export async function importCatalog(
    inputDir: string,
    outputDir: string,
    log: (line: string) => void = console.log,
): Promise<"skipped" | "imported"> {
    const manifestPath = join(inputDir, "manifest.json");
    if (!existsSync(manifestPath)) {
        log(
            `import-catalog: no manifest at ${manifestPath}; building the site without a catalog`,
        );
        return "skipped";
    }
    const manifest = JSON.parse(
        readFileSync(manifestPath, "utf8"),
    ) as GeneratorManifest;
    if (manifest.schemaVersion !== CATALOG_SCHEMA_VERSION)
        throw new Error(
            `import-catalog: unsupported manifest schemaVersion ${manifest.schemaVersion}`,
        );
    const missing = missingImages(manifest, inputDir, existsSync);
    // The generator writes the manifest before it renders, so a partial run lists
    // entries that have no image; shipping those would be a broken catalog.
    if (missing.length > 0)
        throw new Error(
            `import-catalog: ${missing.length} manifest entries have no image (first: ${missing[0]})`,
        );
    rmSync(outputDir, { recursive: true, force: true });
    mkdirSync(join(outputDir, "full"), { recursive: true });
    mkdirSync(join(outputDir, "thumb"), { recursive: true });
    const entries = await mapWithConcurrency(
        manifest.entries,
        CONCURRENCY,
        (entry) => convert(inputDir, outputDir, entry),
    );
    const site: SiteManifest = {
        schemaVersion: CATALOG_SCHEMA_VERSION,
        generatedAt: manifest.generatedAt,
        gitSha: manifest.gitSha,
        thumbWidth: THUMB_WIDTH,
        axes: manifest.axes,
        entries,
    };
    writeFileSync(
        join(outputDir, "manifest.json"),
        JSON.stringify(site, null, 2),
    );
    log(`import-catalog: ${entries.length} entries → ${outputDir}`);
    return "imported";
}

export interface Args {
    inputDir: string;
    outputDir: string;
}

// `pnpm run import-catalog -- <dir> [outDir]` forwards the `--` into argv
// verbatim (Node does not strip it), so drop it before reading the
// positionals. A second positional picks the output directory (docs.yml
// uses it for the nightly channel: public/catalog/nightly); everything
// else about the importer stays keyed off `outputDir` unchanged.
export const parseArgs = (argv: readonly string[]): Args => {
    const [
        inputDir = "../app/build/outputs/catalog",
        outputDir = "public/catalog",
    ] = argv.filter((arg) => arg !== "--");
    return { inputDir, outputDir };
};

const main = async (): Promise<void> => {
    const { inputDir, outputDir } = parseArgs(process.argv.slice(2));
    await importCatalog(inputDir, outputDir);
};

// Only the CLI entry runs main(); Vitest imports the functions without it.
if (process.argv[1]?.endsWith("import-catalog.ts")) {
    main().catch((error: unknown) => {
        console.error(error instanceof Error ? error.message : error);
        process.exit(1);
    });
}
