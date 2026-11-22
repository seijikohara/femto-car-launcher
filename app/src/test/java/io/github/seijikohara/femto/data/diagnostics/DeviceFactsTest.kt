package io.github.seijikohara.femto.data.diagnostics

import android.app.Application
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertContains
import kotlin.test.assertIs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DeviceFactsTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `device features report picture-in-picture and freeform support as the device declares them`() =
        runTest {
            // Both combinations, so a hard-coded value cannot pass.
            listOf(true to false, false to true).forEach { (pip, freeform) ->
                shadowOf(application.packageManager).apply {
                    setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, pip)
                    setSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT, freeform)
                }

                val features = deviceFeatures()

                assertContains(features, "pip=$pip")
                assertContains(features, "freeform=$freeform")
            }
        }

    private suspend fun deviceFeatures(): String =
        DeviceFactsCollector(application)
            .deviceFacts()
            .facts
            .single { it.label == "Device features" }
            .value
            .let { assertIs<FactValue.Text>(it).value }
}
