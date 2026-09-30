package io.github.seijikohara.femto.ui.common

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * A download size in the device's locale, e.g. "45 MB": the update's size as
 * the Updates section's rows and the dashboard's update prompt state it.
 */
@Composable
internal fun fileSize(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)
