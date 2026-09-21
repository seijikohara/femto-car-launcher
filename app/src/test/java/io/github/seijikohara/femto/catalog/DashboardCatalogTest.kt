package io.github.seijikohara.femto.catalog

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.seijikohara.femto.testfixtures.DashboardFixtures
import io.github.seijikohara.femto.testfixtures.MapBackdrop
import io.github.seijikohara.femto.ui.home.components.DashboardScaffold
import io.github.seijikohara.femto.ui.home.components.GlassConfig
import io.github.seijikohara.femto.ui.home.components.MapConfig
import io.github.seijikohara.femto.ui.home.components.PanelVisibility
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.BeforeClass
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Renders the dashboard once per [CatalogEntry] — every geometry × display
 * size × theme × driver side × dock position — for the screenshot catalog on
 * the project website. Not a regression test: nothing is compared, the PNGs
 * are build output (`CatalogRun.OUTPUT_DIR`), and the docs workflow is the
 * consumer. `@Category(CatalogGeneration)` keeps it out of `test`; run it with
 * `./gradlew :app:generateCatalog [-Pfemto.catalog.filter=<regex>]`.
 *
 * Same rendering caveats as the goldens: Robolectric's rasterizer skips the
 * glass blur, downloadable fonts fall back to the system face, and the map is
 * the still capture from [MapBackdrop].
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
@Category(CatalogGeneration::class)
internal class DashboardCatalogTest(
    private val entry: CatalogEntry,
) {
    @Test
    fun renders_the_dashboard_for_the_entry() {
        // Robolectric applies a runtime qualifier change to resources it creates
        // afterwards, so it must precede captureRoboImage, which launches the
        // host Activity. Robolectric 4.17 caches the sandbox per configuration
        // and shares it across parameters, but ParameterizedRobolectricTestRunner
        // still runs each parameter through its own RobolectricTestRunner
        // instance, so no stale window carries over.
        RuntimeEnvironment.setQualifiers(entry.geometry.qualifiers)
        captureRoboImage(filePath = "${CatalogRun.OUTPUT_DIR}/${entry.file}") {
            FemtoTheme(uiScale = entry.scale, darkTheme = entry.darkTheme) {
                DashboardScaffold(
                    uiState = DashboardFixtures.state,
                    is24Hour = true,
                    showClockSeconds = true,
                    speedUnit = SpeedUnit.KILOMETERS_PER_HOUR,
                    temperatureUnit = TemperatureUnit.CELSIUS,
                    mapConfig = MapConfig(),
                    panels = PanelVisibility(),
                    glassConfig = GlassConfig(),
                    onAction = {},
                    modifier = Modifier.fillMaxSize(),
                    dockPosition = entry.dockPosition,
                    driverSide = entry.driverSide,
                    clock = DashboardFixtures.fixedClock,
                    mapSurface = { _, config -> MapBackdrop(darkTheme = entry.darkTheme, mapConfig = config) },
                )
            }
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun entries(): List<Array<Any>> = CatalogMatrix.entries(CatalogRun.filter).map { arrayOf<Any>(it) }

        // The manifest lists exactly the entries this run renders (the filter
        // applies to both), so a partial local run still produces a consistent
        // catalog directory. Guarded by existence, not just by @BeforeClass:
        // ParameterizedRobolectricTestRunner (a JUnit Suite) creates one
        // RobolectricTestRunner per parameter, and each one replays the
        // sandboxed class-level lifecycle, so @BeforeClass actually runs once
        // per entry rather than once per class. The guard is safe because
        // generateCatalog's doFirst wipes OUTPUT_DIR before the run starts, so
        // a manifest found here can only be the one an earlier parameter in
        // this same run wrote.
        @JvmStatic
        @BeforeClass
        fun writeManifest() {
            val manifestFile = File(CatalogRun.OUTPUT_DIR, CatalogRun.MANIFEST_FILE)
            if (!manifestFile.exists()) {
                val manifest = CatalogMatrix.manifest(
                    CatalogMatrix.entries(CatalogRun.filter),
                    Instant.now().truncatedTo(ChronoUnit.SECONDS),
                    CatalogRun.gitSha,
                )
                manifestFile
                    .apply { parentFile?.mkdirs() }
                    .writeText(CatalogJson.encodeToString(CatalogManifest.serializer(), manifest))
            }
        }
    }
}
