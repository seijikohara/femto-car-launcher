---
paths:
  - "webmap/**"
---

# Webmap

Rules for `webmap/`, the TypeScript source of the map WebView pages
(one HTML entry point per backend — `map.html` for OSM/MapLibre,
`mapbox.html` for Mapbox, `googlemaps.html` for Google Maps).
Dependency versions live in `webmap/package.json` +
`pnpm-lock.yaml` (the SSOT) — never restate version numbers here.
`app/src/main/assets/licenses/` holds the license texts for the
bundled OSS map styles (Positron / Dark Matter) and MapLibre GL JS
(BSD-3-Clause) — keep it in step with what each page bundles under
an OSS license. Mapbox GL JS ships under Mapbox's own proprietary
Terms of Service rather than a redistributable OSS license (the end
user's own Mapbox account and token govern its use), so it has no
bundled license file here; a CDN-loaded library (e.g. the Google
Maps JavaScript API) likewise needs none.

## Credit placement

Every backend supplies its own authoritative credit (never overlay
one backend's onto another), and it sits in the **bottom-left**
corner wherever the backend's own ToS permits:

- **OSM/MapLibre**: the page renders no library attribution/logo;
  a native Compose `Attribution()` overlay draws the credit at
  `Alignment.BottomStart` (gated to OSM only via
  `showsNativeAttribution`).
- **Mapbox**: the wordmark (`logoPosition`) and the
  `AttributionControl` are both pinned `"bottom-left"` in
  `mapbox-main.ts`; ToS forbid hiding either, only moving them.
- **Google Maps**: the **one exception**. The Maps JS API fixes the
  Google logo bottom-left but the copyright / ToS text bottom-right
  and exposes no supported way to relocate either; the split stays
  as Google places it (any CSS against `.gm-style-cc` would violate
  the brand-feature terms).

## Toolchain split

- `tsc` is type-check-only: `tsc --noEmit` runs inside
  `pnpm run check`. Vite owns emit and ignores the tsconfig
  `target`.
- `build.target` in `vite.config.ts` is the sole shipped-syntax
  floor. Never raise it above the Android 13 factory-WebView floor
  (CLAUDE.md#tech-stack). A TypeScript compiler swap therefore
  structurally cannot move the floor.

## TypeScript 7 readiness

Readiness criteria, not an upgrade mandate. Never adopt a
pre-stable compiler preview package (e.g. native-compiler
previews) as a build dependency; adopt the native compiler only
when it ships stable under the `typescript` npm package, via the
[`update-gradle-dependency`](../skills/update-gradle-dependency/SKILL.md)
skill's webmap path.

- ESM-only (`"type": "module"` in `package.json`) with erasable
  TypeScript syntax: no enums (erasable-syntax rules bar regular
  enums, not only `const enum`), no runtime namespaces, no
  legacy / experimental decorators, no parameter properties, no
  CommonJS constructs (`require`, `module.exports`, `import =`,
  `export =`). Type-only imports use `import type`.
- Bundler-era module settings: ESNext-family `module`,
  `moduleResolution: "bundler"`, `isolatedModules`, `strict`.
- Treat TypeScript compiler deprecation warnings as failures
  during version bumps — warning-clean on the current bridge
  release is TS7 readiness.

## Package management

- pnpm is pinned via `packageManager` in `webmap/package.json`;
  bumps go through the
  [`update-gradle-dependency`](../skills/update-gradle-dependency/SKILL.md)
  skill.
