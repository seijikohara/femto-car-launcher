package io.github.seijikohara.femto.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/**
 * Compose-side accessor for the driving-lockout state.
 *
 * The single source of truth for the lockout policy is the
 * `gate-driving-visible-feature` skill under `.claude/skills/`.
 *
 * Default behaviour (locked = the safe answer):
 * - Compose previews: unlocked, so design review can see the full
 *   surface.
 * - `ACCESS_FINE_LOCATION` not granted: locked.
 * - Permission granted, no GPS fix yet: locked.
 * - Permission granted, GPS speed below the unlock threshold:
 *   unlocked.
 * - Permission granted, GPS speed at or above the unlock threshold:
 *   locked.
 *
 * The state is re-evaluated every time the host activity reaches
 * the STARTED lifecycle, so a permission grant that arrives via a
 * runtime dialog is reflected as soon as the dialog dismisses.
 */
@Composable
fun rememberDrivingLockState(): Boolean {
    if (LocalInspectionMode.current) return false

    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    return produceState(initialValue = true, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (!context.hasFineLocationPermission()) {
                value = true
                return@repeatOnLifecycle
            }
            DrivingStateRepository(context).lockedFlow().collect { value = it }
        }
    }.value
}

internal fun Context.hasFineLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
