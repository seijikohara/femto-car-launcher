package io.github.seijikohara.femto.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.seijikohara.femto.BuildConfig

/**
 * Driving-lockout signal stub.
 *
 * The single source of truth for the lockout policy is the
 * `gate-driving-visible-feature` skill under `.claude/skills/`.
 * Until the real signal lands (GPS speed, vehicle CAN, AI-Box
 * driving flag), this stub determines whether driver-distracting
 * UI is shown.
 *
 * Default behaviour:
 * - Release builds: locked. The user sees the safe placeholder
 *   surface until the real signal is wired.
 * - Debug builds: unlocked. This is the documented build-variant
 *   override that the lockout SSOT permits, so development and
 *   AVD smoke tests can exercise the full UI.
 *
 * TODO(driving-lockout): replace with a real DrivingState source
 * (GPS speed >= 5 km/h, ACC line, OBD-II RPM > idle, etc.).
 */
@Composable
fun rememberDrivingLockState(): Boolean = remember { !BuildConfig.DEBUG }
