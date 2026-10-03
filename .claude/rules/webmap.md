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
so a page only fetches the library it renders with. The camera-follow
policy (see Camera follow), chevron helpers (`src/chevron.ts`), and
bridge plumbing (`src/bridge.ts`) are one implementation across
backends.
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

An unreachable tile host does not fail the style load — the bundled
styles come from `appassets` and only their sources fail, and
MapLibre never re-fetches a failed TileJSON — so the page escalates
an error naming the tile host to a `fatal` when no tile has arrived
within the grace period (`src/load-outcome.ts`), whatever the number
of hosts. A page opened without data is the common case, not only a
dead mirror. The `fatal` is what makes the host reload the page, on
the next host when there is one; `liveReloadRetryDelayMsOrNull` in
`WebMapView.kt` owns the retry policy. Errors after a tile of the
current style has arrived stay log-only, never UI; a style swap
starts the judgement afresh, because it can re-create the vector
source and fetch its TileJSON again.

The fatal's kind tells the host whether a reload can help. The page
classifies every load failure in `src/load-outcome.ts`:
`isNetworkStatus` decides from MapLibre's `AJAXError` status whether
a request never reached its data or was refused, and
`initFailureDetail` keeps `backend-load-failed` for a map script
that could not be fetched. Status 0 covers every failure the WebView
cannot read, DNS failures and CORS-blocked answers included, so a
mistyped host name retries like an outage. The host mirrors the
kinds in `NetworkFailureKinds` and `RefusedFailureKinds`
(`WebMapView.kt`), and `FailureKindContractTest` guards the pair.
Only the network kinds retry without limit; a refused request, or an
exception once the map library has loaded, is a configuration
failure the host retries within its budget.

Two caveats follow from the rewrite. The origin the styles are
written against is a cross-language fact — `UPSTREAM_TILE_HOST`,
`MapScheme.kt`'s `OFM_STYLE_BASE`, and the bundled `map/*.json`
assets must name the same host, or the rewrite silently matches
nothing and a configured mirror is ignored with no error;
`TileHostContractTest` is the guard. And the native credit overlay
names the default providers: a host swap that serves data from
somewhere else makes that credit wrong, so a mirror is for the same
data under a different origin, not for different data.

## Credit placement

Every backend supplies its own authoritative credit (never overlay
one backend's onto another), and it sits in the **bottom-left**
corner wherever the backend's own ToS permits:

- **OSM/MapLibre, default provider**: the page renders no library
  attribution/logo; a native Compose `Attribution()` overlay draws
  the credit at `Alignment.BottomStart` (gated via
  `showsNativeAttribution`).
- **OSM/MapLibre, custom style URL**: the host cannot know what a
  user-supplied style draws on, so the native overlay is hidden and
  the page adds MapLibre's `AttributionControl` (bottom-left,
  non-compact — the credit must be readable, not folded behind an
  info button), which renders the `attribution` each source
  declares. `setStyleUrl`'s trailing flag carries the decision from
  `showsNativeAttribution`; the `page-attribution` class on `<body>`
  lifts the CSS that hides the control otherwise. Attribution links
  open in the system browser (`shouldOverrideUrlLoading`), never in
  the map WebView.
- **Google Maps**: the **one exception**. The Maps JS API fixes the
  Google logo bottom-left but the copyright / ToS text bottom-right
  and exposes no supported way to relocate either; the split stays
  as Google places it (any CSS against `.gm-style-cc` would violate
  the brand-feature terms).

## Light / dark

Both backends follow the host's resolved light/dark context (the Map
style setting, or the app theme on Auto), by different mechanisms:
the OSM page swaps or recolours its style through `setStyleUrl`
while the page lives, whereas Google's `colorScheme` is a
construction-time `MapOptions` value, so `WebMapView` keys the
WebView on `effectiveGoogleDark` and a flip rebuilds the Google page.
A Map ID's cloud style overrides the scheme only if a dark-mode style
is associated with it in the Cloud console (a 2025 addition); the
Map ID hint in Settings says so.

## Camera follow

The two backends run different engines — MapLibre's `easeTo` behind
`src/follow-camera.ts`, and for Google, whose `moveCamera` is
immediate, a per-frame glide (`src/camera-glide.ts`) — under one
policy:

- `src/camera.ts` is the one home of the follow policy — the motion
  choice (`followMotion`), the one-shot motions and their curve
  (`defaultEase`, MapLibre's default `easeTo` curve, ported), the map
  and chevron orientation (`followOrientation`), and the bearing
  smoothing — and `src/style.ts` of the chevron placement. A backend
  never keeps its own motion duration, curve, or heading rule.
- In heading-up, both maps turn to the smoothed bearing of every
  fix. Never hold the heading back in one backend: the Google-only
  heading dead band (#409, settled by #414) left the road and the
  arrow off vertical and was removed. A change to how the map heading
  follows the bearing (a speed gate, say) goes into `src/camera.ts`
  for both.
- The Google glide moves what `easeTo` moves — the location under
  the chevron and the chevron's screen offset, never the camera
  centre — and derives the centre every frame, so a rotation or zoom
  pivots on the chevron as on the OSM map.
- The Maps JS API has no camera padding, so a tilted Google vector
  map's perspective converges on the viewport centre. The page
  measures that perspective instead of assuming it: `src/lens.ts`,
  fed by a `WebGLOverlayView`'s coordinate transformer (the camera
  matrix the map draws with; Google allows the overlay only on a
  vector map with a Map ID) or, without a Map ID, by an
  `OverlayView`'s `MapCanvasProjection` — a probe that rides only on
  a map that renders vector (`syncLensProbe`) — recovers the focal
  length and camera distance from two probe points. From them it
  derives a yaw bias δ on the map heading, so the direction of travel
  runs straight up through the chevron at the OSM spot
  (`markerSpot`), and the exact ground offset under the chevron. δ is Google's stand-in for camera padding, not a heading
  rule: `followOrientation` stays the one follow-heading source, and
  the backend adds δ only where it talks to the map (`moveCamera`,
  the glide's read-back, the compass, which reports the travel
  heading). The lens corrects for the tilt the map shows, not the
  tilt requested, wherever Google clamps it (and works at the zoom the
  map shows past its ceiling). A measurement that visibly moves δ or
  the anchor (`lensMoved`: the lens appearing, a viewport resize) is a
  new lens generation the glide blends to over the reflow motion
  (`lensGen` in `CameraPose`), without waiting for a fix: the heading
  and the anchor glide, the chevron and its CSS transition stay put.
  Never hard-code a field of view. Both maps place the
  chevron by the one rule (`markerSpot`): until the lens is measured,
  or when the measurement is implausible, the Google chevron stays at
  that spot with no yaw and the flat offset (the road leans until the
  lens is measured) — never on the centre line. A chevron that changes
  spot glides in lockstep with the camera (`spotMotion`) with the
  reflow motion both backends use.
- A fix that arrives while the chevron still glides to a new spot (a
  reflow, or a Google spot move) leaves the chevron's transition
  armed and moves the camera over the time it has left
  (`followMotion`'s `reflowRemainingMs`, `markerTransitionStep`,
  `MarkerTransition.remainingMs`), so the chevron and the camera land
  together on both maps.
- A detached zoom step (the host's +/- button) zooms about the
  chevron's spot on both maps: MapLibre's `easeTo` keeps the padding
  and zooms about the padded centre; the Google glide holds the
  location under the spot as its anchor while the zoom changes.

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
