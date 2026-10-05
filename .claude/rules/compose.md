---
paths:
  - "app/src/main/java/io/github/seijikohara/femto/**/*.kt"
---

# Compose architecture & performance

The official Compose architecture and performance docs are the authoritative external SSOT; the bullets below capture project-specific extensions. Where they differ, the project convention wins.

## Architecture

- Authoritative reference: <https://developer.android.com/develop/ui/compose/architecture>.
- **Unidirectional data flow**: state flows down through `UiState`; events flow up through `(Action) -> Unit`.
- Three-Composable shape for stateful screens:
  - `<Area>Route` obtains the ViewModel internally — `viewModel(factory = <Area>ViewModelFactory)`, plus a per-instance `key` for parameterized VMs — and collects `StateFlow<UiState>` (never a `viewModel` parameter in the Route signature).
  - `<Area>Screen(uiState, onAction)` is pure UI — previewable, testable in isolation.
  - `<Area>ViewModel` exposes `StateFlow<UiState>` and a single `fun onAction(action: Action)`; never expose mutable state or lifecycle-aware fields.
  - It may expose an additional purpose-named `StateFlow` beside `UiState` when a signal must not wait for the `UiState` aggregation or must not replay stale. Four examples exist today, each paired with a policy from `data/common/FlowSharing.kt`: `HomeViewModel.online` (the map's reconnect reading); `HomeViewModel.updatePrompt` (never shown stale); `HomeViewModel.audioSpectrum` (a ~20 Hz stream kept out of `UiState` so only the spectrum canvas recomposes on it); and `VideoViewModel.pictureVisible` (the motion gate, judged afresh on every return).
  - Two further exposures are sanctioned outside this `StateFlow` pattern: a one-shot `events: SharedFlow<…Event>` for a navigation-style request a late collector must not replay (`HomeViewModel.events`), and `VideoViewModel.surfaceHost`, a narrow surface port for the player's `TextureView` rather than state.
- Trivial stateless screens need only `<Area>Screen.kt` (`Route` and `Screen` collapsed into one Composable); promote to the three-Composable shape on the first state addition.
- Every Composable that emits content takes `modifier: Modifier = Modifier` as the first non-state parameter and applies it before any internal modifiers. The Compose ktlint rule `compose:modifier-missing-check` enforces the parameter requirement.
- `FemtoTheme` is wrapped exactly once at the entry point (`MainActivity` for production, the preview block for previews). See `.claude/rules/design-system.md`.
- **Layering**: `data/` never imports `ui/`. A type a repository consumes (e.g. `MusicCommand`) lives in the repository's `data/<domain>/` package, not beside the Composable that emits it.
- `stateIn` / `shareIn` use a shared policy from `data/common/FlowSharing.kt` — `WhileUiSubscribed`, or `WhileUiSubscribedFresh` for state that must never show stale (it stops the upstream and drops the cached value as soon as the last subscriber leaves, with no grace) — never an inline `WhileSubscribed(...)` literal.
- New screens copy the shape of an existing area under `ui/` — the living code is the template (pick a stateless screen or a full Route/Screen/UiState/ViewModel quartet to match what you need).

## Performance

- Authoritative reference: <https://developer.android.com/develop/ui/compose/performance>.
- Collect `Flow` in Composables with `collectAsStateWithLifecycle()` (`androidx.lifecycle:lifecycle-runtime-compose`), not the basic `.collectAsState()`.
- Provide stable `key` parameters to `LazyColumn` / `LazyRow` items so item identity survives reordering.
- Use `derivedStateOf` for derived state to suppress unnecessary recompositions.
- Strong skipping is the default (the Compose compiler ships with Kotlin since 2.0.20): restartable composables are skippable regardless of parameter stability, and composable lambdas are auto-remembered. Add `@Stable` / `@Immutable` only to give a wrapped non-stable type object equality instead of instance equality (e.g. a list re-allocated by a data source); never annotate speculatively. Reference: <https://developer.android.com/develop/ui/compose/performance/stability/strongskipping>.
- Heavy work goes in `LaunchedEffect`, `rememberCoroutineScope`, or the ViewModel — never directly in composition.
- Pass primitive parameters in preference to lambdas that capture outer state; if a lambda is unavoidable, hoist it to a stable reference with `remember`.
