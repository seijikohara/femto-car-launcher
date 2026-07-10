package io.github.seijikohara.femto.data.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Cross-domain checks for the runtime-grant permissions the launcher requests.
 *
 * These sit in `data/common` (not any single domain) because music, system, and
 * diagnostics all consult them; the location-specific fine/coarse checks stay in
 * `data/location`. Each helper returns `true` when the permission is already
 * granted (or auto-granted on the current API level), `false` otherwise. Callers
 * that need to *prompt* should use `ActivityResultContracts.RequestPermission()`
 * and consult these helpers before invoking the underlying API.
 */
internal fun Context.hasReadCalendarPermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.READ_CALENDAR,
    ) == PackageManager.PERMISSION_GRANTED

/**
 * `READ_PHONE_STATE` gates the cellular `SignalStrength` read. It is a runtime
 * grant on every supported API level; without it the dock's cellular
 * indicator degrades to the binary connected/disconnected icon.
 */
internal fun Context.hasReadPhoneStatePermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.READ_PHONE_STATE,
    ) == PackageManager.PERMISSION_GRANTED

/**
 * `RECORD_AUDIO` gates the in-launcher voice assistant's microphone capture
 * and the music card's output-mix Visualizer (which never touches the mic but
 * sits behind the same permission by platform contract). It is a runtime
 * grant on every supported API level; when withheld the assistant sheet falls
 * back to the system-intent delegation rows and the spectrum renders flat.
 */
internal fun Context.hasRecordAudioPermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

/**
 * `BLUETOOTH_CONNECT` is a runtime grant on Android 12+ (API 31); the app's
 * minSdk is 33, so it is always a runtime grant and never auto-granted.
 */
internal fun Context.hasBluetoothConnectPermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.BLUETOOTH_CONNECT,
    ) == PackageManager.PERMISSION_GRANTED
