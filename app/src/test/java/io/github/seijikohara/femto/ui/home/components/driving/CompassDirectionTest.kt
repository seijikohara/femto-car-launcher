package io.github.seijikohara.femto.ui.home.components.driving

import org.junit.Test
import kotlin.test.assertEquals

class CompassDirectionTest {
    @Test fun `maps the eight sectors from their centres`() {
        assertEquals(CompassDirection.N, compassDirectionOf(0f))
        assertEquals(CompassDirection.NE, compassDirectionOf(45f))
        assertEquals(CompassDirection.E, compassDirectionOf(90f))
        assertEquals(CompassDirection.S, compassDirectionOf(180f))
        assertEquals(CompassDirection.W, compassDirectionOf(270f))
        assertEquals(CompassDirection.NW, compassDirectionOf(315f))
    }

    @Test fun `sector boundaries round to the nearer point`() {
        assertEquals(CompassDirection.N, compassDirectionOf(22f)) // <22.5 → N
        assertEquals(CompassDirection.NE, compassDirectionOf(23f)) // ≥22.5 → NE
        assertEquals(CompassDirection.N, compassDirectionOf(338f)) // ≥337.5 → N
    }

    @Test fun `normalizes out-of-range and negative degrees`() {
        assertEquals(CompassDirection.N, compassDirectionOf(360f))
        assertEquals(CompassDirection.N, compassDirectionOf(720f))
        assertEquals(CompassDirection.W, compassDirectionOf(-90f))
    }
}
