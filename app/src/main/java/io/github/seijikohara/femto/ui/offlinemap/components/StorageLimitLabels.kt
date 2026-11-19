package io.github.seijikohara.femto.ui.offlinemap.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.map.offline.OfflineStorageLimit

// Decimal, like the limit itself (OfflineStorageLimit).
private const val BYTES_PER_GB = 1_000_000_000L

/** The limit as its choice names it, e.g. "No limit" or "5 GB". */
@Composable
internal fun storageLimitLabel(limit: OfflineStorageLimit): String =
    limit.bytes?.let { limitLabel(it) } ?: stringResource(R.string.offline_regions_limit_none)

/**
 * A limit in whole gigabytes, e.g. "5 GB": every limit is one, and the storage line and the dialogs name it the way
 * the choice does rather than as a formatted file size.
 */
@Composable
internal fun limitLabel(bytes: Long): String = stringResource(R.string.offline_regions_limit_gb, bytes / BYTES_PER_GB)
