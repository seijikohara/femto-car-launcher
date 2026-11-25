package io.github.seijikohara.femto.ui.home.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.routing.TurnKind
import io.github.seijikohara.femto.ui.home.TurnManeuver
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.distanceLabel
import io.github.seijikohara.femto.ui.locale.shortDistanceFromMeters
import io.github.seijikohara.femto.ui.locale.shortDistanceLabel
import io.github.seijikohara.femto.ui.locale.tripDistanceFromMeters
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToLong

// A distance that rounds to this many metres or feet reads in kilometres or miles instead.
private const val SHORT_DISTANCE_UP_TO = 1_000L

// Below this many metres or feet the distance rounds to FINE_STEP, from here to COARSE_STEP: near the turn the
// readout counts down in small steps, farther out a finer one would change every second without helping.
private const val FINE_STEP_BELOW = 200.0
private const val FINE_STEP = 10L
private const val COARSE_STEP = 50L

/**
 * Return the turn card's distance [meters] as its value and its unit label, in [speedUnit]'s family. Below 1,000
 * metres or feet in whole metres or feet, rounded to 10 below 200 and to 50 from there; from 1,000 (after rounding)
 * in kilometres or miles, rounded as the route row rounds them ([remainingDistanceTenthsOrNull]). The digits are ASCII
 * whatever the locale ([cardNumber]); [locale] picks the decimal separator.
 */
internal fun turnDistance(
    meters: Double,
    speedUnit: SpeedUnit,
    locale: Locale = Locale.getDefault(),
): Pair<String, String> {
    val short = speedUnit.shortDistanceFromMeters(meters.coerceAtLeast(0.0))
    val step = if (short < FINE_STEP_BELOW) FINE_STEP else COARSE_STEP
    return ((short / step).roundToLong() * step)
        .takeIf { it < SHORT_DISTANCE_UP_TO }
        ?.let { rounded -> cardNumber(rounded.toDouble(), decimals = 0, locale) to speedUnit.shortDistanceLabel() }
        ?: (longDistanceValue(meters, speedUnit, locale) to speedUnit.distanceLabel())
}

// A distance from 1,000 m or ft on, in kilometres or miles: tenths below ten units, whole units from there.
private fun longDistanceValue(
    meters: Double,
    speedUnit: SpeedUnit,
    locale: Locale,
): String =
    remainingDistanceTenthsOrNull(meters, speedUnit)?.let { cardNumber(it, decimals = 1, locale) }
        ?: cardNumber(speedUnit.tripDistanceFromMeters(meters), decimals = 0, locale)

/**
 * Return [value] with [decimals] decimals as the turn card writes every number: in ASCII digits whatever the locale,
 * since the card's sentence is English and its numbers read as one, with the separator [locale] uses beside those
 * digits.
 */
internal fun cardNumber(
    value: Double,
    decimals: Int,
    locale: Locale = Locale.getDefault(),
): String =
    DecimalFormat(
        if (decimals > 0) "0." + "0".repeat(decimals) else "0",
        DecimalFormatSymbols
            .getInstance(
                Locale
                    .Builder()
                    .setLocale(locale)
                    .setUnicodeLocaleKeyword("nu", "latn")
                    .build(),
            ).apply { zeroDigit = '0' },
    ).apply { roundingMode = RoundingMode.HALF_UP }
        .format(value)

/**
 * Return the turn card's line of text for [maneuver]: "Turn left", "Take exit 2", "Destination". The exit number is a
 * plain integer; an ordinal would take its words from the device's language, not this sentence's.
 */
@Composable
internal fun turnText(maneuver: TurnManeuver): String =
    when {
        maneuver is TurnManeuver.Turn && maneuver.kind.roundabout && maneuver.exitNumber > 0 -> {
            stringResource(R.string.turn_card_roundabout_exit, cardNumber(maneuver.exitNumber.toDouble(), decimals = 0))
        }

        else -> {
            stringResource(turnTextRes(maneuver))
        }
    }

private val TurnKind.roundabout: Boolean
    get() = this == TurnKind.ROUNDABOUT_COUNTERCLOCKWISE || this == TurnKind.ROUNDABOUT_CLOCKWISE

// The card never announces off-route and beeline hints (turnCardStateOrNull drops them); should one reach it, it
// reads as going on.
@StringRes
private fun turnTextRes(maneuver: TurnManeuver): Int =
    (maneuver as? TurnManeuver.Turn)?.kind?.let(::turnKindTextRes) ?: R.string.turn_card_arrive

// A roundabout's text without its exit number; one with it reads through turn_card_roundabout_exit. Both motorway
// exits read the same: the arrow shows the side, and a line naming it would not fit a narrow card.
@StringRes
private fun turnKindTextRes(kind: TurnKind): Int =
    when (kind) {
        TurnKind.STRAIGHT, TurnKind.OFF_ROUTE, TurnKind.BEELINE -> R.string.turn_card_straight
        TurnKind.LEFT -> R.string.turn_card_left
        TurnKind.RIGHT -> R.string.turn_card_right
        TurnKind.SLIGHT_LEFT -> R.string.turn_card_slight_left
        TurnKind.SLIGHT_RIGHT -> R.string.turn_card_slight_right
        TurnKind.SHARP_LEFT -> R.string.turn_card_sharp_left
        TurnKind.SHARP_RIGHT -> R.string.turn_card_sharp_right
        TurnKind.KEEP_LEFT -> R.string.turn_card_keep_left
        TurnKind.KEEP_RIGHT -> R.string.turn_card_keep_right
        TurnKind.U_TURN_LEFT, TurnKind.U_TURN_RIGHT, TurnKind.U_TURN -> R.string.turn_card_u_turn
        TurnKind.ROUNDABOUT_COUNTERCLOCKWISE, TurnKind.ROUNDABOUT_CLOCKWISE -> R.string.turn_card_roundabout_enter
        TurnKind.EXIT_LEFT, TurnKind.EXIT_RIGHT -> R.string.turn_card_exit
    }
