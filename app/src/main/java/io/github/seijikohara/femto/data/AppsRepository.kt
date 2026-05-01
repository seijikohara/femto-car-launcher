package io.github.seijikohara.femto.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Process
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wraps [LauncherApps] for the launcher home grid.
 *
 * Apps with category.HOME (this app) may call
 * [LauncherApps.getActivityList] without `QUERY_ALL_PACKAGES`
 * thanks to the launcher exception in the package-visibility
 * model. Adding new permissions for this feature is therefore not
 * required.
 */
class AppsRepository(
    private val context: Context,
) {
    private val launcherApps: LauncherApps = checkNotNull(context.getSystemService())

    /**
     * Return all main-launchable activities for the current user,
     * sorted by label. Icons are resolved once into [android.graphics.Bitmap]s
     * on the IO dispatcher so the UI layer does not hold mutable
     * `Drawable` references.
     */
    suspend fun queryApps(): List<AppEntry> =
        withContext(Dispatchers.IO) {
            launcherApps
                .getActivityList(null, Process.myUserHandle())
                .map(LauncherActivityInfo::toAppEntry)
                .sortedBy { it.label.lowercase() }
        }

    /**
     * Launch the given activity. The bounds and options are unused
     * for the MVP grid; pass `null` to defer to system defaults.
     */
    fun launch(componentName: ComponentName): Unit =
        launcherApps.startMainActivity(componentName, Process.myUserHandle(), null, null)
}

private const val ICON_PIXELS = 192

private fun LauncherActivityInfo.toAppEntry(): AppEntry =
    AppEntry(
        componentName = componentName,
        label = label.toString(),
        icon = getIcon(0).toBitmap(width = ICON_PIXELS, height = ICON_PIXELS),
    )
