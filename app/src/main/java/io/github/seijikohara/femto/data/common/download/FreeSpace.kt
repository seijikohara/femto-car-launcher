package io.github.seijikohara.femto.data.common.download

import android.os.storage.StorageManager
import java.io.File
import java.io.IOException

/**
 * Free space a download leaves for the system and the app's other writers: the map converter's temporary files, the
 * trip log, the settings.
 */
internal const val FREE_SPACE_MARGIN_BYTES = 100_000_000L

/** Whether [remainingBytes] more fit in [freeBytes] with [FREE_SPACE_MARGIN_BYTES] left over. */
internal fun hasRoomFor(
    remainingBytes: Long,
    freeBytes: Long,
): Boolean = freeBytes - FREE_SPACE_MARGIN_BYTES >= remainingBytes

/**
 * The bytes the volume of [dir] can allocate, or 0 when the system cannot say.
 *
 * Counts the cached data the system can clear as well, which is the answer to "is there room for this download".
 * [dir] must exist (the no-backup directory always does), so its volume is found without walking up to an ancestor.
 */
internal fun StorageManager.allocatableBytesOrZero(dir: File): Long =
    try {
        getAllocatableBytes(getUuidForPath(dir))
    } catch (_: IOException) {
        0L
    }
