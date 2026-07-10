---
name: add-viewmodel
description: Promote a Compose screen to the UDF (unidirectional data flow) shape — Route + Screen + ViewModel + UiState. Scaffolds the four-file shape; rules at .claude/rules/compose.md.
when_to_use: Promoting a screen to stateful — "add a ViewModel for X", "expose state from Y", "wire UiState to Z screen", "promote HomeScreen to stateful".
argument-hint: "[ScreenName] [package-area]"
allowed-tools:
  - Read
  - Write
  - Edit
  - Glob
  - Grep
  - Bash
  - Skill
paths:
  - app/src/main/java/io/github/seijikohara/femto/ui/**/*.kt
---

# Adding a ViewModel

Rules: `.claude/rules/compose.md`. The scaffold SSOT is
[references/viewmodel-template.md](references/viewmodel-template.md)
— read it at `${CLAUDE_SKILL_DIR}/references/viewmodel-template.md`.
Procedure SSOT is below.

When invoked manually as `/add-viewmodel <ScreenName> <area>`,
treat `$0` as the screen name and `$1` as the area. Without
arguments, prompt the caller for both values.

## Procedure

1. **Pick the area.** `app/src/main/java/io/github/seijikohara/femto/ui/<area>/`.
   The screen file already exists from the
   [`add-compose-screen`](../add-compose-screen/SKILL.md) skill.

2. **Create `<Area>UiState.kt`** from the template:
   - `internal data class <Area>UiState(...)` for state, with the
     initial value as `companion object { val Initial = ... }`.
   - `internal sealed interface <Area>Action { data object X : <Area>Action }`
     for events flowing up.

3. **Create `<Area>ViewModel.kt`** from the template — pick the
   shape per the template's variants:
   - **Primary — flow-derived state** (reference:
     `ui/home/HomeViewModel.kt`):

     ```kotlin
     val uiState: StateFlow<<Area>UiState> =
         combine(repoFlowA, repoFlowB) { a, b -> <Area>UiState(a, b) }
             .stateIn(viewModelScope, WhileUiSubscribed, <Area>UiState.Initial)
     ```

     Import `WhileUiSubscribed` from `data/common/FlowSharing.kt` —
     never an inline `WhileSubscribed(...)` literal
     (`.claude/rules/compose.md`).
   - **Alternative — action-driven state with no upstream flows**:
     `private val _uiState = MutableStateFlow(<Area>UiState.Initial)`
     exposed via `asStateFlow()` — never expose the mutable variant.
     Two sanctioned mutations: `_uiState.value = NewState` for
     whole-state replacement of a sealed `UiState` (shipped
     reference: `ui/drawer/AppDrawerViewModel.kt`), and
     `_uiState.update { it.copy(...) }` for partial mutation of a
     data-class `UiState` (atomic read-modify-write; shipped
     reference: `ui/drawer/AppDrawerViewModel.kt`).
   - Either way: single entry point `fun onAction(action: <Area>Action)`
     with a `when` over the sealed type. Constructor takes only what
     is testable in plain unit tests (repositories or flows from
     `data/<domain>/`, dispatchers, or plain function seams); avoid
     `Context` unless absolutely required (use `AndroidViewModel` if
     so).

4. **Refactor `<Area>Screen.kt`** to take state and callback:

   ```kotlin
   @Composable
   internal fun <Area>Screen(
       uiState: <Area>UiState,
       onAction: (<Area>Action) -> Unit,
       modifier: Modifier = Modifier,
   ) { ... }
   ```

   Update the existing `@PreviewLightDark` to pass
   `<Area>UiState.Initial` and `onAction = {}`.

5. **Add `<Area>Route.kt`** as the VM-binding entry. A VM with
   constructor dependencies (the normal case) gets a
   `viewModelFactory { initializer { ... } }` DSL value declared in
   the ViewModel file (shipped shape:
   `ui/drawer/AppDrawerViewModel.kt` — no `UNCHECKED_CAST`):

   ```kotlin
   internal val <Area>ViewModelFactory: ViewModelProvider.Factory =
       viewModelFactory {
           initializer {
               val application = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
               <Area>ViewModel(/* build dependencies from application */)
           }
       }
   ```

   The Route binds through it:

   ```kotlin
   val viewModel: <Area>ViewModel = viewModel(factory = <Area>ViewModelFactory)
   ```

   Bind via the factory per the template; pass a per-instance `key`
   for parameterised VMs (template Notes).

6. **Add a unit test** under `app/src/test/.../<area>/`. ViewModel
   logic is testable without Compose.

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
- Partial mutation through `_uiState.value = _uiState.value.copy(...)`
  — a read-modify-write race. Use `_uiState.update { it.copy(...) }`
  for partial mutation; `_uiState.value = NewState` is sanctioned
  only for whole-state replacement of a sealed `UiState`.
