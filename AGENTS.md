# Femto Car Launcher

Android home launcher for in-car displays across three device
classes — aftermarket CarPlay / Android Auto AI boxes, built-in
Android head units, and car-mounted smartphones. MVP targets Android 13 (API 33).

It is a regular Play-Store Android app installed on those devices — **not** an
OEM-embedded (Android Automotive) system app, and **not** an Android Auto /
CarPlay projection app (the "built-in Android head units" are aftermarket Android
units, not the car's factory system). In-car visibility and operability are the
design priority, but a **safe default the user can override** (e.g. the UI-scale
setting), not a hard mandate — see #automotive-overrides.

<!-- "multi-region distribution" is prose-cited by NominatimApi.kt. -->
The launcher is designed for **multi-region distribution**. No
single market is privileged in design, code, or documentation;
locale-specific behaviour is parameterised, and the strictest
applicable rule wins when markets diverge.

> **Rule locations.** AGENTS.md is the tool-agnostic project brief
> and rule SSOT for every coding agent (cite rules here as
> `AGENTS.md#<anchor>`); path-scoped rules live in
> `.claude/rules/*.md` (cite by file path; rule-file anchors are not
> addressable from outside). One home per rule; link, never restate.
> Tool-specific surface (Claude Code agents, skills, memory) lives in
> `CLAUDE.md`, which imports AGENTS.md.

## Tech stack <a id="tech-stack"></a>

- Kotlin (auto-applied by AGP), Jetpack Compose (via the BOM),
  Material 3; JDK 21 toolchain, Java 11 source/target. Versions:
  `gradle/libs.versions.toml` + `gradle/wrapper/gradle-wrapper.properties`
  (the JDK toolchain version itself is pinned in
  `gradle/gradle-daemon-jvm.properties`).
- `minSdk = 33`, `targetSdk = 36` with `compileSdk { release(37) }`
  (compile against API 37 as `androidx.core` 1.19+ requires; the
  supported-device floor stays Android 13 / API 33).
- Web map page (`webmap/`): TypeScript (native TS 7 compiler) +
  Vite+ (the `vp` CLI: build / test / oxlint / oxfmt) + `maplibre-gl` /
  `mapbox-gl`, managed with pnpm (pinned via `packageManager`). The
  Vite `build.target` is `chrome109` — the Android 13 factory WebView;
  aftermarket AI boxes may never update it, so never raise it
  without revisiting that floor (phone WebViews stay current, but
  the strictest device class governs). Rules: `.claude/rules/webmap.md`.

## Source layout

```
app/src/
├── main/
│   ├── java/io/github/seijikohara/femto/
│   │   ├── MainActivity.kt           # ComponentActivity, single launcher entry
│   │   ├── data/                     # One sub-package per domain (apps, calendar, clock, common, diagnostics, display, dock, fonts, geocoding, location, music, system, voice, weather); data/ never imports ui/
│   │   └── ui/
│   │       ├── <area>/               # One area per top-level surface
│   │       │   ├── <Area>Route.kt    # VM-binding entry point (when stateful)
│   │       │   ├── <Area>Screen.kt   # Pure UI; takes UiState + onAction
│   │       │   ├── <Area>UiState.kt  # data class + Action sealed (when stateful)
│   │       │   ├── <Area>ViewModel.kt# StateFlow<UiState>; handles Action
│   │       │   └── components/       # Area-private widgets
│   │       └── theme/                # FemtoTheme + tokens + PreviewLightDark
│   └── res/                          # themes (values{,-night}/), strings (per-locale once locales are wired up), icon drawables, xml/
├── test/...                          # JVM unit tests (runTest + TestDispatcher)
└── androidTest/...                   # Compose UI tests (createComposeRule)
```

`webmap/` (top level) is the TypeScript source of the LIVE map
WebView page; Gradle builds it into `assets/web/` via the
node-gradle plugin (`node {}` in `app/build.gradle.kts` is the
wiring SSOT; nothing under `src/main/assets/web/` is committed).
`gradle/libs.versions.toml` is the dependency catalog SSOT
(webmap npm deps: `webmap/package.json` + lockfile).

Trivial stateless screens need only `<Area>Screen.kt` — see
`.claude/rules/compose.md`.

## Rules

### Automotive overrides <a id="automotive-overrides"></a>

| Concern | M3 default | Femto rule | Symbol |
| --- | --- | --- | --- |
| Tap target | 48 dp | **≥ 64 dp** | `FemtoDimens.MinTouchTarget` |
| Body text on the head-unit dashboard | flexible | **≥ 16 sp** — the body floor sits on the rem-style type scale rooted at `FemtoDimens.BaseTextSize` (16 sp), from which every size derives; never `bodySmall` / `labelSmall`. Cards may deliberately relax this for glance metadata, metrics, progress captions, and dense reference text (e.g. license/log listings) — never as a literal in component code: the size lives in `FemtoDimens.GlanceTextSize` (12 sp) or inside a named `Type.kt` extension (e.g. `cardMeta`, `monoReference`). `ui/home/components/`, `ui/licenses/`, and `ui/diagnostics/` are the reference for where the relaxation applies (inherited from the retired dashboard-v2 mockup, whose KDoc notes mark each spot) | `FemtoDimens.MinBodyTextSize` / `FemtoDimens.GlanceTextSize` |

When the value lives in code, the symbol on the right is the SSOT —
not a magic number in another file.

These floors are the **safe default** (the `MEDIUM` UI scale), not a hard ceiling
on user choice. The user-selectable Display-size setting (`UiScale`,
`FemtoTheme(uiScale = ...)`) scales the whole UI through the density; its `SMALL`
option deliberately crosses below the floors as an explicit opt-in — sanctioned
because this ships as a general Play-Store app, mirroring Android's own font-size /
display-size controls. Author components to the floors at `MEDIUM`; the scale
applies on top.

### Launcher behavior <a id="launcher-behavior"></a>

- `MainActivity`: categories `HOME` + `DEFAULT` + `LAUNCHER`,
  `launchMode="singleTask"`, `stateNotNeeded="true"`.
- Orientation is **not** locked — landscape head units, portrait
  phone mounts, and everything between must all work.
- On a smartphone the launcher also runs as a regular app via
  `LAUNCHER`; default HOME is optional there — a phone is a shared,
  daily-use device, never assume the app owns it.
- Aftermarket AI boxes lock the default-launcher slot; the app
  launches via a host "boot-up app" hook (~30 s, outside our
  control). Cold start in-process is a key product metric — keep
  `MainActivity#onCreate` lean.

### Motion-state policy <a id="driving-lockout"></a>

The launcher renders the same dashboard tree regardless of vehicle
motion — there is **no project-wide driving-lockout gate**
(rationale persisted in memory). A feature with a clear, specific
distraction profile gates itself locally on motion or behind a
passenger toggle; there is no global gate to inherit. The
automotive floors above apply regardless of motion.

### Permissions

Every `<uses-permission>` follows the procedure in
`.claude/rules/permissions.md`, which is also the audit-log SSOT —
read the rule before touching `AndroidManifest.xml`.

### Code style <a id="code-style"></a>

- Source code, comments, docstrings, Markdown, commit messages,
  and PR text are written in **English**.
- Comments explain **why** when the why is non-obvious; never
  restate what the code already shows.
- No AI-attribution trailers or footers in commits, PRs, or issues
  (`Co-Authored-By: Claude`, "Generated with Claude Code", or any
  agent's equivalent).
- New screens use `@PreviewLightDark` for both light and dark modes.

### Suppression policy <a id="no-suppress"></a>

Fix warnings, deprecations, and lint findings at the source (migrate
the API, fix the code). Never `@Suppress` to silence them, never
baseline entries, never Spotless `suppressLintsFor`. Mechanical
compiler-required casts (`@Suppress("UNCHECKED_CAST")` in a
`ViewModelProvider.Factory`) are not finding-suppressions.

### SSOT / DRY <a id="ssot-dry"></a>

This rule applies to **all** generated artefacts: production code,
test code, docs, comments, scripts, fixtures, CI configuration.
Each fact lives in one place; other places cite the SSOT — they do
not restate it.

- **Project rules**: this file plus `.claude/rules/*.md`.
- **Code values**: the symbol (`FemtoDimens.X`,
  `MaterialTheme.colorScheme.X`) — never duplicate the literal.
- **Code shape** (screen / ViewModel scaffolds):
  `.claude/rules/compose.md` plus the living screens under `ui/` —
  model new code on an existing neighbour, never a canned template.
- **Procedures**: the procedure docs under `.claude/skills/` — cite
  them, never inline their steps.
- **Decision history**: the project memory (Claude Code-managed —
  see the Memory note in `CLAUDE.md`).
- **Test fixtures and helpers**: `.claude/rules/testing.md`.
- New fact or rule: find its existing home before creating one.

## Path-scoped rules index

Every agent must read the matching rule file **before** editing
files in its scope. Claude Code auto-loads the rule when a touched
file matches the rule's `paths:` frontmatter (the glob SSOT; the
scope column abbreviates it); agents without that mechanism read the
rule file manually. When in doubt, read them all.

| Rule file | Scope | Topic |
| --- | --- | --- |
| `.claude/rules/design-system.md` | `ui/**/*.kt`, `res/**` | FemtoTheme, Material You color, shapes, elevation, typography, previews |
| `.claude/rules/fonts.md` | `data/fonts/**`, `ui/fontpicker/**`, `ui/theme/**`, `MainActivity.kt` | Google Fonts slots, `FontRepository` SSOT, cache eviction, theme wiring |
| `.claude/rules/permissions.md` | `AndroidManifest.xml` | Permission procedure + the audit-log table |
| `.claude/rules/dependencies.md` | Gradle catalog + wrapper, `**/build.gradle.kts`, `settings.gradle.kts`, `webmap/package.json` + lockfile + workspace catalog | Version-catalog discipline, Compose BOM, lock-step rule, build-time endpoints |
| `.claude/rules/kotlin-style.md` | `app/src/**/*.kt` | Expression chains, naming, sanctioned language features, ktlint / Spotless wiring |
| `.claude/rules/compose.md` | `app/src/main/java/io/github/seijikohara/femto/**/*.kt` | UDF architecture, layering, `WhileUiSubscribed`, Compose performance |
| `.claude/rules/testing.md` | `app/src/test/**`, `app/src/androidTest/**` | JUnit 4 + `runTest`, Compose UI tests, `testfixtures/` |
| `.claude/rules/webmap.md` | `webmap/**` | Vite+ toolchain split, `build.target` floor, TS 7 constraints, pnpm pin |

## Build & verify

| Command | Purpose |
| --- | --- |
| `./gradlew assembleDebug` | Debug APK at `app/build/outputs/apk/debug/app-debug.apk` |
| `./gradlew lint` | Android Lint |
| `./gradlew test` | JVM unit tests |
| `./gradlew connectedAndroidTest` | Instrumented tests on device/emulator |
| `./gradlew spotlessCheck` | Format / lint check (Kotlin via ktlint, Gradle DSL, Markdown EOL) |
| `./gradlew spotlessApply` | Auto-fix format violations in place |
| `./gradlew versionCatalogUpdate` | Update `gradle/libs.versions.toml` to the latest stable versions (`nl.littlerobots.version-catalog-update`) |

Verify before claiming success: run the pipeline in
[`.claude/skills/verify-android-build/SKILL.md`](.claude/skills/verify-android-build/SKILL.md)
(spotlessCheck → assembleDebug → lint → test — the canonical
verification procedure) for non-trivial changes. For UI changes,
follow it with
[`.claude/skills/verify-on-emulator/SKILL.md`](.claude/skills/verify-on-emulator/SKILL.md)
to check the result on the TBox-Mock-Play AVD (definition committed
in the skill directory; `create-avd.sh` recreates it).

## Git conventions

- Conventional Commits for commit messages and PR titles (the PR
  title becomes the squash subject).
- A PR that resolves a user-filed issue posts a closing comment on
  that issue: one or two sentences of cause, and the first nightly
  that carries the fix. The rolling nightly is the only distribution
  channel, so the issue thread is where a reporter learns their fix
  shipped; a wordless keyword-close tells them nothing.
- Merges are rebase + squash; history on `main` stays linear.
  Force-push is denied; update a stale branch via the GitHub
  update-branch API (`gh pr update-branch`).
- The `Validate` status check gates every merge.
