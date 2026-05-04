package io.github.seijikohara.femto.data

import java.time.LocalDate
import java.time.LocalTime

internal data class ClockTick(
    val time: LocalTime,
    val date: LocalDate,
)
