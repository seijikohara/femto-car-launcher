package io.github.seijikohara.femto

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri
import io.github.seijikohara.femto.data.places.PlaceTarget
import io.github.seijikohara.femto.data.places.geoHandoffUri
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * A failed launch is logged at WARN, and the diagnostics report collects the
 * app's WARN lines into a report the user shares. The platform's
 * ActivityNotFoundException message embeds the Intent, whose data keeps an
 * opaque `geo:` URI's coordinates, query and label, so the log line must name
 * only the target and the exception class.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LaunchFailureLogTest {
    private val intent =
        Intent(
            Intent.ACTION_VIEW,
            geoHandoffUri(PlaceTarget.Point(35.681236, 139.767125), label = "Home").toUri(),
        )

    // The message the platform builds: it carries the whole Intent.
    private val failure = ActivityNotFoundException("No Activity found to handle $intent")

    @Test
    fun the_log_line_carries_no_part_of_the_destination() {
        val line = launchFailureLogLine("no handler for", intent, failure)
        listOf("geo", "35.68", "139.76", "Home").forEach { secret ->
            assertFalse(secret in line, "log line leaks \"$secret\": $line")
        }
    }

    @Test
    fun the_log_line_names_the_target_and_the_exception_class() =
        assertEquals(
            "no handler for android.intent.action.VIEW: ActivityNotFoundException",
            launchFailureLogLine("no handler for", intent, failure),
        )
}
