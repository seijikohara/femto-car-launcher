---
paths:
  - "docs/**"
---

# Docs site

Rules for `docs/`, the Astro source of the project website published
to GitHub Pages at `https://seijikohara.github.io/femto-car-launcher/`
by `.github/workflows/docs.yml` (push to `main`; `ci.yml`'s
`docs-site` job only proves the build on pull requests). The site
was scaffolded from the Astro Haze template v1.3.0 (MIT — notice kept
in `docs/LICENSE-astro-haze`); everything under `docs/src/` is ours
to edit.

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
- Content follows AGENTS.md#code-style (English) and the brand-neutral
  rule: no competitor or vendor product names.

## Toolchain

- Standalone pnpm project (`packageManager` pinned like `webmap/`);
  Node version in `docs/.node-version` (the same 24.x line the
  Gradle node plugin pins for the webmap build).
- `pnpm run check` = `astro check` + `tsc --noEmit` + Prettier check +
  oxlint + Vitest; `pnpm run build` = `astro build` + the dist link
  check. Run both before claiming a change is done (the
  [`verify-android-build`](../skills/verify-android-build/SKILL.md)
  skill lists them as its docs stage).
- Lint is oxlint (it lints `.astro` script blocks). Format is
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

## Catalog import contract (from PR-3)

The docs workflow hands `pnpm run import-catalog -- <dir>` a directory
containing `manifest.json` plus `img/<id>.png`, produced by
`./gradlew :app:generateCatalog`. The script converts to WebP under
`docs/public/catalog/` and is a no-op when the directory is missing,
so PR builds ship without a catalog.
