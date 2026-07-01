---
paths:
  - "app/src/main/java/io/github/seijikohara/femto/ui/**/*.kt"
  - "app/src/main/res/**"
---

# Design system

Design-system rules for femto-car-launcher UI; the automotive
floors live in `CLAUDE.md#automotive-overrides`.

Material You (Material 3) foundation, **Bold Minimal** aesthetic,
with automotive overrides on top.

- Stable Material 3 API surfaces only; verify a symbol's
  experimental status via `javap` against the resolved artifact,
  not release notes.
- Always wrap composables in `FemtoTheme { ... }`. Do not call
  `MaterialTheme(...)` directly outside `FemtoTheme.kt`.
- Color: Material You dynamic color
  (`dynamicLightColorScheme` / `dynamicDarkColorScheme`) is the
  **default** (`AccentColor.DYNAMIC`) — minSdk 33 guarantees
  `dynamic*ColorScheme` availability, so no `SDK_INT` branch is
  needed. The user may instead pick a fixed accent in Settings,
  which generates the Material 3 scheme from a preset seed
  (`AccentColor` + `accentSeedColor` + MaterialKolor's
  `rememberDynamicColorScheme`); `FemtoTheme(accent = ...)` selects
  between them. Either way, pull from `MaterialTheme.colorScheme.*`;
  the seed colors in `ui/theme/AccentColors.kt` and the curated
  weather-glyph palette in `ui/theme/WeatherGlyphColors.kt` (warm/cool
  distinctions dynamic color cannot produce) are the only hardcoded
  hex outside `Color.kt`.
- Shape: M3 default `Shapes` (squircles). Do not customise.
- Elevation: M3 standard — express surface hierarchy with the
  `surfaceContainer*` color roles (cards sit on `surfaceContainer`,
  nested emphasis on `surfaceContainerHigh`); do not pass a non-zero
  `tonalElevation` / `shadowElevation` to a `Surface` / `Card`. No
  dedicated elevation token exists.
- Typography: Bold Minimal on M3 roles, tuned **one weight notch
  lighter** than the original scale after on-device review found the
  heavy display/headline weights too dense on the head unit (display
  ExtraBold/Bold, headline SemiBold, title Medium; body Normal, label
  Medium — `ui/theme/Type.kt` is the SSOT). Use
  `MaterialTheme.typography.*` styles or the named `Typography`
  extensions in `Type.kt` (`bigNumber`, `heroNumeral`, `sectionLabel`,
  `eyebrow`, `calendarWeekday`, `glanceMetric`, `glanceBody`,
  `progressCaption`, `monoReference`, `cardTitle`, `cardMeta`,
  `cardCta`, `cardCtaHint`, `tileLabel`); never construct ad-hoc
  `TextStyle` literals — a recurring `.copy(fontSize = ...)` becomes a
  new named extension in `Type.kt`.
- Sizing: read from `FemtoDimens` (e.g. `FemtoDimens.MinTouchTarget`).
- Previews use `@PreviewLightDark` from `ui/theme/PreviewLightDark.kt` — never
  hand-write the light/dark `@Preview` pair. Additional single-mode
  geometry previews (`@Preview(name = ..., widthDp = ..., heightDp =
  ...)`) beside it are sanctioned responsive test cases: annotation
  classes cannot parameterise dimensions, and the geometries differ
  per component.
- `ui/theme/FitText.kt` is the SSOT for a single-line label that
  shrinks to fit its available width across locales and screen sizes
  (weekday names, track titles, metric values).
  `ui/theme/PreviewTextStress.kt` is a `@Preview` annotation bundling
  pseudolocale (`en-XA`, `ar-XB`) and large-font-scale cases; apply it
  alongside `@PreviewLightDark` on text-heavy components.
