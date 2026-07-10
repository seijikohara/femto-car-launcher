package io.github.seijikohara.femto.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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

/**
 * A coarse-only grant (the system "Approximate" toggle) lets the launcher
 * serve location at degraded precision via the network provider, honoring the
 * manifest's `ACCESS_COARSE_LOCATION` contract when the user withholds fine.
 */
internal fun Context.hasCoarseLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
