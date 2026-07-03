package io.github.seijikohara.femto.ui.diagnostics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.diagnostics.DiagnosticFact
import io.github.seijikohara.femto.data.diagnostics.DiagnosticSection
import io.github.seijikohara.femto.data.diagnostics.FactHealth
import io.github.seijikohara.femto.data.diagnostics.FactValue
import io.github.seijikohara.femto.data.diagnostics.SectionId
import io.github.seijikohara.femto.data.diagnostics.SectionPayload
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DiagnosticsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val networkTitle = context.getString(R.string.diagnostics_section_network)
    private val appTitle = context.getString(R.string.diagnostics_section_app)
    private val expandNetworkLabel = context.getString(R.string.diagnostics_expand_section, networkTitle)
    private val expandLogsLabel =
        context.getString(
            R.string.diagnostics_expand_section,
            context.getString(R.string.diagnostics_section_logs, 1),
        )
    private val copyLabel = context.getString(R.string.diagnostics_copy)
    private val copiedLabel = context.getString(R.string.diagnostics_copied)

    private val healthyNetworkSection =
        DiagnosticSection(
            SectionId.NETWORK,
            SectionPayload.Facts(
                listOf(DiagnosticFact("Connectivity", FactValue.Status("Online (WIFI)", FactHealth.OK))),
            ),
        )
    private val failingNetworkSection =
        DiagnosticSection(
            SectionId.NETWORK,
            SectionPayload.Facts(
                listOf(DiagnosticFact("Connectivity", FactValue.Status("OFFLINE", FactHealth.ERROR))),
            ),
        )
    private val healthyAppSection =
        DiagnosticSection(
            SectionId.APP,
            SectionPayload.Facts(listOf(DiagnosticFact("Version", FactValue.Text("1.0 (debug)")))),
        )
    private val logsSection =
        DiagnosticSection(
            SectionId.LOGS,
            SectionPayload.LogTail(listOf("W FemtoTag: something failed")),
        )

    @Test
    fun healthy_section_starts_collapsed_and_expands_on_tap() {
        setScreen(DiagnosticsUiState(sections = listOf(healthyNetworkSection)))
        rule.onNodeWithText("Online (WIFI)").assertDoesNotExist()
        rule.onNodeWithContentDescription(expandNetworkLabel).performClick()
        rule.onNodeWithText("Online (WIFI)").assertIsDisplayed()
    }

    @Test
    fun error_section_starts_expanded() {
        setScreen(DiagnosticsUiState(sections = listOf(failingNetworkSection)))
        rule.onNodeWithText("OFFLINE").assertIsDisplayed()
    }

    @Test
    fun badge_shows_issue_count() {
        setScreen(DiagnosticsUiState(sections = listOf(failingNetworkSection)))
        rule.onNodeWithText("1").assertIsDisplayed()
    }

    @Test
    fun problems_only_hides_healthy_sections() {
        setScreen(
            DiagnosticsUiState(
                sections = listOf(healthyAppSection, failingNetworkSection),
                problemsOnly = true,
            ),
        )
        rule.onNodeWithText(appTitle).assertDoesNotExist()
        rule.onNodeWithText(networkTitle).assertIsDisplayed()
    }

    @Test
    fun copy_button_fires_copy_report() {
        val actions = mutableListOf<DiagnosticsAction>()
        setScreen(DiagnosticsUiState(sections = listOf(healthyAppSection)), onAction = { actions += it })
        rule.onNodeWithText(copyLabel).performClick()
        assertEquals(listOf<DiagnosticsAction>(DiagnosticsAction.CopyReport), actions)
    }

    @Test
    fun copy_button_confirms_while_copy_confirmed() {
        setScreen(DiagnosticsUiState(sections = listOf(healthyAppSection), copyConfirmed = true))
        rule.onNodeWithText(copiedLabel).assertIsDisplayed()
    }

    @Test
    fun log_line_renders_when_expanded() {
        setScreen(DiagnosticsUiState(sections = listOf(logsSection)))
        rule.onNodeWithContentDescription(expandLogsLabel).performClick()
        rule.onNodeWithText("W FemtoTag: something failed").assertIsDisplayed()
    }

    private fun setScreen(
        uiState: DiagnosticsUiState,
        onAction: (DiagnosticsAction) -> Unit = {},
    ) = rule.setContent {
        FemtoTheme {
            DiagnosticsScreen(
                uiState = uiState,
                onAction = onAction,
                onBack = {},
            )
        }
    }
}
