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

    @Test fun `degrees maps each point to its 45-degree step`() {
        assertEquals(0f, CompassDirection.N.degrees)
        assertEquals(45f, CompassDirection.NE.degrees)
        assertEquals(90f, CompassDirection.E.degrees)
        assertEquals(135f, CompassDirection.SE.degrees)
        assertEquals(180f, CompassDirection.S.degrees)
        assertEquals(225f, CompassDirection.SW.degrees)
        assertEquals(270f, CompassDirection.W.degrees)
        assertEquals(315f, CompassDirection.NW.degrees)
    }
}
