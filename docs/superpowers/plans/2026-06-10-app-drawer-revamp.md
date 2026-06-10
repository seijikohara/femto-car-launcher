# App Drawer Revamp Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pinned dock fixed at the drawer sheet bottom, S/M/L icon size presets in Settings, and search polish (prefix-priority ordering, auto-list layout).

**Architecture:** Persistence changes live in `data/DrawerPreferences.kt` (ordered pins, icon size enum). Pure ranking/layout functions live in a new `ui/drawer/AppDrawerSearch.kt` for JVM testability. `AppDrawerScreen` restructures into a Column with a fixed `PinnedDock` row. Settings wires `DrawerSettingsStore` into the existing ViewModel via a new `ChoiceRow`.

**Tech Stack:** Kotlin, Jetpack Compose (M3), DataStore Preferences, JUnit4 + Robolectric.

Spec: `docs/superpowers/specs/2026-06-10-app-drawer-revamp-design.md`

---

### Task 1: DrawerPreferences — ordered pins + icon size

**Files:**
- Modify: `app/src/main/java/io/github/seijikohara/femto/data/DrawerPreferences.kt`
- Test: `app/src/test/java/io/github/seijikohara/femto/data/DrawerPreferencesTest.kt`

- [ ] Add `DrawerIconSize { SMALL, MEDIUM, LARGE }` enum.
- [ ] Change `DrawerSettingsStore.pinned` to `Flow<List<String>>` (pin order), add `iconSize: Flow<DrawerIconSize>` and `setIconSize`.
- [ ] Extract migration as a top-level pure function:

```kotlin
internal fun resolvePinnedOrder(order: String?, legacy: Set<String>?): List<String> =
    order?.split(PIN_SEPARATOR)?.filter { it.isNotEmpty() }
        ?: legacy?.sorted()
        ?: emptyList()
```

`PIN_SEPARATOR = '\n'` (cannot appear in a flattened `ComponentName`). `togglePinned` reads the resolved list, appends/removes, writes `drawer_pinned_order`, and removes the legacy `drawer_pinned` key.

- [ ] Tests: pure-function tests for `resolvePinnedOrder` (order kept, legacy sorted fallback, empty default); extend the existing single Robolectric round-trip test to assert append order, unpin, icon size round-trip.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests '*DrawerPreferences*'` → PASS.
- [ ] Commit.

### Task 2: Search ranking + auto-list pure functions

**Files:**
- Create: `app/src/main/java/io/github/seijikohara/femto/ui/drawer/AppDrawerSearch.kt`
- Test: `app/src/test/java/io/github/seijikohara/femto/ui/drawer/AppDrawerSearchTest.kt`

- [ ] Implement:

```kotlin
internal fun <T> filterAndRank(items: List<T>, query: String, labelOf: (T) -> String): List<T>
// blank query → items unchanged; else startsWith matches (ignoreCase) first,
// then contains matches; both partitions keep input order.

internal fun effectiveLayout(persisted: DrawerLayout, query: String): DrawerLayout
// non-blank query → LIST, else persisted.
```

- [ ] JVM tests (no Robolectric): prefix beats substring, case-insensitive, stable order within partitions, blank query passthrough, auto-list selection.
- [ ] Run tests → PASS. Commit.

### Task 3: AppTile / AppListRow size parameters + pin badge

**Files:**
- Modify: `app/src/main/java/io/github/seijikohara/femto/ui/home/components/AppTile.kt`
- Modify: `app/src/main/java/io/github/seijikohara/femto/ui/home/components/AppListRow.kt`

- [ ] `AppTile(entry, onClick, modifier, onLongClick, iconSize: Dp = 64.dp, isPinned: Boolean = false)` — icon wrapped in a `Box`; when pinned, a small `Lucide.Pin` badge at `Alignment.TopEnd` on a `secondaryContainer` circle.
- [ ] `AppListRow(..., iconSize: Dp = 40.dp, isPinned: Boolean = false)` — pinned shows a trailing `Lucide.Pin` icon.
- [ ] Compile check via Task 5 build (callers update there). Commit with Task 5 if interim build breaks; otherwise commit now with default-arg compatibility.

### Task 4: PinnedDock component

**Files:**
- Create: `app/src/main/java/io/github/seijikohara/femto/ui/drawer/components/PinnedDock.kt`

- [ ] `PinnedDock(apps: List<AppEntry>, onLaunch, onUnpin, modifier)` — `HorizontalDivider` + `Surface(color = surfaceContainerHigh)` + `LazyRow` (stable keys). Tile: 64 dp icon + one-line 18 sp label, width ~96 dp, long-press opens an Unpin `DropdownMenu`. `@PreviewLightDark` preview.
- [ ] Commit with Task 5.

### Task 5: AppDrawerScreen restructure + AppDrawerSheet wiring

**Files:**
- Modify: `app/src/main/java/io/github/seijikohara/femto/ui/drawer/AppDrawerScreen.kt`
- Modify: `app/src/main/java/io/github/seijikohara/femto/ui/drawer/AppDrawerSheet.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] `AppDrawerScreen` signature: `pinned: List<String>`, new `iconSize: DrawerIconSize`.
- [ ] `ContentState`: Column = TopBar / content `Box(weight 1f)` / `PinnedDock` (only when pinned apps resolve non-empty; dock list = pinned order mapped through loaded apps, unaffected by query).
- [ ] Grid/list show ALL matched apps (no pinned section split, headers removed); per-item `isPinned` flag drives the badge. Remove `drawer_section_pinned` / `drawer_section_apps` strings.
- [ ] Use `filterAndRank` and `effectiveLayout(layout, query)`.
- [ ] Tile dimensions from preset (private to AppDrawerScreen.kt):

| Preset | tile min width | grid icon | list icon |
| --- | --- | --- | --- |
| SMALL | 96.dp | 48.dp | 32.dp |
| MEDIUM | 120.dp | 64.dp | 40.dp |
| LARGE | 160.dp | 88.dp | 56.dp |

- [ ] `AppDrawerSheet`: collect `pinned` as `List`, collect `iconSize`, pass through.
- [ ] Update previews. Build `assembleDebug` → green. Commit (Tasks 3+4+5 together if split builds red).

### Task 6: Settings integration

**Files:**
- Modify: `ui/settings/SettingsUiState.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/io/github/seijikohara/femto/ui/settings/SettingsViewModelTest.kt` (+ fake in `testfixtures/` if VM test exists)

- [ ] `SettingsUiState`: add `drawerIconSize: DrawerIconSize` (Initial = `MEDIUM`); add `SettingsAction.SetDrawerIconSize`.
- [ ] `SettingsViewModel`: inject `DrawerSettingsStore`, add its `iconSize` to the `combine`, handle the action; update `SettingsViewModelFactory`.
- [ ] `SettingsScreen`: `ChoiceRow` "App drawer icon size" (Small/Medium/Large) in the Display section. New strings.
- [ ] If `SettingsViewModelTest` exists: add `FakeDrawerSettingsStore` to `testfixtures/` and cover the new action.
- [ ] Run unit tests → PASS. Commit.

### Task 7: Verify, PR, merge

- [ ] `./gradlew spotlessApply` then verify via the `verify-android-build` skill (`assembleDebug` + `lint`).
- [ ] `./gradlew test` full pass.
- [ ] Dispatch `compose-launcher-reviewer` on the diff; address findings.
- [ ] Push branch, open PR (no Claude attribution), merge per user instruction.
