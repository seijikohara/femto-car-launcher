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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.seijikohara.femto.data.AppsRepository
import io.github.seijikohara.femto.data.ClockSetting
import io.github.seijikohara.femto.data.DisplayPreferences
import io.github.seijikohara.femto.data.DisplaySettings
import io.github.seijikohara.femto.data.FontPreferences
import io.github.seijikohara.femto.data.FullscreenSetting
import io.github.seijikohara.femto.data.SystemPermissionSignals
import io.github.seijikohara.femto.data.ThemeMode
import io.github.seijikohara.femto.data.hasBluetoothConnectPermission
import io.github.seijikohara.femto.data.hasFineLocationPermission
import io.github.seijikohara.femto.data.hasReadCalendarPermission
import io.github.seijikohara.femto.data.hasReadPhoneStatePermission
import io.github.seijikohara.femto.ui.assistant.AssistantOption
import io.github.seijikohara.femto.ui.assistant.AssistantSheet
import io.github.seijikohara.femto.ui.drawer.AppDrawerSheet
import io.github.seijikohara.femto.ui.home.HomeEvent
import io.github.seijikohara.femto.ui.home.HomeRoute
import io.github.seijikohara.femto.ui.home.components.MapConfig
import io.github.seijikohara.femto.ui.home.components.PanelVisibility
import io.github.seijikohara.femto.ui.locale.resolved
import io.github.seijikohara.femto.ui.settings.SettingsRoute
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.FontTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val appsRepository by lazy { AppsRepository(this) }
    private val displayPreferences by lazy { DisplayPreferences(this) }
    private val fontPreferences by lazy { FontPreferences(this) }

    // Cache the latest fullscreen choice so [onWindowFocusChanged] can re-hide the
    // system bars when focus returns from another Activity. The Compose
    // LaunchedEffect only fires on a setting change, not on a focus regain, so the
    // bars would otherwise stay visible after returning from an external app.
    private var fullscreenSetting = FullscreenSetting.OFF

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
        // Keep the cached fullscreen choice in sync so onWindowFocusChanged can
        // re-hide the bars after focus returns from another Activity.
        lifecycleScope.launch {
            displayPreferences.settings
                .map { it.fullscreen }
                .distinctUntilChanged()
                .collect { fullscreenSetting = it }
        }
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
            // Drive the system bars from the persisted fullscreen choice. This
            // fires only on a setting change; onWindowFocusChanged handles the
            // focus-regain case.
            LaunchedEffect(display.fullscreen) {
                applyFullscreen(display.fullscreen)
            }
            FemtoTheme(fontTheme = fontTheme, accent = display.accentColor, darkTheme = darkTheme) {
                // The dashboard stays composed; the app drawer and assistant are
                // bottom-sheet overlays and settings is a full destination over it.
                var showDrawer by rememberSaveable { mutableStateOf(false) }
                var showAssistant by rememberSaveable { mutableStateOf(false) }
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
                        showClockSeconds = display.showClockSeconds,
                        speedUnit = display.speedUnit.resolved(),
                        temperatureUnit = display.temperatureUnit.resolved(),
                        mapConfig =
                            MapConfig(
                                fps = display.mapFps,
                                style = display.mapStyle,
                                tiltDeg = display.mapTiltDeg,
                                zoom = display.mapZoom,
                                renderPercent = display.mapRenderPercent,
                                renderMode = display.mapRenderMode,
                                lookAheadM = display.mapLookAheadM,
                            ),
                        panels =
                            PanelVisibility(
                                calendar = display.showCalendar,
                                weather = display.showWeather,
                                music = display.showMusic,
                            ),
                        onEvent = { event ->
                            handleHomeEvent(
                                event = event,
                                setShowDrawer = { showDrawer = it },
                                setShowAssistant = { showAssistant = it },
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
                    if (showAssistant) {
                        AssistantSheet(
                            onLaunchOption = { option ->
                                launchAssistantOption(option)
                                showAssistant = false
                            },
                            onDismiss = { showAssistant = false },
                        )
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Returning from another Activity (e.g. an external app) restores the
        // system bars even when fullscreen is on. Re-hide them once focus returns.
        if (hasFocus && fullscreenSetting == FullscreenSetting.ON) {
            applyFullscreen(FullscreenSetting.ON)
        }
    }

    // Hide or show both the status and navigation bars per the fullscreen choice.
    // ON uses the transient-swipe behaviour so the bars reappear on a swipe and
    // auto-hide again, matching an immersive-sticky launcher experience.
    private fun applyFullscreen(setting: FullscreenSetting) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        when (setting) {
            FullscreenSetting.ON -> {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }

            FullscreenSetting.OFF -> {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    private fun handleHomeEvent(
        event: HomeEvent,
        setShowDrawer: (Boolean) -> Unit,
        setShowAssistant: (Boolean) -> Unit,
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

            HomeEvent.OpenAssistantSheet -> {
                // Close the drawer overlay so the two bottom sheets never stack.
                setShowDrawer(false)
                setShowAssistant(true)
            }
        }
    }

    // Fire the intent matching the user's elected assistant option. No package is
    // hard-coded, so each action defers to whichever app the user has elected and
    // works across markets and OEM assistants. A missing handler is a silent
    // no-op (tryStartActivity swallows ActivityNotFoundException).
    private fun launchAssistantOption(option: AssistantOption) {
        val action =
            when (option) {
                AssistantOption.ASSISTANT -> Intent.ACTION_ASSIST
                AssistantOption.VOICE_COMMAND -> Intent.ACTION_VOICE_COMMAND
                AssistantOption.VOICE_SEARCH -> Intent.ACTION_WEB_SEARCH
            }
        tryStartActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

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
     * Start [intent], returning whether an activity handled it. Fire-and-forget
     * callers ignore the result. [ActivityNotFoundException] means the head unit
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
