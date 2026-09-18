package io.github.seijikohara.femto.data.diagnostics

import org.junit.Test
import kotlin.test.assertEquals

class LogTailRedactionTest {
    @Test
    fun `masks credential query values under the spellings tile providers use`() {
        val line =
            "E/chromium: Failed to load resource: https://h.test/s.json?key=AbC1&api-key=x2&access_token=t3&subscription_key=s4&v=2"
        assertEquals(
            "E/chromium: Failed to load resource: https://h.test/s.json?key=<redacted>&api-key=<redacted>" +
                "&access_token=<redacted>&subscription_key=<redacted>&v=2",
            line.redactSecretQueryParams(),
        )
    }

    @Test
    fun `leaves a line without credentials untouched`() {
        val line = "W/WebMapView: LIVE map transient error: AJAXError: Failed to fetch (0): https://h.test/planet"
        assertEquals(line, line.redactSecretQueryParams())
    }
}
