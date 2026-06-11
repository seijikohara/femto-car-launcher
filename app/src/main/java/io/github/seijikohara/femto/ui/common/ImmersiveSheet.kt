package io.github.seijikohara.femto.ui.common

import android.view.View
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Hide the system bars on the host [ModalBottomSheet][androidx.compose.material3.ModalBottomSheet]'s
 * own window while [fullscreen] is on. A modal sheet renders in a separate window
 * that does not inherit the Activity's immersive flags, so without this the
 * launcher's fullscreen visibly drops — the status / navigation bars reappear —
 * for as long as a sheet is open (`MainActivity.applyFullscreen` only governs the
 * Activity window). Mirrors the Activity's transient-swipe behaviour so a swipe
 * still reveals the bars and they auto-hide again.
 *
 * Call from inside the sheet's content lambda so [LocalView] resolves to the sheet
 * window. A no-op when [fullscreen] is off, or when no sheet/dialog window is found
 * (e.g. a preview).
 */
@Composable
internal fun ImmersiveSheetEffect(fullscreen: Boolean) {
    if (!fullscreen) return
    val view = LocalView.current
    LaunchedEffect(view) {
        val window = view.dialogWindowOrNull() ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

// Walk up to the modal-sheet / dialog window host, which exposes its Window
// through DialogWindowProvider.
private fun View.dialogWindowOrNull(): Window? {
    var ancestor = parent
    while (ancestor != null) {
        if (ancestor is DialogWindowProvider) return ancestor.window
        ancestor = ancestor.parent
    }
    return null
}
