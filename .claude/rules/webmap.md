---
paths:
  - "webmap/**"
---

# Webmap

Rules for `webmap/`, the TypeScript source of the map WebView page:
one entry point (`index.html`), whose `?backend=` query parameter
(`osm` / `googlemaps`, resolved in `src/backend-name.ts`)
selects the dynamically imported backend module under
`src/backends/` — Vite code-splits each backend into its own chunk,
so a page only fetches the library it renders with. The shared
camera-follow engine (`src/follow-camera.ts`), chevron helpers
(`src/chevron.ts`), and bridge plumbing (`src/bridge.ts`) are one
implementation across backends.
Dependency versions live in `webmap/package.json` +
`pnpm-lock.yaml` + `pnpm-workspace.yaml` (the Vite+ catalog; together
the SSOT) — never restate version numbers here.
`app/config/` (the AboutLibraries manual entries: `libraries/` plus
the licence bodies in `licenses/`) is the single home for the credit
and licence text of every non-Gradle component the page bundles or
the map draws on — MapLibre GL JS (BSD-3-Clause), the Google Maps JS
API loader (Apache-2.0), the OpenMapTiles Positron / Dark Matter
design the bundled styles derive from (BSD-3-Clause code, CC BY 4.0
design), OpenStreetMap data (ODbL 1.0), OpenFreeMap and Mapterhorn.
The bundle's transitive npm notices are generated at build time by
`scripts/third-party-notices.mjs` into `dist/web/` and read from the
web assets by the licences screen, which also lets the map and
weather credits open it in-app rather than a browser. A CDN-loaded
library (the Google Maps JavaScript API itself, fetched at runtime
by the bundled loader) needs none. Nothing under a proprietary
licence is bundled: the
Mapbox backend was removed in 2026-09 because Mapbox's Product Terms
require a purchased licence for any vehicle-related application,
which no bring-your-own-token arrangement satisfies.

## Tile hosts

The OSM page never hard-codes a live endpoint. It reads the tile
host, the terrain TileJSON and the initial style from the host's
synchronous bridge getters (`tileHost()`, `terrainTileJsonUrl()`,
`initialStyleUrl()` in `WebMapView.kt`) before constructing the map,
and MapLibre's `transformRequest` re-points every request under the
upstream origin (`UPSTREAM_TILE_HOST` in `src/style.ts`) at the
configured host — so the bundled styles and the hosted style URLs
stay written against the upstream layout and a mirror needs no style
rewriting. The Kotlin side owns the host list and its fallback order
(`.claude/rules/dependencies.md`). Outside the launcher (`vp dev`)
the getters are absent and the upstream defaults apply.

## Credit placement

Every backend supplies its own authoritative credit (never overlay
one backend's onto another), and it sits in the **bottom-left**
corner wherever the backend's own ToS permits:

- **OSM/MapLibre**: the page renders no library attribution/logo;
  a native Compose `Attribution()` overlay draws the credit at
  `Alignment.BottomStart` (gated to OSM only via
  `showsNativeAttribution`).
- **Google Maps**: the **one exception**. The Maps JS API fixes the
  Google logo bottom-left but the copyright / ToS text bottom-right
  and exposes no supported way to relocate either; the split stays
  as Google places it (any CSS against `.gm-style-cc` would violate
  the brand-feature terms).

## Toolchain split

The toolchain is Vite+ (`vite-plus`, the `vp` CLI): `vp build` owns
emit, `vp test` runs the bundled Vitest, and `vp check` runs oxfmt +
oxlint. All Vite+ configuration lives in `vite.config.ts` (build,
`test`, and `lint` blocks — the Vite+ docs deprecate separate
`.oxlintrc.json` files).

- `tsc` is type-check-only: `tsc --noEmit` runs inside
  `pnpm run check`. `vp build` owns emit and ignores the tsconfig
  `target`. (The tsgolint `typeAware`/`typeCheck` pair is a coupled
  future decision — see the comment in `vite.config.ts`.)
- `build.target` in `vite.config.ts` is the sole shipped-syntax
  floor. Never raise it above the Android 13 factory-WebView floor
  (AGENTS.md#tech-stack). A TypeScript compiler swap therefore
  structurally cannot move the floor.
- oxfmt follows the root `.editorconfig` (4-space indent — the
  repo-wide formatting SSOT the retired Biome config used to
  override with tabs).
- `let`/`var` are banned (const holders for mutable state): the
  eslint core rules plus the `femto/no-let` Oxlint JS plugin in
  `webmap/no-let.js`, wired via the `lint.jsPlugins` block.

## TypeScript 7 (native compiler)

The webmap type-checks with the native TypeScript 7 compiler — the
stable `typescript` npm package (adopted 2026-07, once 7.x shipped
as `latest`; version pin: `webmap/package.json`). Never adopt a
pre-stable compiler preview package as a build dependency; bumps go
through the
[`update-gradle-dependency`](../skills/update-gradle-dependency/SKILL.md)
skill's webmap path. The criteria below predate the switch as the
"TS7 readiness" rules and remain binding — they are what keeps the
sources native-compiler-clean:

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
