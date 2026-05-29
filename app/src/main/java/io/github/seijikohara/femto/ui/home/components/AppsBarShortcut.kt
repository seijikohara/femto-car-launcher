package io.github.seijikohara.femto.ui.home.components

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.Compass
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Map
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Phone

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
    Phone(Lucide.Phone, "android.intent.category.APP_CONTACTS"),
    Music(Lucide.Music, "android.intent.category.APP_MUSIC"),
    Maps(Lucide.Map, "android.intent.category.APP_MAPS"),
    Camera(Lucide.Camera, "android.intent.category.APP_GALLERY"),
    Navigation(Lucide.Compass, "android.intent.category.APP_MAPS"),
}
