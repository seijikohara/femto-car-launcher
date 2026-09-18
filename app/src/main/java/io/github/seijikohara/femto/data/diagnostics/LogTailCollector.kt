package io.github.seijikohara.femto.data.diagnostics

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
internal class LogTailCollector {
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
                                it.addLast(line.redactSecretQueryParams())
                            }
                        }.toList()
                }
        }.onFailure { Log.w(TAG, "self logcat read failed; diagnostics omit the log tail", it) }
            .getOrDefault(emptyList())
}

// The report is made to be shared, and the WebView logs every failed resource
// load with its full URL — which, for the user's own Google Maps key or a
// custom style URL, carries the credential in the query. Mask those values;
// the same parameter names the map page redacts (bridge.ts redactSecrets).
internal fun String.redactSecretQueryParams(): String = SECRET_QUERY_PARAM.replace(this, "$1<redacted>")

private val SECRET_QUERY_PARAM =
    Regex(
        """([?&](?:access[-_]?token|api[-_]?key|subscription[-_]?key|key|token)=)[^&#\s"']+""",
        RegexOption.IGNORE_CASE,
    )
