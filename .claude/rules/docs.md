---
paths:
  - "docs/**"
---

# Docs site

Rules for `docs/`, the Astro source of the project website published
to GitHub Pages at `https://seijikohara.github.io/femto-car-launcher/`
by `.github/workflows/docs.yml` (push to `main`; `ci.yml`'s
`docs-site` job proves the build on every CI run — pull requests and
`main` pushes alike — but never deploys). The site was scaffolded
from the Astro Haze template v1.3.0 (MIT — notice kept in
`docs/LICENSE-astro-haze`); everything under `docs/src/` is ours to
edit.

## Content SSOT

- README stays the short visitor introduction and links here; the
  long-form feature text lives only in `docs/src/content/features/`.
- `PRIVACY.md`, `TERMS.md`, `logo.svg` and the golden PNGs under
  `app/src/test/screenshots/` are imported in place (glob loader with
  `base: ".."`, relative imports); never copy them into `docs/`.
- Write internal links root-relative (`/install/`, `/terms/`) in
  content and through `withBase()` in components. Two Sätteri hast
  plugins run over every Markdown/MDX body: `satteri-repo-links`
  maps repository files (`PRIVACY.md` → `/privacy/`, anything else →
  GitHub), then `satteri-base-urls` prefixes the Pages base.
  `scripts/check-dist-links.ts` fails the build on a link that
  misses the base or resolves to nothing.
- The site origin and base path have one home: `docs/site.base.json`.
- Content follows AGENTS.md#code-style (English, brand-neutral: no
  competitor or vendor product names).

## Toolchain

- Standalone pnpm project (`packageManager` pinned like `webmap/`);
  Node version in `docs/.node-version` (the same 24.x line the
  Gradle node plugin pins for the webmap build).
- `pnpm run check` = `astro check` + `tsc --noEmit` + Prettier check +
  oxlint + Vitest; `pnpm run build` = `astro build` + the dist link
  check. Run both before claiming a change is done (the
  [`verify-android-build`](../skills/verify-android-build/SKILL.md)
  skill lists them as its docs stage).
- Lint is oxlint (it lints `.astro` script blocks; the `react` and
  `jsx-a11y` plugins cover the catalog viewer's JSX/TSX). Format is
  Prettier + `prettier-plugin-astro`, the one formatter for the whole
  directory — oxfmt has no Astro support (oxc docs, checked
  2026-09-20); revisit if it lands. Both follow the root
  `.editorconfig`.
- TypeScript stays on 5.x here: `@astrojs/check` requires 5.x/6.x
  (the webmap's TypeScript 7 pin is a separate project).
- `docs/dist/`, `docs/.astro/`, `docs/node_modules/` and
  `docs/public/catalog/` are build products (gitignored).
- Local loop: `pnpm --dir docs run dev` (served under
  `/femto-car-launcher/`), `pnpm --dir docs run preview` after a
  build.

## Screenshot catalog

- Input (from `./gradlew :app:generateCatalog`, PR-2): a directory
  with `manifest.json` (schema v1: `schemaVersion`, `generatedAt`,
  `gitSha`, `axes[{id,label,values[{id,label}]}]`,
  `entries[{id,file,widthDp,heightDp,values}]`) and `img/<id>.png`.
- `pnpm run import-catalog -- <dir>` (`scripts/import-catalog.ts`)
  writes `public/catalog/{manifest.json,full/<id>.webp,thumb/<id>.webp}`
  (lossy WebP q85 full size; 480 px q75 thumbnails) and adds `full`,
  `thumb`, `widthPx`, `heightPx` per entry plus `thumbWidth`. It is a
  no-op without a manifest (PR builds ship the page with its empty
  state) and fails when a manifest entry has no image (a partial render
  must not be published).
- The viewer is `src/catalog/`: `matrix.ts` (pure — state ↔ URL with
  `rows`, `cols`, one param per remaining axis and `open`; matrix
  layout; neighbour navigation; entries may be a subset of the axis
  product) and `CatalogViewer.tsx`, the site's only React island
  (`client:load`), which fetches `catalog/manifest.json` at runtime.
  `manifest.ts` re-exports the script's types so the shape has one
  home; `schema.ts` holds `CATALOG_SCHEMA_VERSION`, the schema-version
  constant shared by the import script and the island.
- `docs.yml` renders and imports the catalog before every deploy
  (~10–20 min); `public/catalog/` is never committed.

### Gotchas

- An island that rewrites the URL must pass `history.state` through
  to `replaceState` (never `null`) — the ClientRouter keeps its own
  navigation state there and ignores `popstate` when that state is
  `null`, which breaks the Back button site-wide, not just on the
  page that did it.
- A modal surface must be opaque. A translucent glass panel
  composited over the native `<dialog>`'s `::backdrop` scrim inverts
  light-theme contrast — dark-on-light text lands on a dark backdrop.
