package io.github.seijikohara.femto.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Lightweight checks for the runtime-grant permissions the launcher requests.
 *
 * Each helper returns `true` when the permission is already granted (or when
 * the platform auto-grants it on the current API level), `false` otherwise.
 * Callers that need to *prompt* for a permission should use
 * `ActivityResultContracts.RequestPermission()` and consult these helpers
 * before invoking the underlying API.
 */
internal fun Context.hasFineLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

internal fun Context.hasReadCalendarPermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.READ_CALENDAR,
    ) == PackageManager.PERMISSION_GRANTED

/**
 * `BLUETOOTH_CONNECT` is a runtime grant only on Android 12+ (API 31).
 * Below that, the permission is install-time and always considered granted.
 */
internal fun Context.hasBluetoothConnectPermission(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.BLUETOOTH_CONNECT,
    ) == PackageManager.PERMISSION_GRANTED
}
