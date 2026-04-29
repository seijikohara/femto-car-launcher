package io.github.seijikohara.femto.data

import android.content.ComponentName
import android.graphics.Bitmap
import androidx.compose.runtime.Immutable

/**
 * A launchable app exposed by the home screen.
 *
 * The icon is captured once as a [Bitmap] in the repository so the
 * UI layer can render it without holding `Drawable` references that
 * mutate at runtime.
 */
@Immutable
data class AppEntry(
    val componentName: ComponentName,
    val label: String,
    val icon: Bitmap,
)
