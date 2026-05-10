package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.ui.theme.FemtoDimens

internal enum class AppsBarShortcut(
    val icon: ImageVector,
    val intentCategory: String,
) {
    Phone(Icons.Outlined.Phone, "android.intent.category.APP_CONTACTS"),
    Music(Icons.Outlined.MusicNote, "android.intent.category.APP_MUSIC"),
    Maps(Icons.Outlined.Map, "android.intent.category.APP_MAPS"),
    Camera(Icons.Outlined.PhotoCamera, "android.intent.category.APP_GALLERY"),
    Navigation(Icons.Outlined.Explore, "android.intent.category.APP_MAPS"),
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
        Tile(
            icon = Icons.Outlined.Apps,
            description = "Open all apps",
            onClick = onOpenDrawer,
        )
        AppsBarShortcut.entries.forEach { shortcut ->
            Tile(
                icon = shortcut.icon,
                description = "Apps shortcut: ${shortcut.name}",
                onClick = { onShortcut(shortcut) },
            )
        }
    }
}

@Composable
private fun Tile(
    icon: ImageVector,
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
    Box(contentAlignment = Alignment.Center) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}
