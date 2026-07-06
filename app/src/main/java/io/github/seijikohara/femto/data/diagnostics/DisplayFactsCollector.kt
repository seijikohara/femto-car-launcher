package io.github.seijikohara.femto.data.diagnostics

import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowInsets
import android.view.WindowManager
import androidx.core.content.getSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.hypot

// A display outside this plausible range means xdpi/ydpi is fake (a common
// aftermarket-firmware shortcut of always reporting 160dpi) rather than the
// diagonal being genuinely a phone-sized sliver or a cinema screen.
private const val MIN_PLAUSIBLE_DIAGONAL_INCHES = 3.0
private const val MAX_PLAUSIBLE_DIAGONAL_INCHES = 30.0

/**
 * Builds the primitive-input rows of the DISPLAY section: window bounds,
 * density (current + stable), physical dpi/diagonal, dp geometry, font
 * scale, and orientation/night mode. Pure and free of any live
 * Display/WindowManager/Configuration object so [DisplayFactsTest] can pin
 * the density-mismatch and diagonal derivations on the JVM.
 */
internal fun displayGeometryFacts(
    boundsPx: String,
    maxBoundsPx: String,
    densityDpi: Int,
    stableDensityDpi: Int,
    density: Float,
    xdpi: Float,
    ydpi: Float,
    widthPx: Int,
    heightPx: Int,
    screenWidthDp: Int,
    screenHeightDp: Int,
    smallestScreenWidthDp: Int,
    fontScale: Float,
    orientationPortrait: Boolean,
    nightMode: Boolean,
): List<DiagnosticFact> =
    listOf(
        DiagnosticFact("Window bounds", FactValue.Text("$boundsPx px (max $maxBoundsPx)")),
        DiagnosticFact("Density", FactValue.Text("$densityDpi dpi (density $density)")),
        stableDensityFact(stableDensityDpi, densityDpi),
        DiagnosticFact(
            "Physical dpi",
            FactValue.Text("x ${"%.1f".format(Locale.ROOT, xdpi)} / y ${"%.1f".format(Locale.ROOT, ydpi)}"),
        ),
        physicalDiagonalFact(widthPx, heightPx, xdpi, ydpi),
        DiagnosticFact(
            "dp geometry",
            FactValue.Text("${screenWidthDp}x$screenHeightDp dp (smallest $smallestScreenWidthDp dp)"),
        ),
        fontScaleFact(fontScale),
        DiagnosticFact(
            "Orientation / night mode",
            FactValue.Text(
                "${if (orientationPortrait) "portrait" else "landscape"} / ${if (nightMode) "night" else "day"}",
            ),
        ),
    )

// A Settings > Display-size override desyncs the current density from the
// device's stable baseline; surfacing both values (not just current) is what
// makes an "everything looks tiny/huge" bug report explicable at a glance.
private fun stableDensityFact(
    stableDensityDpi: Int,
    currentDensityDpi: Int,
): DiagnosticFact =
    if (stableDensityDpi != currentDensityDpi) {
        DiagnosticFact(
            "Stable density",
            FactValue.Status(
                "$stableDensityDpi dpi (current $currentDensityDpi — overridden)",
                FactHealth.WARNING,
            ),
        )
    } else {
        DiagnosticFact("Stable density", FactValue.Status("$stableDensityDpi dpi", FactHealth.OK))
    }

// Some aftermarket firmware reports a fixed, wrong xdpi/ydpi (e.g. always
// 160), which makes the dpi-derived diagonal wildly implausible for any car
// display or phone — flag it rather than silently trusting a fake value.
private fun physicalDiagonalFact(
    widthPx: Int,
    heightPx: Int,
    xdpi: Float,
    ydpi: Float,
): DiagnosticFact {
    val diagonalInches = hypot(widthPx / xdpi.toDouble(), heightPx / ydpi.toDouble())
    val value = "%.1f\"".format(Locale.ROOT, diagonalInches)
    val health =
        if (diagonalInches in MIN_PLAUSIBLE_DIAGONAL_INCHES..MAX_PLAUSIBLE_DIAGONAL_INCHES) {
            FactHealth.INFO
        } else {
            FactHealth.WARNING
        }
    return DiagnosticFact("Physical diagonal", FactValue.Status(value, health))
}

private fun fontScaleFact(fontScale: Float): DiagnosticFact =
    DiagnosticFact(
        "Font scale",
        FactValue.Status(fontScale.toString(), if (fontScale == 1.0f) FactHealth.OK else FactHealth.WARNING),
    )

/** Collects the DISPLAY diagnostics section. */
internal class DisplayFactsCollector(
    private val context: Context,
) {
    suspend fun displayFacts(): SectionPayload.Facts =
        withContext(Dispatchers.IO) {
            val displayManager = context.getSystemService<DisplayManager>()!!
            val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
            // The application context can't query window metrics; a
            // display-scoped context is the only way to reach
            // WindowManager.currentWindowMetrics/maximumWindowMetrics.
            val displayContext = context.createDisplayContext(display)
            val windowManager = displayContext.getSystemService<WindowManager>()!!
            val current = windowManager.currentWindowMetrics
            val maximum = windowManager.maximumWindowMetrics
            val metrics = displayContext.resources.displayMetrics
            val configuration = displayContext.resources.configuration

            SectionPayload.Facts(
                buildList {
                    addAll(
                        displayGeometryFacts(
                            boundsPx = "${current.bounds.width()}x${current.bounds.height()}",
                            maxBoundsPx = "${maximum.bounds.width()}x${maximum.bounds.height()}",
                            densityDpi = metrics.densityDpi,
                            stableDensityDpi = DisplayMetrics.DENSITY_DEVICE_STABLE,
                            density = metrics.density,
                            xdpi = metrics.xdpi,
                            ydpi = metrics.ydpi,
                            widthPx = maximum.bounds.width(),
                            heightPx = maximum.bounds.height(),
                            screenWidthDp = configuration.screenWidthDp,
                            screenHeightDp = configuration.screenHeightDp,
                            smallestScreenWidthDp = configuration.smallestScreenWidthDp,
                            fontScale = configuration.fontScale,
                            orientationPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT,
                            nightMode =
                                (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                                    Configuration.UI_MODE_NIGHT_YES,
                        ),
                    )
                    add(
                        DiagnosticFact(
                            "Refresh rate",
                            FactValue.Text("%.0f Hz".format(Locale.ROOT, display.refreshRate)),
                        ),
                    )
                    add(
                        DiagnosticFact(
                            "Display modes",
                            FactValue.Text(
                                display.supportedModes.joinToString {
                                    "${it.physicalWidth}x${it.physicalHeight}@" +
                                        "${"%.0f".format(Locale.ROOT, it.refreshRate)}"
                                },
                            ),
                        ),
                    )
                    add(
                        DiagnosticFact(
                            "HDR / wide gamut",
                            FactValue.Text("${display.isHdr} / ${configuration.isScreenWideColorGamut}"),
                        ),
                    )
                    add(cutoutInsetsFact(current.windowInsets))
                    add(
                        DiagnosticFact(
                            "Displays",
                            FactValue.Text(
                                displayManager.displays.let { displays ->
                                    "${displays.size}: ${
                                        displays.joinToString { "${it.displayId}:${it.name}" }
                                    }"
                                },
                            ),
                        ),
                    )
                    add(DiagnosticFact("Screen timeout", FactValue.Text(screenTimeoutLabel(displayContext))))
                    add(DiagnosticFact("Brightness mode", FactValue.Text(brightnessModeLabel(displayContext))))
                },
            )
        }

    private fun cutoutInsetsFact(windowInsets: WindowInsets): DiagnosticFact {
        val statusBarInsets = windowInsets.getInsets(WindowInsets.Type.statusBars())
        val navBarInsets = windowInsets.getInsets(WindowInsets.Type.navigationBars())
        return DiagnosticFact(
            "Cutout / insets",
            FactValue.Text(
                "cutout=${windowInsets.displayCutout != null}, " +
                    "statusBar=${statusBarInsets.top}px, navBar=${navBarInsets.bottom}px",
            ),
        )
    }

    private fun screenTimeoutLabel(displayContext: Context): String =
        Settings.System
            .getInt(displayContext.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, -1)
            .takeIf { it >= 0 }
            ?.let { "${it / 1000} s" }
            ?: "unknown"

    private fun brightnessModeLabel(displayContext: Context): String =
        when (Settings.System.getInt(displayContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, -1)) {
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC -> "automatic"
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL -> "manual"
            else -> "unknown"
        }
}
