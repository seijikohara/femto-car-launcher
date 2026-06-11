---
paths:
  - "app/src/main/java/io/github/seijikohara/femto/data/fonts/**"
  - "app/src/main/java/io/github/seijikohara/femto/ui/fontpicker/**"
  - "app/src/main/java/io/github/seijikohara/femto/ui/theme/**"
  - "app/src/main/java/io/github/seijikohara/femto/MainActivity.kt"
---

# Fonts

Font rules for femto-car-launcher: the on-demand Google Fonts
pipeline and its theme wiring.

No fonts are bundled. The default is the head-unit **system font**
(`FontFamily.Default`); the user may instead pick any Google Fonts
family per slot, downloaded on demand and cached on disk.

- Two independent slots: a **Latin** face (alphanumerics / Western
  text) and a **CJK fallback** face (the multibyte fill for glyphs
  the Latin face lacks). `FontSelection(latinFamily, cjkFamily)` —
  a null family means the system font for that slot.
- `FontRepository` (app-scoped singleton, `data/`) is the SSOT: it
  loads the public Google Fonts catalog
  (`fonts.google.com/metadata/fonts`, no API key, no Play Services),
  downloads the chosen family's TTF from `fonts.gstatic.com` over
  `INTERNET`, caches it under `filesDir/google_fonts/<slug>/`, and
  **evicts the cache of families no longer selected** on every
  switch. Prefer the upright variable font (one file spans every
  weight and carries full CJK coverage); fall back to static weights.
- `FontCache` owns the on-disk layout (so a relaunch rebuilds the
  family offline with no network); `GoogleFontsApi` owns the
  network; `buildFontFamily(latin, cjk)` (theme) composes the
  resolved faces into one `FontFamily` (Latin first, CJK as
  fallback) that feeds `femtoTypography`.
- `FemtoTheme(fontFamily = ...)` takes the resolved family;
  `MainActivity` collects `FontRepository.resolved` so a freshly
  chosen face swaps in once its download lands.
- Picker UI lives under `ui/fontpicker/` (searchable full-catalog
  list, per-slot, with download progress); Settings exposes one row
  per slot.
- Variable-font weight axes require file-level
  `@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)`
  (see `ui/theme/DownloadedFonts.kt`).
- No fonts ship inside the APK. If a face is ever bundled again
  (e.g. an offline-guaranteed default), scope that as a new feature —
  the former bundling procedure was retired with the bundled fonts.
