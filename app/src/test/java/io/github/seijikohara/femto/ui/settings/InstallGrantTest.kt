package io.github.seijikohara.femto.ui.settings

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.provider.Settings
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The install tap's "Install unknown apps" round trip (Decision 11), driven
 * through a recording result registry: what the tap starts, what comes of the
 * way back, and where it falls back to the release page. The access and the
 * device policy are Robolectric's shadows of the platform's own checks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InstallGrantTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val registry = RecordingRegistry()
    private val outcomes = mutableListOf<Outcome>()
    private lateinit var installUpdate: () -> Unit

    private enum class Outcome { INSTALL, GRANT_DECLINED, UNAVAILABLE }

    @Test
    fun `with the access on, the tap installs at once`() {
        setAccess(on = true)
        setRoute()

        rule.runOnIdle { installUpdate() }

        assertEquals(listOf(Outcome.INSTALL), outcomes)
        assertTrue(registry.launched.isEmpty())
    }

    @Test
    fun `without the access, the tap opens this app's access screen`() {
        setAccess(on = false)
        setRoute()

        rule.runOnIdle { installUpdate() }

        val intent = registry.launched.single() as Intent
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, intent.action)
        assertEquals("package:${context.packageName}", intent.dataString)
        assertEquals(emptyList(), outcomes)
    }

    @Test
    fun `access turned on there, the install goes ahead on the way back`() {
        setAccess(on = false)
        setRoute()
        rule.runOnIdle { installUpdate() }

        setAccess(on = true)
        rule.runOnIdle { registry.dispatchResult(registry.lastRequestCode, Activity.RESULT_OK, null) }

        assertEquals(listOf(Outcome.INSTALL), outcomes)
    }

    @Test
    fun `access left off there, the decline is reported`() {
        setAccess(on = false)
        setRoute()
        rule.runOnIdle { installUpdate() }

        rule.runOnIdle { registry.dispatchResult(registry.lastRequestCode, Activity.RESULT_CANCELED, null) }

        assertEquals(listOf(Outcome.GRANT_DECLINED), outcomes)
    }

    @Test
    fun `the way back reads the access again rather than the result code`() {
        // Leaving the screen with Back reports "cancelled" even after the toggle
        // was turned on, and OEM builds differ.
        setAccess(on = false)
        setRoute()
        rule.runOnIdle { installUpdate() }

        setAccess(on = true)
        rule.runOnIdle { registry.dispatchResult(registry.lastRequestCode, Activity.RESULT_CANCELED, null) }

        assertEquals(listOf(Outcome.INSTALL), outcomes)
    }

    @Test
    fun `a device policy against unknown sources falls back to the release page`() {
        setAccess(on = false)
        // Set the way an admin sets it: through DevicePolicyManager, which the
        // shadow records for UserManager to report.
        val admin = ComponentName(context, "io.github.seijikohara.femto.PolicyAdmin")
        val policies = context.getSystemService(DevicePolicyManager::class.java)
        shadowOf(policies).setProfileOwner(admin)
        policies.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
        setRoute()

        rule.runOnIdle { installUpdate() }

        assertEquals(listOf(Outcome.UNAVAILABLE), outcomes)
        assertTrue(registry.launched.isEmpty())
    }

    @Test
    fun `a ROM without the access screen falls back to the release page`() {
        setAccess(on = false)
        registry.launchFailure = ActivityNotFoundException("no settings screen for unknown sources")
        setRoute()

        rule.runOnIdle { installUpdate() }

        assertEquals(listOf(Outcome.UNAVAILABLE), outcomes)
    }

    private fun setAccess(on: Boolean) = shadowOf(context.packageManager).setCanRequestPackageInstalls(on)

    private fun setRoute() {
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = registry
        }
        rule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                installUpdate =
                    rememberInstallUpdate(
                        onInstall = { outcomes += Outcome.INSTALL },
                        onGrantDecline = { outcomes += Outcome.GRANT_DECLINED },
                        onUnavailable = { outcomes += Outcome.UNAVAILABLE },
                    )
            }
        }
        rule.waitForIdle()
    }

    // Records each launch instead of starting an activity, or fails it the way
    // the platform does when no activity answers the intent.
    private class RecordingRegistry : ActivityResultRegistry() {
        val launched = mutableListOf<Any?>()
        var lastRequestCode = -1
            private set
        var launchFailure: RuntimeException? = null

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            launchFailure?.let { throw it }
            lastRequestCode = requestCode
            launched += input
        }
    }
}
