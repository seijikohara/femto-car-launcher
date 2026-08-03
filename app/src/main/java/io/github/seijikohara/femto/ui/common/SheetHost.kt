package io.github.seijikohara.femto.ui.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Host the launcher's modal sheets at the platform density.
 *
 * A modal sheet renders in its own dialog window, and that window's Compose root
 * re-provides `LocalDensity` from the platform — so `FemtoTheme`'s UI-scale and
 * font-size override never reaches sheet *content* in the first place. It does
 * reach the sheet's dialog, which Material 3 holds in a `remember` keyed on the
 * ambient density: every step of the Display-size or Font-size slider therefore
 * tore the open sheet's window down and built a new one mid-drag, resetting its
 * scroll position, its selected category and its immersive flags, and cancelling
 * the touch stream the user was still dragging with.
 *
 * Handing the sheets the density their content will actually run at keeps that
 * key stable, so the window survives the whole gesture. It also puts
 * [rememberSheetHeight] on the right density — it is read out here but applied
 * inside the sheet, so a scaled reading made the sheet the wrong fraction of the
 * viewport at every UI scale but `MEDIUM`.
 *
 * This deliberately keeps sheets out of the user's scale settings, which is the
 * behaviour they have always had; making sheet content honour the scale is a
 * separate product change, and it must not go through `LocalDensity` at the
 * dialog boundary.
 */
@Composable
internal fun ModalSheetHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    CompositionLocalProvider(
        // The same value AndroidComposeView installs inside the dialog.
        LocalDensity provides remember(context) { platformDensity(context) },
        content = content,
    )
}

private fun platformDensity(context: Context): Density =
    Density(
        context.resources.displayMetrics.density,
        context.resources.configuration.fontScale,
    )
