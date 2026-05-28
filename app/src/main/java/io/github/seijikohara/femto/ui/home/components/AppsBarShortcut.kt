package io.github.seijikohara.femto.ui.home.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Apps shortcut category referenced by [io.github.seijikohara.femto.ui.home.HomeAction.Shortcut].
 *
 * `intentCategory` matches the value the launcher dispatches via
 * `HomeEvent.LaunchAppCategory`, deferring the actual app pick to whichever
 * app the user has elected as the default for that category. The icon /
 * label are only used by previews; the production footer renders bespoke
 * navigation buttons in [DashboardFooter] rather than the generic tile.
 */
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
