package io.github.seijikohara.femto.data.diagnostics

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "LogTailCollector"

// Enough tail to cover the warnings of a feature being exercised right now
// without turning the report into a novel.
private const val MAX_LOG_LINES = 80

/**
 * Collects the LOGS section: the newest warning-level lines of the app's own
 * logcat. Relies on `logcat -d` returning the calling app's own lines without
 * `READ_LOGS` (uid-filtered since Android 4.1); on builds that restrict even
 * that, the section degrades to empty with one WARN.
 */
internal class LogTailCollector(
    private val context: Context,
) {
    suspend fun logTail(): SectionPayload.LogTail =
        withContext(Dispatchers.IO) {
            SectionPayload.LogTail(recentWarningLinesOrEmpty())
        }

    private fun recentWarningLinesOrEmpty(): List<String> =
        runCatching {
            ProcessBuilder("logcat", "-d", "-v", "time", "*:W")
                .redirectErrorStream(true)
                .start()
                .inputStream
                .bufferedReader()
                // Stream into a bounded tail: the dump can run to megabytes
                // on a chatty device, and only the newest lines matter.
                .useLines { lines ->
                    lines
                        .fold(ArrayDeque<String>(MAX_LOG_LINES)) { tail, line ->
                            tail.also {
                                if (it.size == MAX_LOG_LINES) it.removeFirst()
                                it.addLast(line)
                            }
                        }.toList()
                }
        }.onFailure { Log.w(TAG, "self logcat read failed; diagnostics omit the log tail", it) }
            .getOrDefault(emptyList())
}
