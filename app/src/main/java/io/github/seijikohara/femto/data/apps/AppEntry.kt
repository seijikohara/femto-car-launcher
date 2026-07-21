package io.github.seijikohara.femto.data.apps

import android.content.ComponentName
import android.graphics.Bitmap
import androidx.compose.runtime.Immutable

/**
 * A launchable app exposed by the home screen.
 *
 * The icon is captured once as a [Bitmap] in the repository so the
 * UI layer can render it without holding `Drawable` references that
 * mutate at runtime.
 *
 * [isSystem] covers factory and updated-factory packages: neither can be
 * fully uninstalled by the user, so the drawer hides its Uninstall action
 * for them (the App-info page still offers what the system allows).
 */
@Immutable
internal data class AppEntry(
    val componentName: ComponentName,
    val label: String,
    val icon: Bitmap,
    val isSystem: Boolean = false,
)
