---
paths:
  - "app/src/main/java/io/github/seijikohara/femto/data/fonts/**"
  - "app/src/main/java/io/github/seijikohara/femto/ui/fontpicker/**"
  - "app/src/main/java/io/github/seijikohara/femto/ui/theme/**"
  - "app/src/main/java/io/github/seijikohara/femto/MainActivity.kt"
---

# Fonts

Font rules for femto-car-launcher: the on-demand Google Fonts
pipeline, the installed-font picker, and the theme wiring both feed.

No fonts are bundled. Each slot resolves to one of three sources —
the head-unit's **system font** (`FontFamily.Default`), a **Google
Fonts** family downloaded on demand and cached on disk, or a font
**already installed on the device**, resolved straight from its
on-disk file with no download and no cache entry. This is distinct
from the retired *bundled* fonts (shipped inside the APK) — an
installed font is never packaged with the app; it is enumerated at
runtime from whatever the head unit or phone already has.

- Two independent slots: a **Latin** face (alphanumerics / Western
  text) and a **CJK fallback** face (the multibyte fill for glyphs
  the Latin face lacks). `FontSource` (`SystemDefault` /
  `GoogleFonts(family)` / `SystemFont(familyName)`) is the per-slot
  choice; `FontSelection(latin, cjk)` holds one per slot.
  `FontSource.toPersisted()` / `.fromPersisted()` are the DataStore
  encoding — `google:<family>` / `system:<familyName>`, absent key =
  `SystemDefault`. An **unprefixed value is read as a legacy
  `GoogleFonts`**: every value written before this source existed
  was a bare Google Fonts family name, so an existing selection
  keeps resolving unchanged after the upgrade — never break this
  fallback branch.
- `FontRepository` (app-scoped singleton, `data/`) is the SSOT for
  resolving a `FontSelection` into on-disk faces:
  - `GoogleFonts`: loads the public Google Fonts catalog
    (`fonts.google.com/metadata/fonts`, no API key, no Play
    Services), downloads the chosen family's TTF from
    `fonts.gstatic.com` over `INTERNET`, and caches it under
    `filesDir/google_fonts/<slug>/`. Prefer the upright variable
    font (one file spans every weight and carries full CJK
    coverage); fall back to static weights.
  - `SystemFont`: looked up in `FontRepository.systemFonts` (loaded
    once at construction via `installedFontFamilies()`) and resolved
    directly to its files — no network, no cache write, no download
    progress.
  - **Cache eviction is Google-only**: `FontSelection.googleFamilies`
    (not the whole selection) feeds `FontCache.evictExcept`, so a
    `SystemFont` selection is never passed to it — those files live
    outside `filesDir/google_fonts/` and eviction must never touch
    them.
- `FontCache` owns the on-disk Google Fonts layout (so a relaunch
  rebuilds a family offline with no network); `GoogleFontsApi` owns
  the network; `buildFontFamily(latin, cjk)` (theme) composes the
  resolved faces into one `FontFamily` (Latin first, CJK as
  fallback) that feeds `femtoTypography` — it takes `CachedFont`
  regardless of source, so a `SystemFont`'s resolved `CachedFont.Static`
  needs no theme-side change.
- **Installed fonts** (`installedFontFamilies()`,
  `SystemFontCatalog.kt`): enumerates
  `android.graphics.fonts.SystemFonts.getAvailableFonts()` (API 29+,
  unconditional at minSdk 33), groups the files into families, and
  probes each family's Latin / CJK fitness.
  - **Naming**: Android exposes files, not a family catalog.
    `OpenTypeFontName` reads each file's OpenType 'name' table
    (typographic family, ID 16, else font family, ID 1; handles a
    bare sfnt and the first font of a `.ttc`); a file it cannot parse
    falls back to a cleaned filename. Both paths are best-effort —
    never crash on a weird file, skip it instead.
  - **Capability filter**: `GlyphCoverageChecker` builds one
    `Typeface` from the family's representative file (the file whose
    filename-guessed weight is closest to normal — see
    `weightFromFileName`, shared with `GoogleFontsApi`'s static-weight
    naming) and probes `Paint.hasGlyph` for representative characters
    (Latin needs both an upper- and lowercase letter; CJK needs only
    one of Han / Hiragana / Hangul, mirroring
    `GoogleFontFamily.supportsCjk`'s per-subset `any`). `SystemFontFamily.fits(slot)`
    mirrors `GoogleFontFamily.fits(slot)`.
  - Both the file enumeration (`SystemFontFileSource`) and the glyph
    probe (`GlyphCoverageChecker`) are seams — the same JVM-test
    pattern as `FontCatalogSource` / `FontFaceStore` — so grouping and
    fitness logic is unit-testable without Android graphics; only the
    real `Typeface` / `Paint` / `SystemFonts` calls need Android.
- `FemtoTheme(fontFamily = ...)` takes the resolved family;
  `MainActivity` collects `FontRepository.resolved` so a freshly
  chosen face swaps in once its download (or disk resolve) lands.
- `FontRepository.resolvedOnce` marks the first trustworthy
  resolution pass (success, fallback, or failure alike; a
  `SystemFont` selection additionally waits for the installed-font
  enumeration). `MainActivity` gates the splash screen on it — with
  a hard timeout so a cold-cache download can never hold the
  launch — so the typeface swap lands before the first visible
  frame instead of reflowing the dashboard after it.
- Picker UI lives under `ui/fontpicker/` (searchable full-catalog
  list, per-slot, with download progress) with a second "installed
  fonts" section/tab for the device's own fonts (no download
  progress — those resolve instantly); Settings exposes one row per
  slot, showing the chosen face's display name regardless of source
  (`FontSource.displayNameOrNull`).
- Variable-font weight axes require file-level
  `@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)`
  (see `ui/theme/DownloadedFonts.kt`).
- No fonts ship inside the APK. If a face is ever bundled again
  (e.g. an offline-guaranteed default), scope that as a new feature —
  the former bundling procedure was retired with the bundled fonts,
  and is unrelated to the installed-font source above (that reads
  fonts already on the device, never ships one).
