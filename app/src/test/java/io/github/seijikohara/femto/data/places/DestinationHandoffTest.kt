package io.github.seijikohara.femto.data.places

import android.app.Application
import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The destination hand-off's intent, the check that an app takes it, and the
 * launch intent built through the package manager. The check must ask with the
 * action and scheme the launch sends, or the panel could block a hand-off that
 * works. The launcher's manifest declares its own `geo:` entrance, which
 * Robolectric resolves like any other activity, so every test here runs with
 * the launcher among the handlers and pins that it never counts. Robolectric
 * does not apply package visibility, so the manifest's `<queries>` entry is not
 * covered here; [GeoHandoffChoiceTest] covers the rule's branches.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DestinationHandoffTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `the hand-off views the geo URI of its target`() {
        val target = PlaceTarget.Point(35.681236, 139.767125)

        val intent = destinationIntent(target, label = "Home")

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(geoHandoffUri(target, label = "Home"), intent.dataString)
    }

    @Test
    fun `the launcher's own geo entrance is no app that takes a destination`() {
        assertFalse(application.hasDestinationHandler())
    }

    @Test
    fun `an app that filters on the geo scheme takes a destination`() {
        installMapsApp()

        assertTrue(application.hasDestinationHandler())
    }

    @Test
    fun `with only the launcher's own entrance there is nothing to start`() {
        assertNull(application.geoLaunchIntentOrNull(destinationIntent(PlaceTarget.Query("Airport"))))
    }

    @Test
    fun `the one other app is started explicitly with the hand-off's URI`() {
        val maps = installMapsApp()
        val view = destinationIntent(PlaceTarget.Point(35.681236, 139.767125), label = "Home")

        val launch = assertNotNull(application.geoLaunchIntentOrNull(view))

        assertEquals(
            listOf(maps.flattenToString(), view.dataString, Intent.FLAG_ACTIVITY_NEW_TASK),
            listOf(
                launch.component?.flattenToString(),
                launch.dataString,
                launch.flags and Intent.FLAG_ACTIVITY_NEW_TASK,
            ),
        )
    }

    private fun installMapsApp(): ComponentName {
        val maps = ComponentName("com.example.maps", "com.example.maps.MapActivity")
        shadowOf(application.packageManager).run {
            addActivityIfNotPresent(maps)
            addIntentFilterForActivity(
                maps,
                IntentFilter(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addDataScheme("geo")
                },
            )
        }
        return maps
    }

    @Test
    fun `a lookup that fails reads as an app that takes the destination`() {
        // A wrong "no app" would block a hand-off that works, so the check
        // fails open.
        val broken =
            object : ContextWrapper(application) {
                override fun getPackageManager(): PackageManager = throw IllegalStateException("package manager died")
            }

        assertTrue(broken.hasDestinationHandler())
    }
}
