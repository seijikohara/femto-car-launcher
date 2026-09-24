package io.github.seijikohara.femto.ui.settings.components

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.R
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
 * mapping: a held install step stays inert and says why, the check row takes
 * a tap only while a check can start, and the "Updated to …" notice is
 * acknowledged when the section leaves the screen — never while it shows.
 * Settings has no screenshot goldens, so nothing else pins these.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UpdatesSectionTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val actions = mutableListOf<SettingsAction>()

    @Test
    fun `an install held while moving says why and takes no tap`() {
        setSection(ready(UpdateStep.Install(blockedWhileMoving = true)))

        rule.onNodeWithText(context.getString(R.string.settings_updates_parked_only)).assertExists()
        rule.onNodeWithText(context.getString(R.string.settings_updates_install)).assertHasNoClickAction()
    }

    @Test
    fun `an open install step sends InstallUpdate`() {
        setSection(ready(UpdateStep.Install(blockedWhileMoving = false)))

        rule.onNodeWithText(context.getString(R.string.settings_updates_install)).performClick()

        assertEquals(listOf<SettingsAction>(SettingsAction.InstallUpdate), actions)
    }

    @Test
    fun `the check row sends a check while one can start`() {
        setSection(UpdatesUiState.Initial.copy(status = UpdateStatus.UpToDate(lastAttemptAt = null), canCheck = true))

        rule.onNodeWithText(context.getString(R.string.settings_updates_check)).performClick()

        assertEquals(listOf<SettingsAction>(SettingsAction.CheckForUpdates), actions)
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
        assertEquals(emptyList(), actions)

        rule.runOnIdle { shown = false }
        rule.waitForIdle()

        assertEquals(listOf<SettingsAction>(SettingsAction.AcknowledgeUpdatedTo), actions)
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

    private fun ready(step: UpdateStep) =
        UpdatesUiState(
            status = UpdateStatus.Ready("2026.09.25-1"),
            canCheck = false,
            step = step,
            autoCheck = true,
            updatedTo = null,
        )
}
