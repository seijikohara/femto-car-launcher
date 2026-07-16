package io.github.seijikohara.femto.data.apps

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.pm.LauncherApps
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.testfixtures.fakeLauncherActivityInfo
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppsRepositoryTest {
    // Temporary CI benchmark: an extra @Test changes the compiled test bytecode so
    // testDebugUnitTest cannot be restored from the build cache, forcing it to run
    // (to measure the parallel forks). Reverted before merge.
    @Test
    fun `ci benchmark no-op`() = Unit

    private val application: Application = ApplicationProvider.getApplicationContext()

    /**
     * Register a launcher activity with the [LauncherApps] shadow. A resolvable
     * icon is registered too, because `queryApps` rasterizes every entry's icon
     * and the shadowed PackageManager resolves icons from this per-package map.
     */
    private fun installApp(
        packageName: String,
        label: String,
    ) {
        shadowOf(application.packageManager).setUnbadgedApplicationIcon(packageName, ColorDrawable(Color.RED))
        shadowOf(application.getSystemService(LauncherApps::class.java)).addActivity(
            Process.myUserHandle(),
            fakeLauncherActivityInfo(application, packageName, label = label),
        )
    }

    @Test
    fun `launch returns false and does not throw on ActivityNotFoundException`() {
        val repo = AppsRepository(application, launcher = { throw ActivityNotFoundException() })

        assertFalse(repo.launch(STALE_COMPONENT))
    }

    @Test
    fun `launch returns false and does not throw on SecurityException`() {
        // A non-exported or permission-guarded OEM activity must not crash HOME.
        val repo = AppsRepository(application, launcher = { throw SecurityException() })

        assertFalse(repo.launch(STALE_COMPONENT))
    }

    @Test
    fun `launch rethrows unexpected errors`() {
        // Failures outside the known dead-tap cases still surface as bugs.
        val repo = AppsRepository(application, launcher = { throw IllegalStateException() })

        assertFailsWith<IllegalStateException> { repo.launch(STALE_COMPONENT) }
    }

    @Test
    fun `launch returns true on success`() {
        val repo = AppsRepository(application, launcher = {})

        assertTrue(repo.launch(STALE_COMPONENT))
    }

    @Test
    fun `queryApps resolves labels and sorts them alphabetically ignoring case`() =
        runTest {
            // Registered out of order, with a lowercase label that would sort
            // last under a case-sensitive comparison.
            installApp("com.example.zebra", label = "Zebra")
            installApp("com.example.alpha", label = "alpha")
            installApp("com.example.mango", label = "Mango")

            val labels = AppsRepository(application).queryApps().map { it.label }

            assertEquals(listOf("alpha", "Mango", "Zebra"), labels)
        }

    @Test
    fun `queryApps maps the launcher component name`() =
        runTest {
            installApp("com.example.solo", label = "Solo")

            val entry = AppsRepository(application).queryApps().single()

            assertEquals(ComponentName("com.example.solo", "com.example.solo.MainActivity"), entry.componentName)
        }

    @Test
    fun `queryApps drops an entry that fails to resolve and keeps the rest`() =
        runTest {
            installApp("com.example.good", label = "Good")
            // No label and no ApplicationInfo: label resolution throws, which is
            // the pathological-package case the per-app runCatching isolates.
            shadowOf(application.getSystemService(LauncherApps::class.java)).addActivity(
                Process.myUserHandle(),
                fakeLauncherActivityInfo(
                    application,
                    "com.example.broken",
                    label = null,
                    hasApplicationInfo = false,
                ),
            )

            val labels = AppsRepository(application).queryApps().map { it.label }

            assertEquals(listOf("Good"), labels)
        }

    private companion object {
        val STALE_COMPONENT = ComponentName("com.example", "X")
    }
}
