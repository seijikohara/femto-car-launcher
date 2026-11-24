package io.github.seijikohara.femto.ui.home.components

import io.github.seijikohara.femto.data.routing.RoutePoint
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals

class RouteScriptTest {
    @Test fun `the route goes out as a flat lat, lon array at six decimals with both colours`() {
        assertEquals(
            "window.setRoute && setRoute([35.681236,139.767125,-33.868800,151.209300], '#3367D6', '#FFFFFF')",
            setRouteScript(
                listOf(RoutePoint(35.681236, 139.767125), RoutePoint(-33.8688, 151.2093)),
                lineColor = LINE,
                casingColor = CASING,
            ),
        )
    }

    @Test fun `small coordinates stay plain decimals, never exponent notation`() {
        assertEquals(
            "window.setRoute && setRoute([0.000010,-0.000020,0.000000,180.000000], '#3367D6', '#FFFFFF')",
            setRouteScript(
                listOf(RoutePoint(0.00001, -0.00002), RoutePoint(-0.0000004, 180.0)),
                lineColor = LINE,
                casingColor = CASING,
            ),
        )
    }

    @Test fun `the device locale never changes the numbers`() {
        val points = listOf(RoutePoint(48.137154, 11.576124), RoutePoint(48.2, 11.6))
        val expected = setRouteScript(points, lineColor = LINE, casingColor = CASING)
        val original = Locale.getDefault()
        try {
            // A decimal comma, and Arabic-Indic digits.
            listOf(Locale.GERMANY, Locale.forLanguageTag("ar-EG")).forEach { locale ->
                Locale.setDefault(locale)
                assertEquals(expected, setRouteScript(points, lineColor = LINE, casingColor = CASING), "$locale")
            }
        } finally {
            Locale.setDefault(original)
        }
        assertEquals(
            "window.setRoute && setRoute([48.137154,11.576124,48.200000,11.600000], '#3367D6', '#FFFFFF')",
            expected,
        )
    }

    @Test fun `a non-finite coordinate clears the route instead of reaching the script`() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { bad ->
            assertEquals(
                CLEAR_ROUTE_SCRIPT,
                setRouteScript(
                    listOf(RoutePoint(35.68, 139.76), RoutePoint(bad, 139.7), RoutePoint(35.7, 139.7)),
                    lineColor = LINE,
                    casingColor = CASING,
                ),
                "$bad",
            )
        }
    }

    @Test fun `an out-of-range coordinate clears the route`() {
        listOf(RoutePoint(90.5, 0.0), RoutePoint(0.0, -180.5)).forEach { bad ->
            assertEquals(
                CLEAR_ROUTE_SCRIPT,
                setRouteScript(listOf(RoutePoint(0.0, 0.0), bad), lineColor = LINE, casingColor = CASING),
                "$bad",
            )
        }
    }

    @Test fun `fewer than two points clear the route`() {
        listOf(emptyList(), listOf(RoutePoint(35.68, 139.76))).forEach { points ->
            assertEquals(CLEAR_ROUTE_SCRIPT, setRouteScript(points, lineColor = LINE, casingColor = CASING))
        }
    }

    @Test fun `the alternatives go out as one flat lat, lon array per route with their colour`() {
        assertEquals(
            "window.setRouteAlternatives && setRouteAlternatives(" +
                "[[35.681236,139.767125,35.689500,139.691700],[0.000010,-0.000020,0.500000,0.500000]], '#79747E')",
            setRouteAlternativesScript(
                listOf(
                    listOf(RoutePoint(35.681236, 139.767125), RoutePoint(35.6895, 139.6917)),
                    listOf(RoutePoint(0.00001, -0.00002), RoutePoint(0.5, 0.5)),
                ),
                color = MUTED,
            ),
        )
    }

    @Test fun `an alternative the page could not draw is left out`() {
        assertEquals(
            "window.setRouteAlternatives && setRouteAlternatives([[35.680000,139.760000,35.700000,139.700000]], " +
                "'#79747E')",
            setRouteAlternativesScript(
                listOf(
                    listOf(RoutePoint(35.68, 139.76), RoutePoint(Double.NaN, 139.7)),
                    listOf(RoutePoint(35.68, 139.76)),
                    listOf(RoutePoint(35.68, 139.76), RoutePoint(35.7, 139.7)),
                ),
                color = MUTED,
            ),
        )
    }

    @Test fun `no drawable alternative clears them`() {
        listOf(emptyList(), listOf(listOf(RoutePoint(0.0, 0.0), RoutePoint(91.0, 0.0)))).forEach { lines ->
            assertEquals(CLEAR_ROUTE_ALTERNATIVES_SCRIPT, setRouteAlternativesScript(lines, color = MUTED))
        }
    }
}

private const val LINE = "#3367D6"
private const val CASING = "#FFFFFF"
private const val MUTED = "#79747E"
