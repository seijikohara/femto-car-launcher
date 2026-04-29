package io.github.seijikohara.femto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.seijikohara.femto.ui.home.HomeRoute
import io.github.seijikohara.femto.ui.theme.FemtoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FemtoTheme {
                HomeRoute()
            }
        }
    }
}
