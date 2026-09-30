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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.testfixtures.fakeUpdateManifest
import io.github.seijikohara.femto.ui.settings.components.UpdatesSection
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The "Install unknown apps" round trip (Decision 11), driven through a
 * recording result registry: what a tap starts, what comes of the way back,
 * and where it falls back to the release page. Then which Settings actions go
 * through it: the install tap, and the one-tap update, whose download starts
 * only once the access is on, from the row's tap and from the dashboard
 * prompt's "Update" alike, so the access screen never opens a minute later,
 * when the download lands. While a fix shows the vehicle moving, the row's tap
 * only downloads and opens no access screen. The access and the device policy
 * are Robolectric's shadows of the platform's own checks.
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
    private val forwarded = mutableListOf<SettingsAction>()
    private lateinit var routeAction: (SettingsAction) -> Unit
    private val registryOwner =
        object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = registry
        }
    private val offer = fakeUpdateManifest(versionCode = 26092501)
    private val updateTitle = context.getString(R.string.settings_updates_update, offer.versionName)

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

    // --- The Settings actions routed through the access ---------------------------

    @Test
    fun `the one-tap update with the access off opens the access screen first and starts nothing`() {
        setAccess(on = false)
        setActions()

        rule.runOnIdle { routeAction(SettingsAction.StartUpdate) }

        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, (registry.launched.single() as Intent).action)
        assertEquals(emptyList(), forwarded)
    }

    @Test
    fun `the one-tap update starts once the access is turned on there`() {
        setAccess(on = false)
        setActions()
        rule.runOnIdle { routeAction(SettingsAction.StartUpdate) }

        setAccess(on = true)
        rule.runOnIdle { registry.dispatchResult(registry.lastRequestCode, Activity.RESULT_OK, null) }

        assertEquals(listOf<SettingsAction>(SettingsAction.StartUpdate), forwarded)
    }

    @Test
    fun `a one-tap update whose access was left off starts nothing and reports the decline`() {
        setAccess(on = false)
        setActions()
        rule.runOnIdle { routeAction(SettingsAction.StartUpdate) }

        rule.runOnIdle { registry.dispatchResult(registry.lastRequestCode, Activity.RESULT_CANCELED, null) }

        assertEquals(listOf<SettingsAction>(SettingsAction.InstallGrantDeclined), forwarded)
    }

    @Test
    fun `the one-tap update with the access on starts at once`() {
        setAccess(on = true)
        setActions()

        rule.runOnIdle { routeAction(SettingsAction.StartUpdate) }

        assertEquals(listOf<SettingsAction>(SettingsAction.StartUpdate), forwarded)
        assertTrue(registry.launched.isEmpty())
    }

    @Test
    fun `opened by the update prompt without the access, the access screen opens first and nothing starts`() {
        setAccess(on = false)

        setActions(startUpdate = true)

        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, (registry.launched.single() as Intent).action)
        assertEquals(emptyList(), forwarded)
    }

    @Test
    fun `opened by the update prompt, the update starts once the access is turned on there`() {
        setAccess(on = false)
        setActions(startUpdate = true)

        setAccess(on = true)
        rule.runOnIdle { registry.dispatchResult(registry.lastRequestCode, Activity.RESULT_OK, null) }

        assertEquals(listOf<SettingsAction>(SettingsAction.StartUpdate), forwarded)
    }

    @Test
    fun `opened by the update prompt, an access left off starts nothing and reports the decline`() {
        setAccess(on = false)
        setActions(startUpdate = true)

        rule.runOnIdle { registry.dispatchResult(registry.lastRequestCode, Activity.RESULT_CANCELED, null) }

        assertEquals(listOf<SettingsAction>(SettingsAction.InstallGrantDeclined), forwarded)
    }

    @Test
    fun `opened by the update prompt with the access on, the update starts at once`() {
        setAccess(on = true)

        setActions(startUpdate = true)

        assertEquals(listOf<SettingsAction>(SettingsAction.StartUpdate), forwarded)
        assertTrue(registry.launched.isEmpty())
    }

    @Test
    fun `the install tap goes through the access the same way`() {
        setAccess(on = false)
        setActions()
        rule.runOnIdle { routeAction(SettingsAction.InstallUpdate) }

        setAccess(on = true)
        rule.runOnIdle { registry.dispatchResult(registry.lastRequestCode, Activity.RESULT_OK, null) }

        assertEquals(listOf<SettingsAction>(SettingsAction.InstallUpdate), forwarded)
    }

    @Test
    fun `the one-tap update's install goes through the access and on with its token`() {
        setAccess(on = false)
        setActions()
        rule.runOnIdle { routeAction(SettingsAction.InstallOneTapUpdate(token = 7)) }
        assertEquals(emptyList(), forwarded)

        setAccess(on = true)
        rule.runOnIdle { registry.dispatchResult(registry.lastRequestCode, Activity.RESULT_OK, null) }

        assertEquals(listOf<SettingsAction>(SettingsAction.InstallOneTapUpdate(token = 7)), forwarded)
    }

    @Test
    fun `the one-tap update's install with the access on goes on at once with its token`() {
        setAccess(on = true)
        setActions()

        rule.runOnIdle { routeAction(SettingsAction.InstallOneTapUpdate(token = 7)) }

        assertEquals(listOf<SettingsAction>(SettingsAction.InstallOneTapUpdate(token = 7)), forwarded)
        assertTrue(registry.launched.isEmpty())
    }

    @Test
    fun `every other action passes straight through`() {
        setAccess(on = false)
        setActions()

        rule.runOnIdle { routeAction(SettingsAction.CheckForUpdates) }

        assertEquals(listOf<SettingsAction>(SettingsAction.CheckForUpdates), forwarded)
        assertTrue(registry.launched.isEmpty())
    }

    // --- The update row's tap --------------------------------------------------------

    @Test
    fun `while moving, a tap on the update row only downloads and opens no access screen`() {
        setAccess(on = false)
        setUpdateRow(downloadOnly = true)

        rule.onNodeWithText(updateTitle).performClick()

        assertEquals(listOf<SettingsAction>(SettingsAction.DownloadUpdate), tapped())
        assertTrue(registry.launched.isEmpty())
    }

    @Test
    fun `while parked, a tap on the update row asks for the access first, then starts the one-tap update`() {
        setAccess(on = false)
        setUpdateRow(downloadOnly = false)
        rule.onNodeWithText(updateTitle).performClick()
        assertEquals(emptyList(), tapped())

        setAccess(on = true)
        rule.runOnIdle { registry.dispatchResult(registry.lastRequestCode, Activity.RESULT_OK, null) }

        assertEquals(listOf<SettingsAction>(SettingsAction.StartUpdate), tapped())
    }

    private fun setAccess(on: Boolean) = shadowOf(context.packageManager).setCanRequestPackageInstalls(on)

    private fun setRoute() {
        rule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                installUpdate =
                    rememberInstallGrant(
                        onGrant = { outcomes += Outcome.INSTALL },
                        onDecline = { outcomes += Outcome.GRANT_DECLINED },
                        onUnavailable = { outcomes += Outcome.UNAVAILABLE },
                    )
            }
        }
        rule.waitForIdle()
    }

    // The Settings route's action wiring; [startUpdate] is Settings opened by
    // the dashboard's update prompt.
    private fun setActions(startUpdate: Boolean = false) {
        rule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                routeAction =
                    rememberInstallGrantedActions(
                        onAction = { forwarded += it },
                        onUnavailable = { outcomes += Outcome.UNAVAILABLE },
                        startUpdate = startUpdate,
                    )
            }
        }
        rule.waitForIdle()
    }

    // The Updates section's "Update to …" row on an offer, wired to the Settings
    // route's actions the way SettingsRoute wires it.
    private fun setUpdateRow(downloadOnly: Boolean) {
        val step =
            UpdateStep.Download(offer.versionName, offer.apk.size, grantDeclined = false, downloadOnly = downloadOnly)
        rule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                FemtoTheme {
                    UpdatesSection(
                        uiState = SettingsUiState.Initial.copy(updates = UpdatesUiState.Initial.copy(step = step)),
                        onAction =
                            rememberInstallGrantedActions(
                                onAction = { forwarded += it },
                                onUnavailable = { outcomes += Outcome.UNAVAILABLE },
                            ),
                        onOpenDocument = {},
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    // What the row's tap forwarded, apart from the section's report of coming on screen.
    private fun tapped() = forwarded.filterNot { it == SettingsAction.UpdatesShown }

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
