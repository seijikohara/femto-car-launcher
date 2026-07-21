package io.github.seijikohara.femto.data.apps

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import android.util.Log
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
     * Return `false` for the two failures a HOME launcher must survive when
     * tapping a third-party tile: [ActivityNotFoundException] (a stale shortcut
     * after an uninstall) and [SecurityException] (an OEM activity that turns
     * out to be non-exported or permission-guarded). Either would otherwise
     * crash the launcher process. Other errors still propagate
     * (matches `MainActivity#tryStartActivity`).
     */
    fun launch(componentName: ComponentName): Boolean =
        runCatching { startActivity(componentName) }
            .fold(
                onSuccess = { true },
                onFailure = { error ->
                    when (error) {
                        is ActivityNotFoundException, is SecurityException -> {
                            // The only field trail for a dead tap — head units
                            // are typically adb-unreachable.
                            Log.w(TAG, "could not launch ${componentName.flattenToShortString()}", error)
                            false
                        }

                        else -> {
                            throw error
                        }
                    }
                },
            )

    /**
     * Open the system App-info page for the app — the sanctioned entry point
     * for force-stop / disable / storage actions a Play-distributed launcher
     * cannot perform itself. Uses the same [LauncherApps] family as [launch].
     */
    fun openAppDetails(componentName: ComponentName) {
        runCatching { launcherApps.startAppDetailsActivity(componentName, Process.myUserHandle(), null, null) }
            .onFailure { Log.w(TAG, "could not open app details for ${componentName.flattenToShortString()}", it) }
    }

    /**
     * Ask the system uninstaller to remove the app; the confirmation UI and
     * the actual deletion are the system's. Swallows resolution failures the
     * same way [launch] does — a HOME launcher must never crash on a tap.
     */
    fun requestUninstall(componentName: ComponentName) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DELETE, "package:${componentName.packageName}".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { Log.w(TAG, "could not request uninstall for ${componentName.flattenToShortString()}", it) }
    }

    /**
     * Emits on every package add / remove / change for the current user, so an
     * open drawer refreshes itself after an uninstall completes or an install
     * lands. Registration lives only while the flow is collected.
     */
    val packageChanges: Flow<Unit> =
        callbackFlow {
            val callback =
                object : LauncherApps.Callback() {
                    override fun onPackageRemoved(
                        packageName: String?,
                        user: UserHandle?,
                    ) {
                        trySend(Unit)
                    }

                    override fun onPackageAdded(
                        packageName: String?,
                        user: UserHandle?,
                    ) {
                        trySend(Unit)
                    }

                    override fun onPackageChanged(
                        packageName: String?,
                        user: UserHandle?,
                    ) {
                        trySend(Unit)
                    }

                    override fun onPackagesAvailable(
                        packageNames: Array<out String>?,
                        user: UserHandle?,
                        replacing: Boolean,
                    ) {
                        trySend(Unit)
                    }

                    override fun onPackagesUnavailable(
                        packageNames: Array<out String>?,
                        user: UserHandle?,
                        replacing: Boolean,
                    ) {
                        trySend(Unit)
                    }
                }
            launcherApps.registerCallback(callback)
            awaitClose { launcherApps.unregisterCallback(callback) }
        }

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
        }.onFailure {
            // Distinguish a lookup fault from the legitimate "no launchable
            // activity" null — both end in a no-op tap on the music card.
            Log.w(TAG, "launcher lookup failed for $packageName", it)
        }.getOrNull()
}

private const val TAG = "AppsRepository"

private const val ICON_PIXELS = 192

private fun LauncherActivityInfo.toAppEntry(): AppEntry =
    AppEntry(
        componentName = componentName,
        label = label.toString(),
        icon = getIcon(0).toBitmap(width = ICON_PIXELS, height = ICON_PIXELS),
        isSystem =
            applicationInfo.flags and
                (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
    )
