package io.github.seijikohara.femto.ui.home.components.driving

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class BriefingLabelTest {
    private val today = LocalDate.of(2026, 5, 1)

    @Test fun `the same date classifies as today`() {
        assertEquals(RelativeDay.TODAY, relativeDayOf(today, today))
    }

    @Test fun `the next day classifies as tomorrow`() {
        assertEquals(RelativeDay.TOMORROW, relativeDayOf(today.plusDays(1), today))
    }

    @Test fun `two days out classifies as other`() {
        assertEquals(RelativeDay.OTHER, relativeDayOf(today.plusDays(2), today))
    }

    @Test fun `a past date defensively classifies as other`() {
        assertEquals(RelativeDay.OTHER, relativeDayOf(today.minusDays(1), today))
    }
}
