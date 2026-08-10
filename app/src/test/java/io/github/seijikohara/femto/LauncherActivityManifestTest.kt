package io.github.seijikohara.femto

import android.content.ComponentName
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Manifest facts the launcher's behaviour depends on, pinned where a reader can
 * see why they matter.
 *
 * A night-mode change is a configuration change, and an Activity that does not
 * declare it is destroyed and rebuilt: the dashboard blanks, the map WebView is
 * torn down and re-created, and the relaunch comes back through the splash — so
 * the theme looks like it changed *after* the launcher finished starting, which
 * is what a driver reported. Declaring `uiMode` keeps the content on screen and
 * lets Compose recompose the theme in place.
 *
 * The launch attributes travel with it: `singleTask` plus the HOME category is
 * what makes this a launcher at all (`AGENTS.md#launcher-behavior`), and
 * dropping either would change what the HOME button does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LauncherActivityManifestTest {
    private val activityInfo: ActivityInfo
        get() =
            ApplicationProvider
                .getApplicationContext<android.content.Context>()
                .let { context ->
                    context.packageManager.getActivityInfo(
                        ComponentName(context, MainActivity::class.java),
                        0,
                    )
                }

    @Test
    fun `a night mode change is handled without recreating the activity`() {
        assertTrue(
            activityInfo.configChanges and ActivityInfo.CONFIG_UI_MODE != 0,
            "MainActivity does not declare uiMode in configChanges, so a light/dark " +
                "flip relaunches the launcher through the splash",
        )
    }

    @Test
    fun `the launcher keeps a single task`() {
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, activityInfo.launchMode)
    }
}
