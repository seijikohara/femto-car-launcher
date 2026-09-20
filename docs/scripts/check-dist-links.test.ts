// docs/scripts/check-dist-links.test.ts
import { describe, expect, it } from "vitest";
import { collectProblems } from "./check-dist-links.ts";

const base = "/femto-car-launcher";
const existing = new Set([
    "dist/index.html",
    "dist/terms/index.html",
    "dist/_astro/logo.abc123.svg",
]);
const exists = (path: string) => existing.has(path);

describe("collectProblems", () => {
    it("accepts base-prefixed links to files that exist", () => {
        const pages = new Map([
            [
                "dist/index.html",
                `<a href="/femto-car-launcher/terms/">Terms</a><link href="/femto-car-launcher/_astro/logo.abc123.svg">`,
            ],
        ]);
        expect(collectProblems(pages, base, exists)).toEqual([]);
    });

    it("flags root-relative links that miss the base", () => {
        const pages = new Map([
            ["dist/index.html", `<a href="/terms/">Terms</a>`],
        ]);
        expect(collectProblems(pages, base, exists)).toEqual([
            'dist/index.html: "/terms/" does not start with "/femto-car-launcher/"',
        ]);
    });

    it("flags base-prefixed links whose target does not exist in dist", () => {
        const pages = new Map([
            [
                "dist/index.html",
                `<a href="/femto-car-launcher/catalog/">Catalog</a>`,
            ],
        ]);
        expect(collectProblems(pages, base, exists)).toEqual([
            'dist/index.html: "/femto-car-launcher/catalog/" has no file in dist (looked for dist/catalog/index.html)',
        ]);
    });

    it("ignores external, protocol-relative, mailto and fragment links", () => {
        const pages = new Map([
            [
                "dist/index.html",
                `<a href="https://github.com/">g</a><a href="//cdn.example/x">c</a><a href="mailto:a@b.c">m</a><a href="#top">t</a>`,
            ],
        ]);
        expect(collectProblems(pages, base, exists)).toEqual([]);
    });

    it("strips query strings and fragments before checking existence", () => {
        const pages = new Map([
            [
                "dist/index.html",
                `<a href="/femto-car-launcher/terms/?utm=1#contact">c</a>`,
            ],
        ]);
        expect(collectProblems(pages, base, exists)).toEqual([]);
    });
});
