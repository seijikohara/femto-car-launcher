package io.github.seijikohara.femto

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.seijikohara.femto.data.AppsRepository
import io.github.seijikohara.femto.data.hasBluetoothConnectPermission
import io.github.seijikohara.femto.data.hasFineLocationPermission
import io.github.seijikohara.femto.data.hasReadCalendarPermission
import io.github.seijikohara.femto.ui.drawer.AppDrawerRoute
import io.github.seijikohara.femto.ui.home.HomeEvent
import io.github.seijikohara.femto.ui.home.HomeRoute
import io.github.seijikohara.femto.ui.theme.FemtoTheme

class MainActivity : ComponentActivity() {
    private val appsRepository by lazy { AppsRepository(this) }

    // Permission results are not consumed here — each repository self-checks
    // on every emit, so a late grant flows through naturally.
    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEmulatorMapRendering()
        enableEdgeToEdge()
        requestRuntimePermissions()
        setContent {
            FemtoTheme {
                var showDrawer by rememberSaveable { mutableStateOf(false) }
                if (showDrawer) {
                    AppDrawerRoute(
                        onLaunch = { component ->
                            appsRepository.launch(component)
                            showDrawer = false
                        },
                        onBack = { showDrawer = false },
                    )
                } else {
                    HomeRoute(
                        onEvent = { event ->
                            handleHomeEvent(event) { showDrawer = it }
                        },
                    )
                }
            }
        }
    }

    private fun handleHomeEvent(
        event: HomeEvent,
        setShowDrawer: (Boolean) -> Unit,
    ) {
        when (event) {
            HomeEvent.OpenDrawer -> setShowDrawer(true)
            is HomeEvent.LaunchComponent -> appsRepository.launch(event.component)
            is HomeEvent.LaunchAppCategory -> launchAppCategory(event.intentCategory)
            is HomeEvent.LaunchGeo -> launchGeo(event.latitude, event.longitude)
            HomeEvent.OpenNotificationListenerSettings -> openNotificationListenerSettings()
            HomeEvent.OpenSystemSettings -> openSystemSettings()
        }
    }

    private fun openSystemSettings() {
        val intent =
            Intent(Settings.ACTION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityIfResolved(intent)
    }

    private fun launchAppCategory(category: String) {
        // makeMainSelectorActivity defers picker to whichever app the user has
        // elected as the default for the given semantic category — works
        // across markets without a hard-coded package list.
        val intent =
            Intent
                .makeMainSelectorActivity(Intent.ACTION_MAIN, category)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityIfResolved(intent)
    }

    private fun launchGeo(
        latitude: Double,
        longitude: Double,
    ) {
        // A bare geo: URI lets whichever maps app the user has elected resolve
        // the position — no provider or package is hard-coded.
        val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:$latitude,$longitude?z=$MAPS_ZOOM_LEVEL"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityIfResolved(intent)
    }

    private fun openNotificationListenerSettings() {
        val intent =
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityIfResolved(intent)
    }

    /**
     * Request the runtime permissions the dashboard surfaces depend on.
     * Each permission is requested only if not already granted; BLUETOOTH_CONNECT
     * is requested only on Android 12+ where it became a runtime grant.
     */
    private fun requestRuntimePermissions() {
        val needed =
            buildList {
                if (!hasFineLocationPermission()) add(Manifest.permission.ACCESS_FINE_LOCATION)
                if (!hasReadCalendarPermission()) add(Manifest.permission.READ_CALENDAR)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothConnectPermission()) {
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                }
            }
        if (needed.isNotEmpty()) permissionsLauncher.launch(needed.toTypedArray())
    }

    /**
     * Start [intent] but no-op if no activity resolves. Other failures (e.g.
     * `SecurityException`) propagate so they surface as crashes rather than
     * silent dead clicks during development.
     */
    private fun startActivityIfResolved(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Head-unit has no app for this category / settings target.
        }
    }

    /**
     * Nudge MapLibre's OpenGL `EGLConfigChooser` onto its emulator-safe config
     * path when running on an emulator. The chooser only relaxes the EGL config
     * (dropping `EGL_COLOR_BUFFER_TYPE`, which the software GL translator cannot
     * present) when it detects an emulator, but its heuristics miss modern
     * `sdk_gphone` AVDs. It reads the JVM property `ro.kernel.qemu`; setting it
     * here — before any MapView builds its renderer — makes the map paint on the
     * emulator. This is a no-op on real head units (the heuristic stays false),
     * so production rendering is unchanged.
     */
    private fun enableEmulatorMapRendering() {
        if (isProbablyEmulator() && System.getProperty(QEMU_PROPERTY) == null) {
            System.setProperty(QEMU_PROPERTY, "1")
        }
    }
}

private fun isProbablyEmulator(): Boolean =
    Build.FINGERPRINT.startsWith("generic") ||
        Build.FINGERPRINT.contains("sdk_gphone") ||
        Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
        Build.MODEL.contains("sdk_gphone") ||
        Build.MODEL.contains("Emulator") ||
        Build.MODEL.contains("Android SDK built for") ||
        Build.HARDWARE in setOf("ranchu", "ranchu64", "goldfish", "goldfish_arm64") ||
        Build.PRODUCT.startsWith("sdk") ||
        Build.PRODUCT.contains("sdk_gphone") ||
        Build.BRAND.startsWith("generic")

private const val QEMU_PROPERTY = "ro.kernel.qemu"

// Street-level zoom for the geo: handoff — close enough to read nearby roads
// without dropping below neighbourhood context.
private const val MAPS_ZOOM_LEVEL = 15
