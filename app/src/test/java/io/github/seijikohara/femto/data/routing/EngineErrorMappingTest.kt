package io.github.seijikohara.femto.data.routing

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals

/** BRouter reports every failure as text; each message it is known to send maps to one typed result. */
@RunWith(Parameterized::class)
internal class EngineErrorMappingTest(
    private val message: String,
    private val expected: RouteResult,
) {
    @Test
    fun `maps the engine's message to a typed result`() {
        assertEquals(expected, EngineOutcome.Error(message).toRouteResult())
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> =
            listOf(
                arrayOf("datafile E135_N30.rd5 not found", RouteResult.MissingTiles(listOf("E135_N30"))),
                arrayOf("datafile W5_S5.rd5 not found", RouteResult.MissingTiles(listOf("W5_S5"))),
                arrayOf(
                    "java.io.IOException: lookup version mismatch (old rd5?) lookups.dat=11 E130_N30.rd5=10",
                    RouteResult.DataVersionMismatch,
                ),
                arrayOf("pass0 timeout after 60 seconds", RouteResult.TimedOut),
                arrayOf("pass1 timeout after 60 seconds", RouteResult.TimedOut),
                arrayOf(
                    "target island detected for section 0",
                    RouteResult.Failed("target island detected for section 0"),
                ),
                arrayOf("no track found", RouteResult.Failed("no track found")),
            )
    }
}
