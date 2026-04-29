# ViewModel + UiState scaffold

Shape SSOT for the four-file UDF pattern. Replace `<Area>` and
`<area>` placeholders. Do not change the structure (read-only
exposed `StateFlow`, single `onAction` entry, `Route` separate from
`Screen`) without a documented reason. Rule:
`CLAUDE.md#compose-architecture`.

## `<Area>UiState.kt`

```kotlin
package io.github.seijikohara.femto.ui.<area>

import androidx.compose.runtime.Immutable

@Immutable
data class <Area>UiState(
    // Add stable fields. Use kotlinx.collections.immutable.ImmutableList
    // for lists where stability matters for skippability.
    val isLoading: Boolean = false,
    val items: List<String> = emptyList(),
) {
    companion object {
        val Initial: <Area>UiState = <Area>UiState()
    }
}

sealed interface <Area>Action {
    data object Refresh : <Area>Action
    data class Select(val id: String) : <Area>Action
}
```

## `<Area>ViewModel.kt`

```kotlin
package io.github.seijikohara.femto.ui.<area>

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class <Area>ViewModel(
    // Inject repositories / dispatchers here. Keep the VM
    // testable in a plain JVM unit test — no Android types.
) : ViewModel() {

    private val _uiState = MutableStateFlow(<Area>UiState.Initial)
    val uiState: StateFlow<<Area>UiState> = _uiState.asStateFlow()

    fun onAction(action: <Area>Action) {
        when (action) {
            <Area>Action.Refresh -> refresh()
            is <Area>Action.Select -> select(action.id)
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // ... do the work, then:
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun select(id: String) {
        // ... handle selection
    }
}
```

## `<Area>Route.kt`

```kotlin
package io.github.seijikohara.femto.ui.<area>

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun <Area>Route(
    modifier: Modifier = Modifier,
    viewModel: <Area>ViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    <Area>Screen(
        uiState = uiState,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}
```

## `<Area>Screen.kt`

```kotlin
package io.github.seijikohara.femto.ui.<area>

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark

@Composable
fun <Area>Screen(
    uiState: <Area>UiState,
    onAction: (<Area>Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(FemtoDimens.ScreenPadding),
            contentAlignment = Alignment.Center,
        ) {
            // TODO: render uiState; emit user events via onAction.
            Text(
                text = "<Area>",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

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
  `CLAUDE.md#compose-architecture`.
- Use `_uiState.update { it.copy(...) }` for mutations — atomic,
  no read-modify-write race.
- For events that should fire once (navigation, snackbars), use a
  `Channel<Event>` exposed as `Flow<Event>` rather than
  `StateFlow`. Add to the same VM.
