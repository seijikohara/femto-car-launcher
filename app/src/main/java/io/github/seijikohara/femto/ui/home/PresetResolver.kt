package io.github.seijikohara.femto.ui.home

import io.github.seijikohara.femto.data.display.PresetMode

/** The dashboard face currently rendered. */
internal enum class PresetId { COCKPIT, DRIVING }

/** Settings inputs to the auto-switch, snapshotted together so a change re-resolves. */
internal data class PresetSwitchConfig(
    val mode: PresetMode,
    val thresholdKmh: Int,
)

// Symmetric hysteresis band around the threshold: AUTO enters DRIVING at
// threshold+band and returns to COCKPIT only at threshold-band, so a car idling
// near the threshold at a stop-light does not flap between faces.
internal const val PRESET_HYSTERESIS_KMH = 3

/**
 * Resolve the active preset. Pure so the switch logic is JVM-testable. Order:
 * passenger unlock (deliberate override) wins; then a manual mode; then AUTO
 * decides by speed with hysteresis carried through [previous].
 */
internal fun resolvePreset(
    mode: PresetMode,
    speedKmh: Double,
    thresholdKmh: Int,
    previous: PresetId,
    passengerUnlock: Boolean,
): PresetId =
    when {
        passengerUnlock -> PresetId.COCKPIT
        mode == PresetMode.COCKPIT -> PresetId.COCKPIT
        mode == PresetMode.DRIVING -> PresetId.DRIVING
        speedKmh >= thresholdKmh + PRESET_HYSTERESIS_KMH -> PresetId.DRIVING
        speedKmh <= thresholdKmh - PRESET_HYSTERESIS_KMH -> PresetId.COCKPIT
        else -> previous
    }
