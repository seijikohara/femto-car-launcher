---
paths:
  - "app/src/main/java/io/github/seijikohara/femto/**/*.kt"
---

# Compose architecture & performance

The official Compose architecture and performance docs are the
authoritative external SSOT; the bullets below capture
project-specific extensions. Where they differ, the project
convention wins.

## Architecture

- Authoritative reference:
  <https://developer.android.com/develop/ui/compose/architecture>.
- **Unidirectional data flow**: state flows down through
  `UiState`; events flow up through `(Action) -> Unit`.
- Three-Composable shape for stateful screens:
  - `<Area>Route` obtains the ViewModel internally —
    `viewModel(factory = <Area>ViewModelFactory)`, plus a
    per-instance `key` for parameterised VMs — and collects
    `StateFlow<UiState>` (never a `viewModel` parameter in the
    Route signature).
  - `<Area>Screen(uiState, onAction)` is pure UI — previewable,
    testable in isolation.
  - `<Area>ViewModel` exposes `StateFlow<UiState>` and a single
    `fun onAction(action: Action)`; never expose mutable state or
    lifecycle-aware fields.
- Trivial stateless screens need only `<Area>Screen.kt` (`Route`
  and `Screen` collapsed into one Composable); promote to the
  three-Composable shape on the first state addition.
- Every Composable that emits content takes `modifier: Modifier =
  Modifier` as the first non-state parameter and applies it before
  any internal modifiers. This is enforced by the Compose ktlint
  rule `compose:modifier-missing-check`.
- `FemtoTheme` is wrapped exactly once at the entry point
  (`MainActivity` for production, the preview block for previews).
  See `.claude/rules/design-system.md`.
- **Layering**: `data/` never imports `ui/`. A type a repository
  consumes (e.g. `MusicCommand`) lives in the repository's
  `data/<domain>/` package, not beside the Composable that emits it.
- `stateIn` / `shareIn` use the shared `WhileUiSubscribed` policy
  from `data/common/FlowSharing.kt` — never an inline
  `WhileSubscribed(...)` literal.
- Use the `add-compose-screen` skill to scaffold a new screen from
  the canonical template, and the `add-viewmodel` skill to scaffold
  the VM + UiState.

## Performance

- Authoritative reference:
  <https://developer.android.com/develop/ui/compose/performance>.
- Collect `Flow` in Composables with `collectAsStateWithLifecycle()`
  (`androidx.lifecycle:lifecycle-runtime-compose`), not the basic
  `.collectAsState()`.
- Provide stable `key` parameters to `LazyColumn` / `LazyRow`
  items so item identity survives reordering.
- Use `derivedStateOf` for derived state to suppress unnecessary
  recompositions.
- Strong skipping is the default (the Compose compiler ships with
  Kotlin since 2.0.20): restartable composables are skippable
  regardless of parameter stability, and composable lambdas are
  auto-remembered. Add `@Stable` / `@Immutable` only to give a
  wrapped non-stable type object equality instead of instance
  equality (e.g. a list re-allocated by a data source); never
  annotate speculatively. Reference:
  <https://developer.android.com/develop/ui/compose/performance/stability/strongskipping>.
- Heavy work goes in `LaunchedEffect`, `rememberCoroutineScope`,
  or the ViewModel — never directly in composition.
- Pass primitive parameters in preference to lambdas that capture
  outer state; if a lambda is unavoidable, hoist it to a stable
  reference with `remember`.
