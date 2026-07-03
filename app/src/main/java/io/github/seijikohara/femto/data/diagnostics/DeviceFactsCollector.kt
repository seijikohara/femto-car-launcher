package io.github.seijikohara.femto.data.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// getprop returns nothing useful for a property the ROM doesn't define, and
// the process itself can fail on a locked-down build; either way the row
// still needs a value, so absence renders as this sentinel rather than the
// section silently dropping the fact.
private const val PROP_UNAVAILABLE = "unavailable"

/** Collects the DEVICE diagnostics section. */
internal class DeviceFactsCollector(
    private val context: Context,
) {
    suspend fun deviceFacts(): SectionPayload.Facts =
        withContext(Dispatchers.IO) {
            SectionPayload.Facts(
                buildList {
                    add(DiagnosticFact("Device", FactValue.Text("${Build.MANUFACTURER} ${Build.MODEL}")))
                    add(
                        DiagnosticFact(
                            "Android",
                            FactValue.Text("${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
                        ),
                    )
                    add(DiagnosticFact("Build id", FactValue.Text(Build.DISPLAY)))
                    add(DiagnosticFact("Fingerprint", FactValue.Text(Build.FINGERPRINT)))
                    add(
                        DiagnosticFact(
                            "Product / device / board",
                            FactValue.Text("${Build.PRODUCT} / ${Build.DEVICE} / ${Build.BOARD}"),
                        ),
                    )
                    add(
                        DiagnosticFact(
                            "Hardware / brand",
                            FactValue.Text("${Build.HARDWARE} / ${Build.BRAND}"),
                        ),
                    )
                    add(DiagnosticFact("SoC", FactValue.Text("${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}")))
                    add(DiagnosticFact("Security patch", FactValue.Text(Build.VERSION.SECURITY_PATCH)))
                    add(DiagnosticFact("Incremental", FactValue.Text(Build.VERSION.INCREMENTAL)))
                    add(DiagnosticFact("ABIs", FactValue.Text(Build.SUPPORTED_ABIS.joinToString())))
                    add(
                        DiagnosticFact(
                            "Radio",
                            FactValue.Text(Build.getRadioVersion().takeUnless { it.isNullOrBlank() } ?: "none"),
                        ),
                    )
                    add(buildTypeTagsFact())
                    add(DiagnosticFact("Performance class", FactValue.Text(performanceClassLabel())))
                    add(DiagnosticFact("Device features", FactValue.Text(deviceFeaturesLabel())))
                    add(DiagnosticFact("ro.treble.enabled", FactValue.Text(getprop("ro.treble.enabled"))))
                    add(
                        DiagnosticFact(
                            "ro.build.characteristics",
                            FactValue.Text(getprop("ro.build.characteristics")),
                        ),
                    )
                    add(DiagnosticFact("ro.board.platform", FactValue.Text(getprop("ro.board.platform"))))
                },
            )
        }

    // A build off the shipped "user" type or signed with the public test-keys
    // is a hacked/eng firmware — worth a warning since it changes what the
    // rest of the diagnostics can be trusted to mean.
    private fun buildTypeTagsFact(): DiagnosticFact {
        val hackedOrEngFirmware = Build.TYPE != "user" || Build.TAGS?.contains("test-keys") == true
        return DiagnosticFact(
            "Build type / tags",
            FactValue.Status(
                "${Build.TYPE} / ${Build.TAGS}",
                if (hackedOrEngFirmware) FactHealth.WARNING else FactHealth.OK,
            ),
        )
    }

    private fun performanceClassLabel(): String =
        Build.VERSION.MEDIA_PERFORMANCE_CLASS.let { performanceClass ->
            if (performanceClass == 0) "0 (none)" else performanceClass.toString()
        }

    private fun deviceFeaturesLabel(): String {
        val packageManager = context.packageManager
        return listOf(
            "automotive" to PackageManager.FEATURE_AUTOMOTIVE,
            "leanback" to PackageManager.FEATURE_LEANBACK,
            "touchscreen" to PackageManager.FEATURE_TOUCHSCREEN,
        ).joinToString { (label, feature) -> "$label=${packageManager.hasSystemFeature(feature)}" }
    }

    // Same ProcessBuilder + runCatching degrade idiom as the logcat tail in
    // data/system/DiagnosticsRepository.kt: a getprop failure or a property
    // the ROM never set is a datum ("unavailable"), never a crash.
    private fun getprop(name: String): String =
        runCatching {
            ProcessBuilder("getprop", name)
                .redirectErrorStream(true)
                .start()
                .inputStream
                .bufferedReader()
                .useLines { it.firstOrNull()?.trim() }
        }.getOrNull()
            .takeUnless { it.isNullOrBlank() }
            ?: PROP_UNAVAILABLE
}
