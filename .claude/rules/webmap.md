---
paths:
  - "webmap/**"
---

# Webmap

Rules for `webmap/`, the TypeScript source of the LIVE map WebView
page. Dependency versions live in `webmap/package.json` +
`pnpm-lock.yaml` (the SSOT) — never restate version numbers here.
`app/src/main/assets/licenses/` holds the licenses of the bundled
map styles (Positron / Dark Matter) and MapLibre GL JS — keep it in
step with what the page bundles.

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
