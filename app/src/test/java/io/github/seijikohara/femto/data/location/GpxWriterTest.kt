package io.github.seijikohara.femto.data.location

import io.github.seijikohara.femto.testfixtures.fakeTrackPoint
import org.junit.Test
import java.io.StringWriter
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GpxWriterTest {
    @Test
    fun `splits tracks per trip and segments on a fix drought`() {
        val out = StringWriter()
        val gpx = GpxWriter(out)
        gpx.begin()
        gpx.point(fakeTrackPoint(tripId = 1L, timeMs = 0L))
        gpx.point(fakeTrackPoint(tripId = 1L, timeMs = 30_000L))
        // 90 s hole inside the same trip: new segment, same track.
        gpx.point(fakeTrackPoint(tripId = 1L, timeMs = 120_000L))
        // Reset boundary: new track.
        gpx.point(fakeTrackPoint(tripId = 2L, timeMs = 130_000L))
        gpx.end()

        val xml = out.toString()
        assertEquals(2, Regex("<trk>").findAll(xml).count())
        assertEquals(3, Regex("<trkseg>").findAll(xml).count())
        assertTrue(xml.contains("<name>Trip 1</name>"))
        assertTrue(xml.contains("<name>Trip 2</name>"))
        assertTrue(xml.trimEnd().endsWith("</gpx>"))
    }

    @Test
    fun `writes gpx 1_0 point elements from the recorded readings`() {
        val out = StringWriter()
        val gpx = GpxWriter(out)
        gpx.begin()
        gpx.point(
            fakeTrackPoint(
                latitude = 35.5,
                longitude = 139.25,
                timeMs = 0L,
                speedMps = 12.5f,
                bearingDeg = 270f,
                altitudeM = 47.0,
            ),
        )
        gpx.end()

        val xml = out.toString()
        assertTrue(xml.contains("<gpx version=\"1.0\""))
        assertTrue(xml.contains("<trkpt lat=\"35.5\" lon=\"139.25\">"))
        assertTrue(xml.contains("<ele>47.0</ele>"))
        assertTrue(xml.contains("<time>1970-01-01T00:00:00Z</time>"))
        assertTrue(xml.contains("<course>270.0</course>"))
        assertTrue(xml.contains("<speed>12.5</speed>"))
    }

    @Test
    fun `omits elements the chip never reported`() {
        val out = StringWriter()
        val gpx = GpxWriter(out)
        gpx.begin()
        gpx.point(fakeTrackPoint(speedMps = null, bearingDeg = null, altitudeM = null))
        gpx.end()

        val xml = out.toString()
        assertFalse(xml.contains("<ele>"))
        assertFalse(xml.contains("<course>"))
        assertFalse(xml.contains("<speed>"))
    }
}
