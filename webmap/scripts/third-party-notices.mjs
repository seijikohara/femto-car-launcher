// Collect the licence notices of the production npm packages Vite can bundle
// into the map page, so the APK carries them: BSD, MIT and ISC all require the
// notice to travel with the code, and the MapLibre distribution inlines its
// dependencies without theirs. Runs after `vp build` (see package.json) and
// writes into the built web assets; the Kotlin licences screen reads the file
// from assets/web/. Type-only packages never reach the bundle and are skipped,
// and so are the direct dependencies: each of those has its own manual
// AboutLibraries entry under app/config, the single home for that credit.
import { execFileSync } from "node:child_process";
import { mkdirSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";

const webmapDir = join(import.meta.dirname, "..");
const outFile = join(webmapDir, "dist", "web", "THIRD-PARTY-NOTICES.txt");
const directDependencies = new Set(
    Object.keys(JSON.parse(readFileSync(join(webmapDir, "package.json"), "utf8")).dependencies),
);
const separator = "=".repeat(72);
const args = ["licenses", "list", "--prod", "--json"];

// Under `pnpm run` the running package manager is reachable through
// npm_execpath; a bare invocation falls back to whatever pnpm is on PATH.
const listingJson =
    process.env.npm_execpath === undefined
        ? execFileSync("pnpm", args, { encoding: "utf8" })
        : execFileSync(process.execPath, [process.env.npm_execpath, ...args], { encoding: "utf8" });

// `pnpm licenses list` groups packages by licence id; flatten to one row per
// package, keyed on the group's id.
const packages = Object.entries(JSON.parse(listingJson))
    .flatMap(([license, entries]) => entries.map((entry) => ({ ...entry, license })))
    .filter((entry) => !entry.name.startsWith("@types/") && !directDependencies.has(entry.name))
    .toSorted((a, b) => a.name.localeCompare(b.name));

const noticeOf = (dir) => {
    const file = readdirSync(dir).find((name) => /^licen[cs]e/iu.test(name));
    return file === undefined ? null : readFileSync(join(dir, file), "utf8").trim();
};

const authorOf = (meta) => (typeof meta.author === "string" ? meta.author : meta.author?.name);

const sections = packages.map((entry) => {
    const dir = entry.paths[0];
    const meta = JSON.parse(readFileSync(join(dir, "package.json"), "utf8"));
    const header = [`${entry.name}@${entry.versions.join(", ")}`, `License: ${entry.license}`];
    if (meta.homepage) {
        header.push(meta.homepage);
    }
    const author = authorOf(meta);
    if (author) {
        header.push(`Author: ${author}`);
    }
    const notice =
        noticeOf(dir) ??
        `(no license file in the package; license declared as ${entry.license} in its package.json)`;
    return `${header.join("\n")}\n\n${notice}`;
});

mkdirSync(dirname(outFile), { recursive: true });
writeFileSync(
    outFile,
    [
        "Third-party notices for the map page bundle",
        "",
        "Generated at build time from the production npm dependency tree of webmap/;",
        "the direct dependencies are credited on the licences screen in their own right.",
        "",
        separator,
        "",
        sections.join(`\n\n${separator}\n\n`),
        "",
    ].join("\n"),
);
console.log(`third-party notices: ${packages.length} packages -> ${outFile}`);
