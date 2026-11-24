package io.github.seijikohara.femto.ui.offlinemap.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.map.offline.RegionDownload
import io.github.seijikohara.femto.data.map.offline.RegionDownloadFailure
import io.github.seijikohara.femto.data.map.offline.RegionDownloadPhase
import io.github.seijikohara.femto.ui.common.fileSize
import io.github.seijikohara.femto.ui.offlinemap.OfflineRegionsAction
import io.github.seijikohara.femto.ui.settings.components.SettingRowHorizontalInset
import io.github.seijikohara.femto.ui.settings.components.SettingRowVerticalInset
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoLinearProgressIndicator
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark

/**
 * The one download, in whichever phase it is: running with its bar and both sizes, being checked, paused with its
 * reason and the bytes it holds, or failed with its reason. Only a running or checked download is titled
 * Downloading; a paused or failed one carries the region's name alone. Each phase offers only the buttons that do
 * something in it; Cancel asks first (the ViewModel opens the confirmation), and Dismiss clears a failure, which
 * left nothing on disk.
 */
@Composable
internal fun RegionDownloadRow(
    download: RegionDownload,
    onAction: (OfflineRegionsAction) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier =
        modifier
            .fillMaxWidth()
            .padding(horizontal = SettingRowHorizontalInset, vertical = SettingRowVerticalInset),
    verticalArrangement = Arrangement.spacedBy(4.dp),
) {
    Text(
        text = downloadTitle(download),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    when (download.phase) {
        RegionDownloadPhase.RUNNING -> {
            // Not announced byte by byte: the bar reports the progress to accessibility services itself.
            Line(text = progressText(download))
            FemtoLinearProgressIndicator(
                progress = { download.fraction() },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
            Buttons {
                DownloadButton(
                    labelRes = R.string.offline_regions_download_cancel,
                    onClick = { onAction(OfflineRegionsAction.CancelDownload) },
                )
            }
        }

        RegionDownloadPhase.VERIFYING -> {
            Line(text = stringResource(R.string.offline_regions_download_verifying), announced = true)
        }

        RegionDownloadPhase.PAUSED -> {
            // A download restored after a restart carries no reason, so it says only that it is paused; its sizes
            // say what is left.
            Line(
                text =
                    download.failure?.let { failureText(it) }
                        ?: stringResource(R.string.offline_regions_download_paused),
                announced = true,
            )
            Line(text = progressText(download))
            Buttons {
                DownloadButton(
                    labelRes = R.string.offline_regions_download_resume,
                    onClick = { onAction(OfflineRegionsAction.ResumeDownload) },
                )
                DownloadButton(
                    labelRes = R.string.offline_regions_download_cancel,
                    onClick = { onAction(OfflineRegionsAction.CancelDownload) },
                )
            }
        }

        RegionDownloadPhase.FAILED -> {
            download.failure?.let {
                Line(text = failureText(it), announced = true, color = MaterialTheme.colorScheme.error)
            }
            Buttons {
                DownloadButton(
                    labelRes = R.string.offline_regions_download_dismiss,
                    onClick = { onAction(OfflineRegionsAction.DismissDownload) },
                )
            }
        }
    }
}

// Nothing runs in a paused or failed download, a restored one included, so only a transfer or its check says
// Downloading.
@Composable
private fun downloadTitle(download: RegionDownload): String =
    when (download.phase) {
        RegionDownloadPhase.RUNNING, RegionDownloadPhase.VERIFYING -> {
            stringResource(R.string.offline_regions_download_title, download.name)
        }

        RegionDownloadPhase.PAUSED, RegionDownloadPhase.FAILED -> {
            download.name
        }
    }

// Two Longs to the end: a 58 GB file overflows an Int, so the fraction is taken in floating point.
private fun RegionDownload.fraction(): Float =
    if (totalBytes > 0) (receivedBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f) else 0f

@Composable
private fun progressText(download: RegionDownload): String =
    stringResource(
        R.string.offline_regions_download_progress,
        fileSize(download.receivedBytes),
        fileSize(download.totalBytes),
    )

@Composable
private fun failureText(failure: RegionDownloadFailure): String =
    stringResource(
        when (failure) {
            RegionDownloadFailure.NETWORK -> R.string.offline_regions_failure_network
            RegionDownloadFailure.STORAGE -> R.string.offline_regions_failure_storage
            RegionDownloadFailure.NO_SPACE -> R.string.offline_regions_failure_no_space
            RegionDownloadFailure.OVER_LIMIT -> R.string.offline_regions_failure_over_limit
            RegionDownloadFailure.GONE -> R.string.offline_regions_failure_gone
            RegionDownloadFailure.CORRUPT -> R.string.offline_regions_failure_corrupt
        },
    )

// [announced] marks a line that is itself an outcome, read out as it appears.
@Composable
private fun Line(
    text: String,
    modifier: Modifier = Modifier,
    announced: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) = Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = color,
    modifier = if (announced) modifier.semantics { liveRegion = LiveRegionMode.Polite } else modifier,
)

@Composable
private fun Buttons(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    verticalAlignment = Alignment.CenterVertically,
) {
    content()
}

@Composable
private fun DownloadButton(
    @StringRes labelRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = TextButton(
    onClick = onClick,
    modifier = modifier.heightIn(min = FemtoDimens.MinTouchTarget),
) {
    Text(text = stringResource(labelRes))
}

@PreviewLightDark
@Composable
private fun PausedDownloadRowPreview() {
    FemtoTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            RegionDownloadRow(
                download =
                    RegionDownload(
                        regionId = "bayern",
                        name = "Bayern",
                        receivedBytes = 412_000_000L,
                        totalBytes = 1_043_341_312L,
                        phase = RegionDownloadPhase.PAUSED,
                        failure = RegionDownloadFailure.NETWORK,
                    ),
                onAction = {},
            )
        }
    }
}
