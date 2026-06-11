# Compose screen scaffold

This file is the **shape SSOT** for new Compose screens. Use it as
the starting point. Replace `<Name>` and `<area>` placeholders.
Replace the TODO with real content. Do not change the structure
(`Surface` root, `FemtoDimens` padding, `@PreviewLightDark`)
without a documented reason.

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
internal fun <Name>Screen(
    // required hoisted state and callbacks go here, first
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
            // TODO: real content using MaterialTheme.typography.*
            //       and FemtoDimens.* — never magic numbers.
            Text(
                text = "<Name>",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun <Name>ScreenPreview() {
    FemtoTheme {
        <Name>Screen()
    }
}
```

## Notes

- `modifier` is the first non-state parameter (after required
  state/callbacks), applied before internal modifiers
  (`modifier.fillMaxSize()`, `modifier.padding(...)`, etc.) —
  ktlint `compose:modifier-missing-check` enforces presence;
  shipped screens fix the order.
- Declarations are `internal` (the preview stays `private`) —
  prefer `internal` until export is needed. Rule:
  `.claude/rules/kotlin-style.md`.
- `color = MaterialTheme.colorScheme.background` is for top-level
  screens. Sheet-hosted screens (Settings, FontPicker) use
  `surfaceContainerLow` — match the shipped component for your
  container type; see `.claude/rules/design-system.md`.
- `FemtoTheme { ... }` only appears inside the preview — production
  callers wrap once at `MainActivity`. Rule:
  `.claude/rules/design-system.md`.
- For interactive elements set
  `Modifier.defaultMinSize(minWidth = FemtoDimens.MinTouchTarget,
  minHeight = FemtoDimens.MinTouchTarget)`. Rule:
  `CLAUDE.md#automotive-overrides`.
- For longer text passages prefer `bodyLarge`. Reserve `bodyMedium`
  for secondary content. Never `bodySmall` / `labelSmall` on the
  head-unit dashboard. Rule: `CLAUDE.md#automotive-overrides`.
- Per `.claude/rules/kotlin-style.md`, prefer expression chains: when a
  Composable's body simply forwards parameters to a single emitter,
  use an expression body
  (`@Composable fun Foo() = Surface { ... }`); collapse
  `when` / `if` branches to single expressions where the result
  is the only output.
