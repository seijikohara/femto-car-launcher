package io.github.seijikohara.femto.data.common

import android.app.role.RoleManager
import android.content.Context
import androidx.core.content.getSystemService

/**
 * Whether this app holds the HOME role, i.e. is the device's default home app.
 * Shared because two domains read it: diagnostics reports it, and the updater
 * picks its post-install path by it (the home app may start an activity from
 * the background, though not always; see the updater's afterUpdateAction).
 */
internal fun Context.holdsHomeRole(): Boolean =
    getSystemService<RoleManager>()?.isRoleHeld(RoleManager.ROLE_HOME) == true
