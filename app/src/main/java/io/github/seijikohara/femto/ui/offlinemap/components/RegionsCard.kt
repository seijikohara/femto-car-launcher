package io.github.seijikohara.femto.ui.offlinemap.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The rounded card both views of the sheet hold their rows in, as a Settings category holds its own. */
@Composable
internal fun RegionsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = Surface(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainer,
    content = content,
)
