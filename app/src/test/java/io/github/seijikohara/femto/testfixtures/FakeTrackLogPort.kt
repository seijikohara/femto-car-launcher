package io.github.seijikohara.femto.testfixtures

import android.net.Uri
import io.github.seijikohara.femto.ui.settings.TrackLogPort

/**
 * In-memory [TrackLogPort]: records every call and returns the configured
 * results, so ViewModel tests can drive the export/delete flows without a
 * database or ContentResolver.
 */
internal class FakeTrackLogPort(
    var exportResult: Long? = 0L,
    var clearResult: Boolean = true,
) : TrackLogPort {
    val exportedTo = mutableListOf<Uri>()

    var clearCalls: Int = 0
        private set

    override suspend fun exportTo(uri: Uri): Long? {
        exportedTo += uri
        return exportResult
    }

    override suspend fun clearHistory(): Boolean {
        clearCalls += 1
        return clearResult
    }
}
