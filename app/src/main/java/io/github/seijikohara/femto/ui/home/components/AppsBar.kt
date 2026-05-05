package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.ui.theme.FemtoDimens

internal enum class AppsBarShortcut(
    val label: String,
    val intentCategory: String,
) {
    Phone("📞", "android.intent.category.APP_CONTACTS"),
    Music("🎵", "android.intent.category.APP_MUSIC"),
    Maps("📍", "android.intent.category.APP_MAPS"),
    Camera("📷", "android.intent.category.APP_GALLERY"),
    Navigation("🧭", "android.intent.category.APP_MAPS"),
}

@Composable
internal fun AppsBar(
    onOpenDrawer: () -> Unit,
    onShortcut: (AppsBarShortcut) -> Unit,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surfaceContainer,
    shape = MaterialTheme.shapes.large,
    tonalElevation = FemtoDimens.CardElevation,
) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tile(label = "≡", description = "Open all apps", onClick = onOpenDrawer)
        AppsBarShortcut.entries.forEach { shortcut ->
            Tile(
                label = shortcut.label,
                description = "Apps shortcut: ${shortcut.name}",
                onClick = { onShortcut(shortcut) },
            )
        }
    }
}

@Composable
private fun Tile(
    label: String,
    description: String,
    onClick: () -> Unit,
) = Surface(
    modifier =
        Modifier
            .size(FemtoDimens.MinTouchTarget)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
}
