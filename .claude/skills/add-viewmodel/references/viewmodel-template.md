# ViewModel + UiState scaffold

Shape SSOT for the UDF pattern: UiState, ViewModel (+ factory), and
Route. Replace `<Area>` and `<area>` placeholders. Do not change the
structure (read-only exposed `StateFlow`, single `onAction` entry,
`Route` separate from `Screen`) without a documented reason. Rule:
`.claude/rules/compose.md`. The `Screen` body shape lives in
[../../add-compose-screen/references/screen-template.md](../../add-compose-screen/references/screen-template.md)
— only the signature delta is shown here.

## `<Area>UiState.kt`

```kotlin
package io.github.seijikohara.femto.ui.<area>

// Strong skipping covers stability — annotate @Stable/@Immutable
// only per .claude/rules/compose.md.
internal data class <Area>UiState(
    val isLoading: Boolean = false,
    val items: List<String> = emptyList(),
) {
    companion object {
        val Initial: <Area>UiState = <Area>UiState()
    }
}

internal sealed interface <Area>Action {
    data object Refresh : <Area>Action

    data class Select(
        val id: String,
    ) : <Area>Action
}
```

## `<Area>ViewModel.kt` — primary shape (flow-derived state)

Use when the state derives from repository flows — most shipped
VMs do (reference: `ui/home/HomeViewModel.kt`).

```kotlin
package io.github.seijikohara.femto.ui.<area>

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.seijikohara.femto.data.common.WhileUiSubscribed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

internal class <Area>ViewModel(
    // Inject repositories / flows / dispatchers. Keep the VM
    // testable in a plain JVM unit test — no Android types.
    private val itemsFlow: Flow<List<String>>,
    private val loadingFlow: Flow<Boolean>,
) : ViewModel() {
    val uiState: StateFlow<<Area>UiState> =
        combine(itemsFlow, loadingFlow) { items, isLoading ->
            <Area>UiState(isLoading = isLoading, items = items)
        }.stateIn(viewModelScope, WhileUiSubscribed, <Area>UiState.Initial)

    fun onAction(action: <Area>Action) =
        when (action) {
            <Area>Action.Refresh -> refresh()
            is <Area>Action.Select -> select(action.id)
        }

    private fun refresh() {
        // ... delegate to the repository; the flows above carry the result back
    }

    private fun select(id: String) {
        // ... handle selection
    }
}
```

`WhileUiSubscribed` comes from `data/common/FlowSharing.kt` — never
an inline `WhileSubscribed(...)` literal
(`.claude/rules/compose.md`).

### Variant — when state has no upstream flows

Action-driven state: `private val _uiState = MutableStateFlow(...)`
exposed via `asStateFlow()`. Two sanctioned mutations:

- `_uiState.value = NewState` — whole-state replacement of a sealed
  `UiState` (shipped reference: `ui/drawer/AppDrawerViewModel.kt`):

  ```kotlin
  private val _uiState = MutableStateFlow<<Area>UiState>(<Area>UiState.Loading)
  val uiState: StateFlow<<Area>UiState> = _uiState.asStateFlow()

  private fun refresh() {
      _uiState.value = <Area>UiState.Loading
      viewModelScope.launch {
          // ... do the work, then:
          _uiState.value = <Area>UiState.Content(result)
      }
  }
  ```

- `_uiState.update { it.copy(...) }` — partial mutation of a
  data-class `UiState` (atomic read-modify-write; no shipped
  reference yet):

  ```kotlin
  private val _uiState = MutableStateFlow(<Area>UiState.Initial)
  val uiState: StateFlow<<Area>UiState> = _uiState.asStateFlow()

  private fun refresh() {
      _uiState.update { it.copy(isLoading = true) }
      viewModelScope.launch {
          // ... do the work, then:
          _uiState.update { it.copy(isLoading = false) }
      }
  }
  ```

## Factory (same file as the ViewModel)

`viewModelFactory { initializer { ... } }` DSL — the shipped shape,
transcribed from `ui/drawer/AppDrawerViewModel.kt`. No standalone
factory class, no `@Suppress("UNCHECKED_CAST")`.

```kotlin
internal val <Area>ViewModelFactory: ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            val application = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
            <Area>ViewModel(
                // Build repositories from application here.
            )
        }
    }
```

Four older VMs (`ui/home`, `ui/settings`, `ui/fontpicker`,
`ui/assistant`) still use the standalone
`ViewModelProvider.Factory` class shape; they migrate to this DSL
opportunistically. New code always uses the DSL.

## `<Area>Route.kt`

```kotlin
package io.github.seijikohara.femto.ui.<area>

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
internal fun <Area>Route(modifier: Modifier = Modifier) {
    val viewModel: <Area>ViewModel = viewModel(factory = <Area>ViewModelFactory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    <Area>Screen(
        uiState = uiState,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}
```

With only `modifier`, ktlint keeps the signature on one line. Once
the Route takes callbacks (shipped Routes all do — e.g.
`ui/settings/SettingsRoute.kt`), list parameters one per line with a
trailing comma.

For parameterised instances declare the factory as a function of
the parameter (`internal fun <Area>ViewModelFactory(slot: Slot):
ViewModelProvider.Factory = viewModelFactory { ... }`) and pass a
per-instance `key` so each parameter value keeps its own VM
(per-instance `key` shipped shape: `ui/fontpicker/FontPickerRoute.kt`):

```kotlin
viewModel(
    key = "font-picker-$slot",
    factory = FontPickerViewModelFactory(slot),
)
```

Bare `viewModel()` is correct only for a no-arg constructor.

## `<Area>Screen.kt` — signature delta only

Body shape: see
[../../add-compose-screen/references/screen-template.md](../../add-compose-screen/references/screen-template.md)
— the single Screen-shape SSOT. Promotion changes only the signature
and the preview:

```kotlin
@Composable
internal fun <Area>Screen(
    uiState: <Area>UiState,
    onAction: (<Area>Action) -> Unit,
    modifier: Modifier = Modifier,
) { ... }

@PreviewLightDark
@Composable
private fun <Area>ScreenPreview() {
    FemtoTheme {
        <Area>Screen(
            uiState = <Area>UiState.Initial,
            onAction = {},
        )
    }
}
```

## Notes

- The `Route` is the only file that touches `viewModel()` and
  `collectAsStateWithLifecycle()`. The `Screen` stays pure and
  preview-friendly.
- `MutableStateFlow` is `private`. Only `StateFlow` leaves the
  class. This is enforced by the reviewer agent against
  `.claude/rules/compose.md`.
- `onAction` is a `when` expression with `Unit` inferred return
  type. Per the expression-chain rule in
  `.claude/rules/kotlin-style.md`, prefer this shape over a
  statement body that wraps the same `when`.
- Mutate `_uiState` only through the two sanctioned shapes in the
  variant section above (whole-state `.value` replacement, partial
  `update {}`) — never `_uiState.value = _uiState.value.copy(...)`.
- For events that should fire once (navigation, snackbars), use a
  private `MutableSharedFlow` exposed via `asSharedFlow()`,
  collected in the Route with `LaunchedEffect` (see
  `ui/home/HomeViewModel.kt` — the shipped pattern).
- `internal` on every declaration except the `private` preview —
  `.claude/rules/kotlin-style.md`: prefer `internal` until export
  is needed.
