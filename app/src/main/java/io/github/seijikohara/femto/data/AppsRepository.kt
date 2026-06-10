package io.github.seijikohara.femto.data

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Process
import android.util.Log
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
internal class AppsRepository(
    private val context: Context,
    launcher: ((ComponentName) -> Unit)? = null,
) {
    private val launcherApps: LauncherApps = checkNotNull(context.getSystemService())

    // Seam: production launches via LauncherApps; tests inject a stub launcher.
    private val startActivity: (ComponentName) -> Unit =
        launcher ?: { componentName ->
            launcherApps.startMainActivity(componentName, Process.myUserHandle(), null, null)
        }

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
                // Isolate per-app resolution: getIcon() can throw
                // Resources.NotFoundException or OOM on a pathological adaptive
                // icon. One bad package must not blank the whole grid.
                .mapNotNull { info ->
                    runCatching { info.toAppEntry() }
                        .onFailure {
                            Log.w(TAG, "dropping ${info.componentName.flattenToShortString()} from app grid", it)
                        }.getOrNull()
                }.sortedBy { it.label.lowercase() }
        }

    /**
     * Launch the given activity and report whether it resolved.
     *
     * Return `false` for [ActivityNotFoundException] — a stale shortcut
     * after an uninstall must not crash the HOME launcher. Other errors
     * still propagate (matches `MainActivity#startActivityIfResolved`).
     */
    fun launch(componentName: ComponentName): Boolean =
        runCatching { startActivity(componentName) }
            .fold(
                onSuccess = { true },
                onFailure = { error ->
                    if (error is ActivityNotFoundException) false else throw error
                },
            )

    /**
     * Resolve a package to its primary launcher activity, or null when the
     * package exposes no launchable activity (e.g. a background-only media
     * service). Used to open the app behind the current media session, reusing
     * the same [LauncherApps] launch path as the home grid.
     */
    fun launcherComponentFor(packageName: String): ComponentName? =
        runCatching {
            launcherApps
                .getActivityList(packageName, Process.myUserHandle())
                .firstOrNull()
                ?.componentName
        }.getOrNull()
}

private const val TAG = "AppsRepository"

private const val ICON_PIXELS = 192

private fun LauncherActivityInfo.toAppEntry(): AppEntry =
    AppEntry(
        componentName = componentName,
        label = label.toString(),
        icon = getIcon(0).toBitmap(width = ICON_PIXELS, height = ICON_PIXELS),
    )
