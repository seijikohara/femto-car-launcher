package io.github.seijikohara.femto.ui.home

import android.content.Context
import android.text.format.Formatter
import android.view.MotionEvent
import androidx.activity.ComponentDialog
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.testfixtures.fakeUpdateManifest
import io.github.seijikohara.femto.ui.home.components.GlassConfig
import io.github.seijikohara.femto.ui.home.components.MapConfig
import io.github.seijikohara.femto.ui.home.components.PanelVisibility
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals

/**
 * The dashboard's update prompt as the driver meets it: the dialog names the
 * version and its download size, each answer is a full-size touch target that
 * carries the version it answers, Back counts as Later while a tap beside the
 * dialog answers nothing, and a sheet over the dashboard holds the prompt
 * back. The dashboard goldens never carry a prompt, so nothing else
 * pins these. Same Robolectric harness as PanelDismissTest: no fix keeps the
 * map on its static fallback.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w853dp-h512dp-mdpi")
class UpdatePromptTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val actions = mutableListOf<HomeAction>()

    @Test
    fun `the prompt names the version and its download size`() {
        setHome()

        val size = Formatter.formatShortFileSize(context, UPDATE.apk.size)
        rule.onNodeWithText(context.getString(R.string.update_prompt_title)).assertExists()
        rule.onNodeWithText(context.getString(R.string.update_prompt_text, UPDATE.versionName, size)).assertExists()
    }

    @Test
    fun `Later answers for the version asked about`() {
        setHome()

        rule.onNodeWithText(context.getString(R.string.update_prompt_later)).performClick()

        assertEquals(listOf<HomeAction>(HomeAction.UpdateLater(UPDATE.versionCode)), answers())
    }

    @Test
    fun `Update answers for the version asked about`() {
        setHome()

        rule.onNodeWithText(context.getString(R.string.update_prompt_update)).performClick()

        assertEquals(listOf<HomeAction>(HomeAction.UpdateNow(UPDATE.versionCode)), answers())
    }

    @Test
    fun `Back counts as Later`() {
        setHome()

        rule.runOnIdle { (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        assertEquals(listOf<HomeAction>(HomeAction.UpdateLater(UPDATE.versionCode)), answers())
    }

    @Test
    fun `a tap beside the dialog answers nothing`() {
        // A stray tap near the map must not answer "never ask about this
        // version": only Back or a button does.
        setHome()

        rule.runOnIdle {
            // The dialog's window wraps its content, so a tap beside it arrives
            // at the window with coordinates outside its bounds.
            val dialog = ShadowDialog.getLatestDialog()
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
                MotionEvent.obtain(0L, 0L, action, -20f, -20f, 0).also { dialog.onTouchEvent(it) }.recycle()
            }
        }
        rule.waitForIdle()

        assertEquals(emptyList(), answers())
        rule.onNodeWithText(context.getString(R.string.update_prompt_title)).assertExists()
    }

    @Test
    fun `both answers are full-size touch targets`() {
        setHome()

        listOf(R.string.update_prompt_later, R.string.update_prompt_update).forEach { label ->
            rule
                .onNodeWithText(context.getString(label))
                .assertWidthIsAtLeast(FemtoDimens.MinTouchTarget)
                .assertHeightIsAtLeast(FemtoDimens.MinTouchTarget)
        }
    }

    @Test
    fun `a sheet over the dashboard holds the prompt back`() {
        // Over Settings, the Updates section may be showing (and recording as
        // seen) the very offer the prompt would ask about.
        setHome(sheetOpen = true)

        rule.onNodeWithText(context.getString(R.string.update_prompt_title)).assertDoesNotExist()
    }

    private fun setHome(sheetOpen: Boolean = false) {
        rule.setContent {
            FemtoTheme {
                HomeScreen(
                    uiState = HomeUiState.Initial,
                    is24Hour = true,
                    showClockSeconds = true,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    mapConfig = MapConfig(),
                    panels = PanelVisibility(),
                    glassConfig = GlassConfig(),
                    onAction = { actions += it },
                    updatePrompt = UPDATE,
                    sheetOpen = sheetOpen,
                )
            }
        }
        rule.waitForIdle()
    }

    private fun answers() = actions.filter { it is HomeAction.UpdateLater || it is HomeAction.UpdateNow }

    private companion object {
        val UPDATE = fakeUpdateManifest(versionCode = 26092501)
    }
}
