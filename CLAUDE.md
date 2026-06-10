# Femto Car Launcher

Android home launcher for car head units (aftermarket CarPlay /
Android Auto AI boxes and built-in Android units). MVP targets
Android 13 (API 33).

The launcher is designed for **multi-region distribution**. No
single market is privileged in design, code, or documentation;
locale-specific behaviour (language, font fallback, regulation,
billing) is parameterised, and the strictest applicable rule wins
when markets diverge.

> **This file is the project rule SSOT.** Skills and agents under
> `.claude/` reference this file rather than restating its rules. If
> a rule lives here, do not duplicate it elsewhere — link or cite the
> section anchor instead.

## Tech stack

- Kotlin (auto-applied by AGP 9), Jetpack Compose (BOM `2026.05.01`),
  Material 3.
- AGP 9.2.1, Gradle 9.5.1, JDK 21 toolchain, Java 11 source/target.
- `minSdk = 33`, `targetSdk = 36` with `compileSdk { release(37) }`
  (compile against API 37 as `androidx.core` 1.19+ requires; the
  supported-device floor stays Android 13 / API 33).
- AndroidX: `core-ktx`, `core-splashscreen`, `activity-compose`,
  `lifecycle-runtime-compose`, `lifecycle-viewmodel-compose`,
  `datastore-preferences`.
- Web map page (`webmap/`): TypeScript + Vite + `maplibre-gl`, managed
  with pnpm (pinned via `packageManager`). Vite's `build.target` is
  `chrome109` — the Android 13 factory WebView; aftermarket AI boxes
  may never update it, so never raise it without revisiting that floor.

## Source layout

```
app/src/
├── main/
│   ├── java/io/github/seijikohara/femto/
│   │   ├── MainActivity.kt           # ComponentActivity, single launcher entry
│   │   ├── data/                     # Repositories, DataStore stores, network APIs (flat package)
│   │   └── ui/
│   │       ├── <area>/               # One area per top-level surface
│   │       │   ├── <Area>Route.kt    # VM-binding entry point (when stateful)
│   │       │   ├── <Area>Screen.kt   # Pure UI; takes UiState + onAction
│   │       │   ├── <Area>UiState.kt  # data class + Action sealed (when stateful)
│   │       │   ├── <Area>ViewModel.kt# StateFlow<UiState>; handles Action
│   │       │   └── components/       # Area-private widgets
│   │       └── theme/                # FemtoTheme + tokens + PreviewLightDark
│   └── res/                          # themes.xml (values{,-night}/), strings (per-locale)
├── test/...                          # JVM unit tests (runTest + TestDispatcher)
└── androidTest/...                   # Compose UI tests (createComposeRule)
```

`webmap/` (top level) is the TypeScript source of the LIVE map WebView
page. Gradle builds it into `assets/web/` via the node-gradle plugin —
the `node {}` block in `app/build.gradle.kts` is the wiring SSOT, and
nothing under `src/main/assets/web/` is committed. `assets/licenses/`
holds the licenses of the bundled map styles (Positron / Dark Matter)
and MapLibre GL JS.
`gradle/libs.versions.toml` is the dependency catalog SSOT (the web
page's npm dependencies live in `webmap/package.json` + lockfile).

Trivial screens without ViewModel state need only `<Area>Screen.kt`;
the other files appear when state arrives. See
`CLAUDE.md#compose-architecture`.

## Rules

Each rule has an anchor ID. Skills and agents cite them as
`CLAUDE.md#<anchor>`.

### Design system <a id="design-system"></a>

Material You (Material 3) foundation, **Bold Minimal** aesthetic,
with automotive overrides on top.

- Always wrap composables in `FemtoTheme { ... }`. Do not call
  `MaterialTheme(...)` directly outside `FemtoTheme.kt`.
- Color: Material You dynamic color
  (`dynamicLightColorScheme` / `dynamicDarkColorScheme`) is the
  **default** (`AccentColor.DYNAMIC`). The user may instead pick a
  fixed accent in Settings, which generates the Material 3 scheme from
  a preset seed (`AccentColor` + `accentSeedColor` + MaterialKolor's
  `rememberDynamicColorScheme`); `FemtoTheme(accent = ...)` selects
  between them. Either way, pull from `MaterialTheme.colorScheme.*`;
  the seed colors in `ui/theme/AccentColors.kt` are the only hardcoded
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
  `MaterialTheme.typography.*` styles; never construct ad-hoc
  `TextStyle` literals.
- Sizing: read from `FemtoDimens` (e.g. `FemtoDimens.MinTouchTarget`).
- Previews use `@PreviewLightDark` from `ui/theme/PreviewLightDark.kt` — never
  write the two `@Preview` annotations by hand.

### Automotive overrides <a id="automotive-overrides"></a>

| Concern | M3 default | Femto rule | Symbol |
| --- | --- | --- | --- |
| Tap target | 48 dp | **≥ 64 dp** | `FemtoDimens.MinTouchTarget` |
| Body text on the head-unit dashboard | flexible | **≥ 18 sp**, never `bodySmall` / `labelSmall`. Cards may deliberately relax this for glance metadata, metrics, and progress captions; the shipped card components under `ui/home/components/` are the reference for where the relaxation applies (inherited from the retired dashboard-v2 mockup, whose KDoc notes mark each spot) | `FemtoDimens.MinBodyTextSize` |

When the value lives in code, the symbol on the right is the SSOT —
not a magic number in another file.

### Fonts <a id="fonts"></a>

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

### Launcher behavior <a id="launcher-behavior"></a>

- `MainActivity` is registered with categories `HOME`, `DEFAULT`,
  and `LAUNCHER`, `launchMode="singleTask"`,
  `stateNotNeeded="true"`.
- Orientation is **not** locked — both portrait and landscape head
  units must work.
- Aftermarket AI boxes lock the default-launcher slot; the app
  launches via a host-side "boot-up app" hook with a ~30 s delay. That
  is outside our control, but cold start inside the process is a key
  product metric — keep `MainActivity#onCreate` lean.

### Motion-state policy <a id="driving-lockout"></a>

The launcher renders the same dashboard tree regardless of vehicle
motion. There is **no project-wide driving-lockout gate**.

- Passengers commonly operate the head unit; gating the whole UI on
  motion punishes the passenger for the driver being in motion.
- The OEM cluster owns vehicle-state information (speed, RPM, fuel,
  warnings). The launcher's role is the head-unit shell, not a
  redundant driver display.
- Comparable aftermarket car launchers ship the same posture today.

If a future feature has a clear and specific distraction profile
(e.g., embedded video playback inside the launcher itself, a chat
composer with multi-line input), that feature gates itself locally
on motion or behind a passenger toggle. There is no global gate to
inherit from. The previous `gate-driving-visible-feature` skill,
`DrivingStateRepository`, and `rememberDrivingLockState()`
Composable have all been removed in line with this policy.

`FemtoDimens.MinBodyTextSize` (18 sp) and `FemtoDimens.MinTouchTarget`
(64 dp) stay — they express glance-readability and tap-accuracy
targets that matter for any head-unit UI, regardless of motion.

### Permissions <a id="permissions"></a>

- Every `<uses-permission>` is added through the
  [`add-launcher-permission`](.claude/skills/add-launcher-permission/SKILL.md)
  skill.
- Adding any permission requires a one-line justification in the
  commit message body.
- The audit log below lists every declared permission with its
  one-line justification, alphabetised. Keep it in sync with
  `AndroidManifest.xml`.

| Permission | Justification |
| --- | --- |
| `ACCESS_COARSE_LOCATION` | Paired with `ACCESS_FINE_LOCATION` per the Android 12+ runtime model — users may grant only coarse. The dashboard panels accept either precision and render with degraded precision when only coarse is granted. |
| `ACCESS_FINE_LOCATION` | Centre the head-unit map on the user's position, derive the speed / altitude / address overlays, and locate the user for weather lookups. Required at runtime; the dependent panels render empty until the permission is granted. |
| `ACCESS_NETWORK_STATE` | MapLibre connectivity probe before fetching OpenFreeMap vector map tiles. |
| `ACCESS_WIFI_STATE` | Read Wi-Fi transport / validation state so the footer status cluster reports a live Wi-Fi indicator. Normal protection; auto-granted at install. |
| `BLUETOOTH_CONNECT` | Read the set of currently-connected Bluetooth devices (HEADSET / A2DP / GATT) so the footer status cluster reflects head-unit pairing state. Dangerous on Android 12+; runtime grant. When denied, the connected-device APIs are unreadable, so the BT indicator falls back to the adapter power state (on/off) rather than a misleading "disconnected"; the rest of the launcher remains functional. |
| `INTERNET` | Open-Meteo weather API, OpenFreeMap vector map tile fetch (MapLibre), Nominatim/OSM reverse geocoding, the optional live-map terrain layer (Mapterhorn DEM tiles), and the on-demand Google Fonts catalog + TTF download (`fonts.google.com` / `fonts.gstatic.com`, no API key, no Play Services). |
| `READ_CALENDAR` | Query `CalendarContract.Instances` for the dashboard's Calendar card — the 6-day strip dots and the upcoming-event list. Dangerous; runtime grant. The card falls back to "today's date only" when denied. |
| `READ_PHONE_STATE` | Read the cellular `SignalStrength.level` via `TelephonyCallback` so the footer status cluster shows graduated cellular signal bars. Dangerous; runtime grant. The cellular indicator degrades to the binary connected/disconnected icon when denied. |
| `RECORD_AUDIO` | Capture speech for the in-launcher voice assistant (`android.speech.SpeechRecognizer`) so the user talks to the assistant without leaving the launcher. Dangerous; requested at runtime only when the user taps the mic, never at startup. When denied — or when no on-device recognizer is available — the assistant sheet degrades to the system-intent delegation rows. |

### Dependencies <a id="dependencies"></a>

- All dependencies and plugins are declared in
  `gradle/libs.versions.toml` first, then referenced via `libs.*`
  aliases. No raw `implementation("...")` strings in module
  `build.gradle.kts` files.
- Compose dependencies go through the BOM. Overriding a single
  Compose artifact's version requires a justification in the commit
  body.
- The Kotlin version and the
  `org.jetbrains.kotlin.plugin.compose` plugin version move in
  lock-step.
- See the
  [`update-gradle-dependency`](.claude/skills/update-gradle-dependency/SKILL.md)
  skill for the procedure.

### Code style <a id="code-style"></a>

- Source code, comments, docstrings, Markdown, commit messages,
  and PR text are written in **English** (echoes the global
  `~/.claude/CLAUDE.md`).
- Comments explain **why** when the why is non-obvious. Comments
  that describe **what** the code already shows are not added.
- Do not add `Co-Authored-By: Claude` or "Generated with Claude
  Code" trailers / footers (echoes the global rule).
- New screens use `@PreviewLightDark` for both light and dark modes.

## Coding standards

The project follows the latest **official Kotlin and Android
guidelines**. The links below are the authoritative external SSOT;
the bullets capture project-specific extensions or sharpenings of
those guidelines. Where the official guideline and a project
convention differ, the project convention wins for this repo.

### Kotlin style <a id="kotlin-style"></a>

- Authoritative reference: <https://kotlinlang.org/docs/coding-conventions.html>.
- **Prefer expression chains over statement blocks.** Use
  single-expression function bodies (`fun foo() = bar()`),
  expression `when` and `if` (consume the result rather than
  branching as a statement), and chained calls (`?.`, `?:`,
  `let` / `run` / `also` / `apply`, `runCatching {}.onFailure {}`,
  collection chains) in preference to intermediate `val`
  declarations or imperative blocks. Reach for a block body only
  when the function genuinely runs unrelated side effects in
  sequence (`onCreate`, `init`, lifecycle callbacks).
- When every branch of a `when` is a single expression, omit the
  `{}`: `branch -> doIt()`. ktlint enforces a consistency rule that
  wraps **every** branch of a `when` in `{}` as soon as one branch
  spans multiple lines (e.g. a function call with named arguments
  on separate lines). When that happens, accept the block form
  rather than fighting the formatter — flatten the offending call
  to a single line if the goal is the unwrapped style. The
  `multiline-expression-wrapping` ktlint rule is **disabled** in
  `.editorconfig` so single-line branches stay unwrapped where
  ktlint's consistency rule does not interfere.
- Trailing commas in multi-line argument and parameter lists, in
  `when` branches, and in collection literals.
- `enum.entries` (Kotlin 1.9+) instead of the deprecated `.values()`.
- `internal` for module-internal API; `private` for class- or
  file-internal API. Public API of `app` is intentionally narrow —
  prefer `internal` until export is needed.
- Default arguments over overloads; named arguments at call sites
  for booleans and numeric flags.
- Scope functions (`let`, `also`, `apply`, `run`, `with`) are used
  for readability, not brevity. If two readers disagree on which
  scope function fits, prefer an explicit local `val`.
- File-level `@OptIn(ExperimentalXxx::class)` for experimental
  APIs. Never module-level opt-in via `freeCompilerArgs`.
- Top-level `private val Foo = ...` for file-private constants.
  `UPPER_SNAKE_CASE` only for `const val` or `companion object`
  public constants.
- Sealed hierarchies for closed Action / Event types; data classes
  for value containers; `data object` for stateless markers.
- Immutability: prefer `val` and read-only collections (`List<T>`,
  `Map<K, V>`) over mutable variants in public API.

### Compose architecture <a id="compose-architecture"></a>

- Authoritative reference:
  <https://developer.android.com/develop/ui/compose/architecture>.
- **Unidirectional data flow**: state flows down through
  `UiState`; events flow up through `(Action) -> Unit`.
- Three-Composable shape for stateful screens:
  - `<Area>Route(viewModel = viewModel())` wires the VM and
    collects `StateFlow<UiState>`.
  - `<Area>Screen(uiState, onAction)` is pure UI — previewable,
    testable in isolation.
  - `<Area>ViewModel` exposes `StateFlow<UiState>` and a single
    `fun onAction(action: Action)`; never expose mutable state or
    lifecycle-aware fields.
- Trivial stateless screens may collapse `Route` and `Screen` into
  one Composable; promote to the three-Composable shape on the
  first state addition.
- Every Composable that emits content takes `modifier: Modifier =
  Modifier` as the first non-state parameter and applies it before
  any internal modifiers. This is enforced by the Compose ktlint
  rule `compose:modifier-missing-check`.
- `FemtoTheme` is wrapped exactly once at the entry point
  (`MainActivity` for production, the preview block for previews).
  See `CLAUDE.md#design-system`.
- Use the `add-viewmodel` skill to scaffold the VM + UiState.

### Compose performance <a id="compose-performance"></a>

- Authoritative reference:
  <https://developer.android.com/develop/ui/compose/performance>.
- Collect `Flow` in Composables with `collectAsStateWithLifecycle()`
  (`androidx.lifecycle:lifecycle-runtime-compose`), not the basic
  `.collectAsState()`.
- Provide stable `key` parameters to `LazyColumn` / `LazyRow`
  items so item identity survives reordering.
- Use `derivedStateOf` for derived state to suppress unnecessary
  recompositions.
- Trust the Compose 2 stability inference (Kotlin 2.x compiler).
  Add `@Stable` / `@Immutable` only when wrapping a non-stable
  type. Never apply these annotations speculatively.
- Heavy work goes in `LaunchedEffect`, `rememberCoroutineScope`,
  or the ViewModel — never directly in composition.
- Pass primitive parameters in preference to lambdas that capture
  outer state; if a lambda is unavoidable, hoist it to a stable
  reference with `remember`.

### Testing <a id="testing"></a>

- Authoritative reference:
  <https://developer.android.com/training/testing>.
- JVM unit tests in `app/src/test/...`: JUnit 4 today (room to
  migrate to Kotest later). Async code uses `runTest` from
  `kotlinx-coroutines-test` plus an injected `TestDispatcher`.
- Compose UI tests in `app/src/androidTest/...`: use
  `createComposeRule()`. Wrap content in `FemtoTheme { ... }` —
  never an ad-hoc `MaterialTheme`.
- Test data lives in `testfixtures/` packages (one per source
  set). Builders / factories are the SSOT for test data; the
  `data class FakeFoo(...)` literal in a single test file is a
  finding the second time it appears.
- One assertion focus per test; descriptive names (`returns_x_when_y`).
- Parameterised tests for repeated cases.
- ViewModels expose `StateFlow`; tests collect with
  `viewModel.uiState.test { ... }` (Turbine) or with explicit
  `runCurrent()` calls.

### SSOT / DRY <a id="ssot-dry"></a>

This rule applies to **all** generated artefacts: production code,
test code, Markdown docs, comments, scripts, fixtures, and CI
configuration. Each fact lives in one place; other places link or
cite the SSOT — they do not restate it.

- The SSOT for **project rules** is this file.
- The SSOT for **code values** is the symbol (`FemtoDimens.X`,
  `MaterialTheme.colorScheme.X`). Never duplicate the literal in
  another file.
- The SSOT for **code shape** (screen scaffold, ViewModel
  scaffold) is the template under
  `.claude/skills/<name>/references/`. Generated code copies from
  the template; future template changes flow through.
- The SSOT for **procedures** is the skill under
  `.claude/skills/`. Other skills cite that skill rather than
  inlining its steps.
- The SSOT for **decision history** is the memory under
  `~/.claude/projects/<...>/memory/`. CLAUDE.md does not duplicate
  decision rationale.
- The SSOT for **test fixtures and helpers** is a single
  `testfixtures/` package per source set
  (`app/src/test/.../testfixtures/`,
  `app/src/androidTest/.../testfixtures/`). Repeat-yourself in test
  setup is the same kind of debt as in production.
- When a new fact or rule appears: ask first whether it already has
  a home. Only create a new entry when it does not.

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

The
[`verify-android-build`](.claude/skills/verify-android-build/SKILL.md)
skill is the canonical verification procedure — use it after
non-trivial changes. Spotless is configured at the project root
(`build.gradle.kts`) for `*.gradle.kts` and `*.md` and at `:app`
(`app/build.gradle.kts`) for `*.kt`. ktlint runs with the official
code style plus the `io.nlopez.compose.rules:ktlint` Compose rule
set. The `.editorconfig` at the project root is the configuration
SSOT for both ktlint and Android Studio.

## Claude Code surface

Project-local automation lives in `.claude/`. Read the relevant
file before working in its area.

### Subagents (`.claude/agents/`)

| Agent | When to use |
| --- | --- |
| `compose-launcher-reviewer` | After touching `ui/theme`, `ui/home`, `MainActivity`, manifest, build files, or fonts; before opening a PR. Pass the diff explicitly. |
| `similar-app-researcher` | Before scoping any feature, to study how comparable car launchers and prior-art projects solve the same problem. |

### Skills (`.claude/skills/`)

Auto-trigger when their description matches the situation **and**
manually invocable as `/<skill-name>`. Manual-only skills are marked
`disable-model-invocation: true`.

| Skill | Purpose |
| --- | --- |
| `add-compose-screen` | Scaffold a new Compose screen. |
| `add-viewmodel` | Promote a screen to the UDF `Route` + `Screen` + `ViewModel` shape. |
| `add-launcher-permission` | Discipline for new `<uses-permission>` entries. |
| `update-gradle-dependency` | Version-catalog discipline. |
| `update-launcher-icon` | Re-derive the adaptive launcher icon from `/logo.svg` (the brand-mark SSOT). |
| `verify-android-build` | Build + lint verification. |
| `build` | **Manual.** `/build [task]` — runs `./gradlew <task>`. |
| `lint` | **Manual.** `/lint [task]` — Android Lint with parsed summary. |
| `format` | **Manual.** `/format` — runs `./gradlew spotlessApply` and reports the diff. |
| `review` | **Manual.** `/review [git-range]` — dispatches `compose-launcher-reviewer` on the diff. |

### Settings (`.claude/`)

- `settings.json` (committed) — permission allowlist for safe Bash
  and WebFetch surfaces, plus a deny list for destructive Git ops
  and secret files.
- `settings.local.json` (gitignored) — your per-machine overrides.

## Memory

Persisted decisions (design direction, competitive research, etc.)
live at
`~/.claude/projects/-Users-seiji-git-GitHub-seijikohara-femto-car-launcher/memory/`.
Read it before re-litigating settled choices. Update it when a
durable decision is made.
