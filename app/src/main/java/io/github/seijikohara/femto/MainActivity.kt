package io.github.seijikohara.femto

import android.os.Bundle
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
import io.github.seijikohara.femto.ui.home.HomeRoute
import io.github.seijikohara.femto.ui.theme.FemtoTheme

class MainActivity : ComponentActivity() {
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
                            AppsRepository(this).launch(component)
                            showDrawer = false
                        },
                    )
                } else {
                    HomeRoute()
                }
            }
        }
    }
}
