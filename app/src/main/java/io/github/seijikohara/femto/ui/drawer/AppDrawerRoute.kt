package io.github.seijikohara.femto.ui.drawer

import android.content.ComponentName
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.seijikohara.femto.data.AppEntry
import io.github.seijikohara.femto.data.AppsRepository

@Composable
internal fun AppDrawerRoute(
    onLaunch: (ComponentName) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    LaunchedEffect(Unit) {
        apps = runCatching { AppsRepository(context).queryApps() }.getOrDefault(emptyList())
    }
    BackHandler(onBack = onBack)
    AppDrawerScreen(apps = apps, onLaunch = onLaunch, modifier = modifier)
}
