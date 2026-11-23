package io.github.seijikohara.femto.data.routing

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RouteConditionsTest {
    @Test
    fun `no conditions still set the target speed`() {
        assertEquals(mapOf("vmax" to "110"), RouteConditions().engineParameters())
    }

    @Test
    fun `avoiding motorways adds its parameter as 1`() {
        assertEquals(
            mapOf("vmax" to "110", "avoid_motorways" to "1"),
            RouteConditions(avoidMotorways = true).engineParameters(),
        )
    }

    @Test
    fun `avoiding tolls adds its parameter as 1`() {
        assertEquals(mapOf("vmax" to "110", "avoid_toll" to "1"), RouteConditions(avoidTolls = true).engineParameters())
    }

    @Test
    fun `only conditions with nothing set are the default`() {
        assertTrue(RouteConditions().isDefault)
        assertFalse(RouteConditions(avoidMotorways = true).isDefault)
        assertFalse(RouteConditions(avoidTolls = true).isDefault)
    }
}
