---
name: add-viewmodel
description: Use when promoting a Compose screen to the UDF (unidirectional data flow) shape — Route + Screen + ViewModel + UiState. Triggers on "add a ViewModel for X", "expose state from Y", "wire UiState to Z screen", "promote HomeScreen to stateful". Scaffolds the four-file shape; rules at CLAUDE.md#compose-architecture.
argument-hint: "[ScreenName] [package-area]"
allowed-tools:
  - Read
  - Write
  - Edit
  - Glob
  - Grep
paths:
  - app/src/main/java/io/github/seijikohara/femto/ui/**/*.kt
---

# Adding a ViewModel

Rules: see `CLAUDE.md#compose-architecture` and
`CLAUDE.md#compose-performance`. The four-file scaffold SSOT is
[references/viewmodel-template.md](references/viewmodel-template.md).
Procedure SSOT is below.

When invoked manually as `/add-viewmodel <ScreenName> <area>`,
treat `$0` as the screen name and `$1` as the area. Without
arguments, prompt the caller for both values.

## Procedure

1. **Pick the area.** `app/src/main/java/io/github/seijikohara/femto/ui/<area>/`.
   The screen file already exists from the
   [`add-compose-screen`](../add-compose-screen/SKILL.md) skill.

2. **Create `<Area>UiState.kt`** from the template:
   - `data class <Area>UiState(...)` for state.
   - `sealed interface <Area>Action { data object X : <Area>Action }`
     for events flowing up.
   - Initial state as a `companion object` `Empty` / `Initial`
     value when natural.

3. **Create `<Area>ViewModel.kt`** from the template:
   - Extends `androidx.lifecycle.ViewModel`.
   - Holds `private val _uiState = MutableStateFlow(...)`; exposes
     `val uiState: StateFlow<<Area>UiState> = _uiState.asStateFlow()`
     — never expose the mutable variant.
   - Single entry point `fun onAction(action: <Area>Action)` with a
     `when` over the sealed type.
   - Constructor takes only what is testable in plain unit tests
     (repositories, dispatchers); avoid `Context` unless absolutely
     required (use `AndroidViewModel` if so).

4. **Refactor `<Area>Screen.kt`** to take state and callback:
   ```kotlin
   @Composable
   fun <Area>Screen(uiState: <Area>UiState, onAction: (<Area>Action) -> Unit) { ... }
   ```
   Update the existing `@PreviewLightDark` to pass a fake
   `<Area>UiState` and `onAction = {}`.

5. **Add `<Area>Route.kt`** as the VM-binding entry:
   ```kotlin
   @Composable
   fun <Area>Route(viewModel: <Area>ViewModel = viewModel()) {
       val uiState by viewModel.uiState.collectAsStateWithLifecycle()
       <Area>Screen(uiState = uiState, onAction = viewModel::onAction)
   }
   ```
   Callers (e.g. navigation graph or `MainActivity`) invoke
   `<Area>Route()`.

6. **Add a unit test** under `app/src/test/.../<area>/` once tests
   are wired up. ViewModel logic is testable without Compose.

7. **Verify** with the
   [`verify-android-build`](../verify-android-build/SKILL.md) skill.

## Skill-specific anti-patterns

- Exposing `MutableStateFlow` / `MutableSharedFlow` from the VM —
  always expose the read-only variant.
- Calling `.collectAsState()` instead of
  `.collectAsStateWithLifecycle()` in the Route.
- Mixing UI logic into the VM (e.g. `Color`, `Dp`, Compose types).
  The VM speaks UiState — the Screen translates state into UI.
- Putting `Context` in a VM constructor for non-Android reasons.
  If you need resources, pass a string resolver lambda or use
  `AndroidViewModel` deliberately.
- Two ViewModels for the same screen. One screen → one VM.
- Calling `viewModelScope.launch { ... }` without guarding against
  duplicate emissions. Use `_uiState.update { }` for state
  mutations to keep the read-modify-write atomic.
