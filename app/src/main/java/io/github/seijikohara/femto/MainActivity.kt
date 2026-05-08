package io.github.seijikohara.femto

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.seijikohara.femto.data.AppsRepository
import io.github.seijikohara.femto.ui.drawer.AppDrawerRoute
import io.github.seijikohara.femto.ui.home.HomeEvent
import io.github.seijikohara.femto.ui.home.HomeRoute
import io.github.seijikohara.femto.ui.theme.FemtoTheme

class MainActivity : ComponentActivity() {
    private val appsRepository by lazy { AppsRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
            HomeEvent.OpenNotificationListenerSettings -> openNotificationListenerSettings()
        }
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

    private fun openNotificationListenerSettings() {
        val intent =
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityIfResolved(intent)
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
}
