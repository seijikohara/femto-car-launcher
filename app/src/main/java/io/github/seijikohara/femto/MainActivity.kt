package io.github.seijikohara.femto

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.seijikohara.femto.data.AppsRepository
import io.github.seijikohara.femto.data.ClockSetting
import io.github.seijikohara.femto.data.DisplayPreferences
import io.github.seijikohara.femto.data.DisplaySettings
import io.github.seijikohara.femto.data.FontPreferences
import io.github.seijikohara.femto.data.SystemPermissionSignals
import io.github.seijikohara.femto.data.ThemeMode
import io.github.seijikohara.femto.data.hasBluetoothConnectPermission
import io.github.seijikohara.femto.data.hasFineLocationPermission
import io.github.seijikohara.femto.data.hasReadCalendarPermission
import io.github.seijikohara.femto.data.hasReadPhoneStatePermission
import io.github.seijikohara.femto.ui.drawer.AppDrawerSheet
import io.github.seijikohara.femto.ui.home.HomeEvent
import io.github.seijikohara.femto.ui.home.HomeRoute
import io.github.seijikohara.femto.ui.locale.resolved
import io.github.seijikohara.femto.ui.settings.SettingsRoute
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.FontTheme

class MainActivity : ComponentActivity() {
    private val appsRepository by lazy { AppsRepository(this) }
    private val displayPreferences by lazy { DisplayPreferences(this) }
    private val fontPreferences by lazy { FontPreferences(this) }

    // Emit on the process-wide refresh signal so permission-gated flows (e.g.
    // the Bluetooth footer indicator) re-read after a late runtime grant.
    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            SystemPermissionSignals.refreshes.tryEmit(Unit)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEmulatorMapRendering()
        enableEdgeToEdge()
        requestRuntimePermissions()
        setContent {
            // User display settings override the locale/system defaults; the
            // theme + font feed FemtoTheme, the units + clock feed the dashboard.
            val display by displayPreferences.settings.collectAsStateWithLifecycle(
                initialValue = DisplaySettings.Default,
            )
            val fontTheme by fontPreferences.fontTheme.collectAsStateWithLifecycle(initialValue = FontTheme.INTER)
            val darkTheme =
                when (display.themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            FemtoTheme(fontTheme = fontTheme, darkTheme = darkTheme) {
                // The dashboard stays composed; the app drawer is a bottom-sheet
                // overlay and settings is a full destination over it.
                var showDrawer by rememberSaveable { mutableStateOf(false) }
                var showSettings by rememberSaveable { mutableStateOf(false) }
                if (showSettings) {
                    SettingsRoute(
                        onBack = { showSettings = false },
                        onOpenNotificationAccess = ::openNotificationListenerSettings,
                        onOpenSystemSettings = ::openSystemSettings,
                    )
                } else {
                    HomeRoute(
                        is24Hour = resolveIs24Hour(display.clock),
                        speedUnit = display.speedUnit.resolved(),
                        temperatureUnit = display.temperatureUnit.resolved(),
                        onEvent = { event ->
                            handleHomeEvent(
                                event = event,
                                setShowDrawer = { showDrawer = it },
                                setShowSettings = { showSettings = it },
                            )
                        },
                    )
                    if (showDrawer) {
                        AppDrawerSheet(
                            onLaunch = { component ->
                                appsRepository.launch(component)
                                showDrawer = false
                            },
                            onDismiss = { showDrawer = false },
                        )
                    }
                }
            }
        }
    }

    private fun handleHomeEvent(
        event: HomeEvent,
        setShowDrawer: (Boolean) -> Unit,
        setShowSettings: (Boolean) -> Unit,
    ) {
        when (event) {
            HomeEvent.OpenDrawer -> {
                setShowDrawer(true)
            }

            is HomeEvent.LaunchComponent -> {
                appsRepository.launch(event.component)
            }

            is HomeEvent.LaunchAppCategory -> {
                launchAppCategory(event.intentCategory)
            }

            is HomeEvent.LaunchGeo -> {
                launchGeo(event.latitude, event.longitude)
            }

            HomeEvent.OpenNotificationListenerSettings -> {
                openNotificationListenerSettings()
            }

            HomeEvent.OpenInAppSettings -> {
                // Close the drawer overlay so it does not re-appear behind settings
                // when the user navigates back.
                setShowDrawer(false)
                setShowSettings(true)
            }

            HomeEvent.OpenAssistant -> {
                openAssistant()
            }
        }
    }

    // Defer to whichever assistant the user has elected (ACTION_ASSIST),
    // falling back to the generic voice-command intent. No package is
    // hard-coded, so it works across markets and OEM assistants. `||`
    // short-circuits: the fallback fires only when no assistant resolves.
    private fun openAssistant() =
        tryStartActivity(Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) ||
            tryStartActivity(Intent(Intent.ACTION_VOICE_COMMAND).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    private fun resolveIs24Hour(clock: ClockSetting): Boolean =
        when (clock) {
            ClockSetting.AUTO -> DateFormat.is24HourFormat(this)
            ClockSetting.TWELVE_HOUR -> false
            ClockSetting.TWENTY_FOUR_HOUR -> true
        }

    private fun openSystemSettings() {
        val intent =
            Intent(Settings.ACTION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        tryStartActivity(intent)
    }

    private fun launchAppCategory(category: String) {
        // makeMainSelectorActivity defers picker to whichever app the user has
        // elected as the default for the given semantic category — works
        // across markets without a hard-coded package list.
        val intent =
            Intent
                .makeMainSelectorActivity(Intent.ACTION_MAIN, category)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        tryStartActivity(intent)
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
        tryStartActivity(intent)
    }

    private fun openNotificationListenerSettings() {
        val intent =
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        tryStartActivity(intent)
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
                if (!hasReadPhoneStatePermission()) add(Manifest.permission.READ_PHONE_STATE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothConnectPermission()) {
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                }
            }
        if (needed.isNotEmpty()) permissionsLauncher.launch(needed.toTypedArray())
    }

    /**
     * Start [intent], returning whether an activity handled it. Callers that
     * chain alternatives consume the result (the assistant fallback); fire-and-
     * forget callers ignore it. [ActivityNotFoundException] means the head unit
     * has no app for the target — a silent no-op rather than a dead-click crash;
     * any other failure (e.g. `SecurityException`) propagates.
     */
    private fun tryStartActivity(intent: Intent): Boolean =
        try {
            startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
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
