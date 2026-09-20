// docs/scripts/check-dist-links.ts
// Fails the build when a page links to a root-relative path that misses the
// GitHub Pages base or points at nothing in dist/. Runs under Node 24's
// built-in type stripping (`node scripts/check-dist-links.ts`), so it uses no
// TypeScript-only runtime syntax.
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import siteBase from "../site.base.json" with { type: "json" };

const ATTRIBUTE = /\b(?:href|src)="([^"]*)"/g;
const EXTERNAL_OR_ANCHOR = /^(?:[a-z][a-z0-9+.-]*:|\/\/|#)/i;

const targetFile = (dist: string, urlPath: string): string => {
    const clean = urlPath.replace(/[?#].*$/, "");
    return clean.endsWith("/")
        ? join(dist, clean, "index.html")
        : join(dist, clean);
};

export const collectProblems = (
    pages: Map<string, string>,
    base: string,
    exists: (path: string) => boolean,
    dist = "dist",
): string[] => {
    const problems: string[] = [];
    for (const [page, html] of pages) {
        for (const match of html.matchAll(ATTRIBUTE)) {
            const url = match[1];
            if (
                url === undefined ||
                url === "" ||
                EXTERNAL_OR_ANCHOR.test(url) ||
                !url.startsWith("/")
            )
                continue;
            if (!url.startsWith(`${base}/`)) {
                problems.push(
                    `${page}: "${url}" does not start with "${base}/"`,
                );
                continue;
            }
            const file = targetFile(dist, url.slice(base.length));
            if (!exists(file))
                problems.push(
                    `${page}: "${url}" has no file in dist (looked for ${file})`,
                );
        }
    }
    return problems;
};

const htmlFiles = (dir: string): string[] =>
    readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
        const path = join(dir, entry.name);
        if (entry.isDirectory()) return htmlFiles(path);
        return entry.name.endsWith(".html") ? [path] : [];
    });

const main = (): void => {
    const dist = "dist";
    const pages = new Map(
        htmlFiles(dist).map(
            (file) => [file, readFileSync(file, "utf8")] as const,
        ),
    );
    const problems = collectProblems(pages, siteBase.base, existsSync, dist);
    for (const problem of problems) console.error(problem);
    console.log(
        `check-dist-links: ${pages.size} pages, ${problems.length} problems`,
    );
    if (problems.length > 0) process.exit(1);
};

// Only the CLI entry runs main(); Vitest imports collectProblems without it.
if (process.argv[1]?.endsWith("check-dist-links.ts")) main();
