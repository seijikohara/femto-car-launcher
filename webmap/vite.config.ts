import { resolve } from "node:path";
// vite-plus re-exports Vite's defineConfig with the Vite+ blocks (test, lint)
// typed; `vp dev` / `vp build` / `vp test` / `vp lint` / `vp check` all read
// this one file.
import { defineConfig } from "vite-plus";

export default defineConfig({
    // Relative asset URLs: the page is served from
    // https://appassets.androidplatform.net/assets/web/index.html, so absolute
    // "/assets/..." URLs would escape the web/ asset subtree.
    base: "./",
    build: {
        // Android 13's factory WebView is Chromium 109; aftermarket AI boxes
        // without Play Services may never update it, so never emit newer syntax.
        target: "chrome109",
        // dist/web/ mirrors the assets/web/ layout the Kotlin host expects; the
        // whole dist/ directory is wired into the Android assets source set.
        outDir: "dist/web",
        rollupOptions: {
            // One entry page for all backends; main.ts resolves ?backend= and
            // dynamic-imports the matching module, which Vite code-splits into
            // per-backend chunks.
            input: resolve(import.meta.dirname, "index.html"),
        },
    },
    test: {
        // style.ts is pure data transformation; no DOM environment needed.
        environment: "node",
        include: ["src/**/*.test.ts"],
    },
    lint: {
        // Oxlint settings live here, not in an .oxlintrc.json — `vp lint` /
        // `vp check` read only this block by default, and the Vite+ docs
        // recommend the single config home. Parity with the retired Biome
        // setup: the recommended preset maps to the correctness + suspicious
        // categories, noVar/useConst map to the eslint core rules below, and
        // the custom let ban moves from the Biome GritQL plugin (no-let.grit)
        // to the no-let.js Oxlint JS plugin (alpha API, dev-time only —
        // nothing from it ships).
        ignorePatterns: ["dist/**"],
        jsPlugins: ["./no-let.js"],
        categories: {
            correctness: "error",
            suspicious: "error",
        },
        // Type checking stays an explicit `tsc --noEmit` in the check script
        // (the same shape `vp migrate` generates). The alternative —
        // options.typeAware + options.typeCheck (tsgolint) — is coupled: the
        // type-aware RULE set comes with the type CHECK, and it outlaws the
        // deliberate boundary casts and defensive conversions the bridge code
        // is built on (e.g. no-unsafe-type-assertion on the window/UMD
        // accessors) — a stricter contract than the Biome-parity this
        // migration keeps. Enabling it is a deliberate future decision.
        rules: {
            "eslint/no-var": "error",
            "eslint/prefer-const": "error",
            "femto/no-let": "error",
        },
    },
});
