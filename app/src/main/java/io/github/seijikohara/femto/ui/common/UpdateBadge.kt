package io.github.seijikohara.femto.ui.common

import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.seijikohara.femto.R

/**
 * The dot that marks where an update waits: the dock's Settings button and
 * the Updates entry in the Settings category list. Both draw this one
 * composable, so they cannot differ in colour or in what they announce. It is
 * the standard M3 small badge, in the accent colour rather than the badge's
 * default error red: it announces something new, not a fault, on a screen a
 * driver reads at a glance. Its description is merged into the control it
 * sits on, so a screen reader reads it after the control's own label.
 */
@Composable
internal fun UpdateBadge(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.update_available)
    Badge(
        modifier = modifier.semantics { contentDescription = description },
        containerColor = MaterialTheme.colorScheme.primary,
    )
}
