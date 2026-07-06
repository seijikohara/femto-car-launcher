package io.github.seijikohara.femto.data.diagnostics

import android.app.ActivityManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Builds the permission table rows from a package's raw grant map: dangerous
 * (user-deniable) permissions sort first, alphabetically within each group,
 * so a denied dangerous grant never scrolls past the fold. Pure so
 * [PermissionFactsTest] can pin the flag-decode and sort on the JVM without a
 * live `PackageManager`.
 */
internal fun permissionRowsFrom(
    requested: Array<String>?,
    flags: IntArray?,
    dangerous: Set<String>,
): List<PermissionRow> =
    requested
        .orEmpty()
        .mapIndexed { index, permission ->
            PermissionRow(
                name = permission.substringAfterLast('.'),
                granted = (flags?.getOrNull(index) ?: 0) and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0,
                dangerous = permission in dangerous,
            )
        }.sortedWith(compareByDescending<PermissionRow> { it.dangerous }.thenBy { it.name })

/** Collects the PERMISSIONS diagnostics section. */
internal class PermissionFactsCollector(
    private val context: Context,
) {
    suspend fun permissionFacts(): SectionPayload.PermissionTable =
        withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            val packageInfo =
                packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
                )
            val requested = packageInfo.requestedPermissions
            val dangerous = requested.orEmpty().filter { it.isDangerousPermission(packageManager) }.toSet()

            SectionPayload.PermissionTable(
                rows = permissionRowsFrom(requested, packageInfo.requestedPermissionsFlags, dangerous),
                extras =
                    listOf(
                        notificationListenerFact(),
                        homeRoleFact(),
                        batteryOptimizationFact(),
                        notificationsFact(),
                        backgroundFact(),
                    ),
            )
        }

    // A permission the platform never registered (a stale request, an OEM
    // fork removing a constant) degrades to "not dangerous" rather than
    // crashing the whole section.
    private fun String.isDangerousPermission(packageManager: PackageManager): Boolean =
        runCatching {
            packageManager.getPermissionInfo(this, 0).protection == PermissionInfo.PROTECTION_DANGEROUS
        }.getOrDefault(false)

    private fun notificationListenerFact(): DiagnosticFact {
        val enabled = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
        return DiagnosticFact(
            "Notification listener",
            FactValue.Status(
                if (enabled) "enabled" else "disabled",
                if (enabled) FactHealth.OK else FactHealth.ERROR,
            ),
        )
    }

    // INFO, not a verdict: a phone install legitimately never holds the HOME
    // role (the user keeps their stock launcher), unlike an AI box or head
    // unit where holding it is the whole point of the app.
    private fun homeRoleFact(): DiagnosticFact {
        val held = context.getSystemService<RoleManager>()?.isRoleHeld(RoleManager.ROLE_HOME) == true
        val defaultPackage =
            context.packageManager
                .resolveActivity(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
                )?.activityInfo
                ?.packageName
        val heldLabel = if (held) "held" else "not held"
        val value = defaultPackage?.let { "$heldLabel (default: $it)" } ?: heldLabel
        return DiagnosticFact("HOME role", FactValue.Status(value, FactHealth.INFO))
    }

    private fun batteryOptimizationFact(): DiagnosticFact {
        val exempt =
            context.getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(context.packageName) == true
        return DiagnosticFact(
            "Battery optimization",
            FactValue.Status(
                if (exempt) "exempt" else "optimized",
                if (exempt) FactHealth.OK else FactHealth.WARNING,
            ),
        )
    }

    private fun notificationsFact(): DiagnosticFact {
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        return DiagnosticFact(
            "Notifications",
            FactValue.Status(
                if (enabled) "enabled" else "disabled",
                if (enabled) FactHealth.OK else FactHealth.WARNING,
            ),
        )
    }

    private fun backgroundFact(): DiagnosticFact {
        val restricted = context.getSystemService<ActivityManager>()?.isBackgroundRestricted == true
        return DiagnosticFact(
            "Background",
            FactValue.Status(
                if (restricted) "restricted" else "unrestricted",
                if (restricted) FactHealth.WARNING else FactHealth.OK,
            ),
        )
    }
}
