package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.calendar.CalendarInfo

internal fun fakeCalendarInfo(
    id: Long = 1L,
    displayName: String = "Personal",
    accountName: String = "me@example.com",
    color: Int = 0xFF4285F4.toInt(),
): CalendarInfo = CalendarInfo(id, displayName, accountName, color)
