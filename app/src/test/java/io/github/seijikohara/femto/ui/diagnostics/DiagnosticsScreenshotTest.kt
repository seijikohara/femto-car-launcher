package io.github.seijikohara.femto.ui.diagnostics

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.seijikohara.femto.testfixtures.ScreenshotCompareOptions
import io.github.seijikohara.femto.testfixtures.fakeDiagnosticSections
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Diagnostics-screen goldens across a head-unit landscape geometry, initial
 * and problems-only. Same recording flow as the music / calendar / weather
 * panel goldens: goldens are recorded on CI and committed from the artifact
 * (macOS and Linux anti-alias differently, so a local record would not match
 * the CI runner).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class DiagnosticsScreenshotTest {
    @Test
    @Config(qualifiers = "w853dp-h512dp-mdpi")
    fun diagnostics_head_unit_initial() =
        capture("diagnostics-head-unit-853x512-initial") {
            DiagnosticsScreen(
                uiState = DiagnosticsUiState(sections = fakeDiagnosticSections(issue = true)),
                onAction = {},
                onBack = {},
                modifier = Modifier.fillMaxSize(),
            )
        }

    @Test
    @Config(qualifiers = "w853dp-h512dp-mdpi")
    fun diagnostics_head_unit_problems_only() =
        capture("diagnostics-head-unit-853x512-problems") {
            DiagnosticsScreen(
                uiState = DiagnosticsUiState(sections = fakeDiagnosticSections(issue = true), problemsOnly = true),
                onAction = {},
                onBack = {},
                modifier = Modifier.fillMaxSize(),
            )
        }

    private fun capture(
        name: String,
        content: @Composable () -> Unit,
    ) {
        captureRoboImage(filePath = "src/test/screenshots/$name.png", roborazziOptions = ScreenshotCompareOptions) {
            FemtoTheme { content() }
        }
    }
}
