package io.github.seijikohara.femto.ui.home.components

import java.time.format.DateTimeFormatter

// Shared 12/24-hour clock formatter WITH minutes ("HH:mm" / "h:mm a"), honouring
// the user's clock-format setting. Used by the calendar card/panel event times
// and the weather panel's sun times so the format lives in one place. (The
// forecast hour label uses an hour-ONLY format — see WeatherGlyph.forecastHourLabel.)
private val ClockTimeFormatter24: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val ClockTimeFormatter12: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

internal fun clockTimeFormatter(is24Hour: Boolean): DateTimeFormatter =
    if (is24Hour) ClockTimeFormatter24 else ClockTimeFormatter12
