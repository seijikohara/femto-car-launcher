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
fun <Name>Screen(
    modifier: Modifier = Modifier,
    // additional hoisted state and callbacks go here
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

- The `modifier: Modifier = Modifier` parameter is mandatory on
  every Composable that emits content. The first thing the body
  does with it is apply layout constraints (`modifier.fillMaxSize()`,
  `modifier.padding(...)`, etc.). Compose Lint enforces this via
  the `compose:modifier-missing-check` rule.
- `FemtoTheme { ... }` only appears inside the preview — production
  callers wrap once at `MainActivity`. Rule: `CLAUDE.md#design-system`.
- For interactive elements set
  `Modifier.defaultMinSize(minWidth = FemtoDimens.MinTouchTarget,
  minHeight = FemtoDimens.MinTouchTarget)`. Rule:
  `CLAUDE.md#automotive-overrides`.
- For longer text passages prefer `bodyLarge`. Reserve `bodyMedium`
  for secondary content. Never `bodySmall` / `labelSmall` on the
  head-unit dashboard. Rule: `CLAUDE.md#automotive-overrides`.
- Per `CLAUDE.md#kotlin-style`, prefer expression chains: when a
  Composable's body simply forwards parameters to a single emitter,
  use an expression body
  (`@Composable fun Foo() = Surface { ... }`); collapse
  `when` / `if` branches to single expressions where the result
  is the only output.
