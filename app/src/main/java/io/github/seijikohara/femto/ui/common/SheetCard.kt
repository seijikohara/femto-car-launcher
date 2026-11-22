package io.github.seijikohara.femto.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The rounded card a sheet over settings holds its rows in, as a Settings category holds its own: the Saved map
 * regions sheet (list and picker) and the Routing data sheet.
 */
@Composable
internal fun SheetCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = Surface(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainer,
    content = content,
)
