package io.github.seijikohara.femto.ui.settings.components

import android.content.Context
import android.text.format.Formatter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.update.UpdateFailure
import io.github.seijikohara.femto.ui.settings.AvailableVersion
import io.github.seijikohara.femto.ui.settings.SettingsAction
import io.github.seijikohara.femto.ui.settings.SettingsUiState
import io.github.seijikohara.femto.ui.settings.UpdateStatus
import io.github.seijikohara.femto.ui.settings.UpdateStep
import io.github.seijikohara.femto.ui.settings.UpdatesUiState
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * What the Updates section does with its state, beyond the ViewModel's
 * mapping: the "Available version" row names the offer or says the build is up
 * to date, the "Update to …" row reads each step of the one-tap update and
 * takes a tap only where one does something, a held install stays inert and
 * says why, a declined install grant says so and stays tappable, a retry shows
 * its size, the check row takes a tap only while a check can start, and the
 * "Updated to …" notice is acknowledged when the section leaves the screen —
 * never while it shows. Settings has no screenshot goldens, so nothing else
 * pins these.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UpdatesSectionTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val actions = mutableListOf<SettingsAction>()
    private val size = Formatter.formatShortFileSize(context, APK_BYTES)
    private val updateTitle = context.getString(R.string.settings_updates_update, VERSION)

    @Test
    fun `the available version row names the offered version and its size`() {
        setSection(offered(UpdateStep.Download(VERSION, APK_BYTES)))

        val summary = context.getString(R.string.settings_updates_available_summary, VERSION, size)
        rule.onNodeWithText(context.getString(R.string.settings_updates_available)).assertExists()
        rule.onNodeWithText(summary).assertExists()
    }

    @Test
    fun `the available version row says up to date after a check found nothing newer`() {
        setSection(
            UpdatesUiState.Initial.copy(
                status = UpdateStatus.Checked(lastAttemptAt = null),
                canCheck = true,
                availableVersion = AvailableVersion.UpToDate,
            ),
        )

        rule.onNodeWithText(context.getString(R.string.settings_updates_up_to_date)).assertExists()
    }

    @Test
    fun `no available version row before a check has found anything`() {
        setSection(UpdatesUiState.Initial.copy(status = UpdateStatus.NeverChecked, canCheck = true))

        rule.onNodeWithText(context.getString(R.string.settings_updates_available)).assertDoesNotExist()
    }

    @Test
    fun `the check row says it has never checked`() {
        setSection(UpdatesUiState.Initial.copy(status = UpdateStatus.NeverChecked, canCheck = true))

        rule.onNodeWithText(context.getString(R.string.settings_updates_status_never_checked)).assertExists()
    }

    @Test
    fun `the update row offers the download with its size and sends the update`() {
        setSection(offered(UpdateStep.Download(VERSION, APK_BYTES)))

        rule.onNodeWithText(context.getString(R.string.settings_updates_update_download_desc, size)).assertExists()
        rule.onNodeWithText(updateTitle).performClick()

        assertEquals(listOf<SettingsAction>(SettingsAction.DownloadUpdate), tapActions())
    }

    @Test
    fun `the update row counts the download up, draws its bar and takes no tap`() {
        setSection(offered(UpdateStep.Downloading(VERSION, fraction = 0.42f)))

        rule.onNodeWithText(context.getString(R.string.settings_updates_update_downloading, 42)).assertExists()
        rule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.42f, 0f..1f))).assertExists()
        rule.onNodeWithText(updateTitle).assertHasNoClickAction()
    }

    @Test
    fun `the update row after a verified download sends InstallUpdate`() {
        setSection(offered(UpdateStep.Install(VERSION, blockedWhileMoving = false, grantDeclined = false)))

        rule.onNodeWithText(context.getString(R.string.settings_updates_update_ready_desc)).assertExists()
        rule.onNodeWithText(updateTitle).performClick()

        assertEquals(listOf<SettingsAction>(SettingsAction.InstallUpdate), tapActions())
    }

    @Test
    fun `an install held while moving says why and takes no tap`() {
        setSection(offered(UpdateStep.Install(VERSION, blockedWhileMoving = true, grantDeclined = false)))

        rule.onNodeWithText(context.getString(R.string.settings_updates_parked_only)).assertExists()
        rule.onNodeWithText(updateTitle).assertHasNoClickAction()
    }

    @Test
    fun `a declined install grant says why, and the step still sends InstallUpdate`() {
        setSection(offered(UpdateStep.Install(VERSION, blockedWhileMoving = false, grantDeclined = true)))

        rule.onNodeWithText(context.getString(R.string.settings_updates_install_grant_declined)).assertExists()
        rule.onNodeWithText(updateTitle).performClick()

        assertEquals(listOf<SettingsAction>(SettingsAction.InstallUpdate), tapActions())
    }

    @Test
    fun `the update row reports the hand-off to the installer and takes no tap`() {
        setSection(offered(UpdateStep.Installing(VERSION)))

        rule.onNodeWithText(context.getString(R.string.settings_updates_update_installing)).assertExists()
        rule.onNodeWithText(updateTitle).assertHasNoClickAction()
    }

    @Test
    fun `a kept confirmation can be shown again`() {
        setSection(offered(UpdateStep.ShowInstallDialog(blockedWhileMoving = false, grantDeclined = false)))

        rule.onNodeWithText(context.getString(R.string.settings_updates_show_install_dialog)).assertHasClickAction()
    }

    @Test
    fun `a retry shows the size it may download`() {
        setSection(
            UpdatesUiState.Initial.copy(
                status = UpdateStatus.Failed(UpdateFailure.NETWORK),
                canCheck = true,
                availableVersion = AvailableVersion.Offered(VERSION, APK_BYTES),
                step = UpdateStep.Retry(VERSION, sizeBytes = APK_BYTES),
                updateOffered = true,
            ),
        )

        rule.onNodeWithText(context.getString(R.string.settings_updates_retry_desc, VERSION, size)).assertExists()
    }

    @Test
    fun `the check row sends a check while one can start`() {
        setSection(UpdatesUiState.Initial.copy(status = UpdateStatus.Checked(lastAttemptAt = null), canCheck = true))

        rule.onNodeWithText(context.getString(R.string.settings_updates_check)).performClick()

        assertEquals(listOf<SettingsAction>(SettingsAction.CheckForUpdates), tapActions())
    }

    @Test
    fun `the check row takes no tap while a check cannot start`() {
        setSection(UpdatesUiState.Initial.copy(status = UpdateStatus.Checking, canCheck = false))

        rule.onNodeWithText(context.getString(R.string.settings_updates_check)).assertHasNoClickAction()
    }

    @Test
    fun `the updated notice is acknowledged when the section leaves, not while it shows`() {
        var shown by mutableStateOf(true)
        val updates = UpdatesUiState.Initial.copy(updatedTo = "2026.09.25-1")
        rule.setContent {
            FemtoTheme {
                if (shown) {
                    UpdatesSection(
                        uiState = SettingsUiState.Initial.copy(updates = updates),
                        onAction = { actions += it },
                        onOpenDocument = {},
                    )
                }
            }
        }
        rule.waitForIdle()
        assertEquals(emptyList(), acknowledgements())

        rule.runOnIdle { shown = false }
        rule.waitForIdle()

        assertEquals(listOf<SettingsAction>(SettingsAction.AcknowledgeUpdatedTo), acknowledgements())
    }

    private fun setSection(updates: UpdatesUiState) {
        rule.setContent {
            FemtoTheme {
                UpdatesSection(
                    uiState = SettingsUiState.Initial.copy(updates = updates),
                    onAction = { actions += it },
                    onOpenDocument = {},
                )
            }
        }
        rule.waitForIdle()
    }

    // The actions a tap sent, apart from the section's own reports.
    private fun tapActions() = actions.filterNot { it == SettingsAction.AcknowledgeUpdatedTo }

    private fun acknowledgements() = actions.filter { it == SettingsAction.AcknowledgeUpdatedTo }

    private fun offered(step: UpdateStep) =
        UpdatesUiState(
            status = UpdateStatus.Checked(lastAttemptAt = null),
            canCheck = false,
            availableVersion = AvailableVersion.Offered(VERSION, APK_BYTES),
            step = step,
            autoCheck = true,
            updatedTo = null,
            updateOffered = true,
        )

    private companion object {
        const val VERSION = "2026.09.25-1"
        const val APK_BYTES = 45_310_215L
    }
}
