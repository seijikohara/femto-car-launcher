package io.github.seijikohara.femto.data.calendar

/** A device calendar the user can choose to show or hide. `color` labels it in the selector only. */
internal data class CalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val color: Int,
)
