package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.common.download.ProbeResult
import io.github.seijikohara.femto.data.common.download.RemoteFile
import io.github.seijikohara.femto.data.common.download.RemoteFileFeed
import io.github.seijikohara.femto.data.common.download.RevalidationResult
import io.github.seijikohara.femto.data.common.download.TransferResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/** One [FakeRemoteFileFeed.download] call: what was asked for, and what the part held when it was. */
internal data class RequestedFileDownload(
    val url: String,
    val part: File,
    val expected: RemoteFile,
    val heldBytes: Long,
)

/**
 * How a [FakeRemoteFileFeed] download stops before the end: the part's length then, and the result. A null length
 * deletes the part, as the transport does for a missing file or a range that no longer matches.
 */
internal data class FakeDownloadStop(
    val partBytes: Long?,
    val result: TransferResult,
)

/**
 * In-memory [RemoteFileFeed] for the stores built on the file transport; it keeps the transport's contract on the part
 * file.
 *
 * [probe] answers from [probeAnswers] by URL, and `Unavailable` for any other. [download] stands for a server that
 * sends [sentFile], or the expected file when that is null: another file goes through `onRestart` first with the old
 * part still on disk, as the transport calls it. A refusal deletes the part; an acceptance waits at the gate of
 * [gateRestart], if one is set, and only then opens the part from its start, which truncates it, as the transport does
 * when it opens its output stream. The part then grows halfway to its end, which is reported; the download waits at
 * the gate of [gateDownload], if one is set; and the part grows to its end — the whole file, or the length of [stop] —
 * which is reported again. With [gateOutlivesCancel] set, the wait at that gate goes on after the caller is cancelled,
 * so the second report comes after the cancel. Lengths are set without writing bytes, so a part can stand for
 * gigabytes.
 */
internal open class FakeRemoteFileFeed : RemoteFileFeed {
    val probeAnswers = mutableMapOf<String, ProbeResult>()

    /** Every probed URL, in call order. */
    val probedUrls = mutableListOf<String>()

    var sentFile: RemoteFile? = null
    var stop: FakeDownloadStop? = null

    /** How the download of one URL stops, ahead of [stop], so one file of several can fail. */
    val stopsByUrl = mutableMapOf<String, FakeDownloadStop>()

    /** Whether a download held at [gateDownload]'s gate outlives its caller's cancel, as a blocking read does. */
    var gateOutlivesCancel: Boolean = false

    /** Every download, in call order. */
    val downloads = mutableListOf<RequestedFileDownload>()

    private var downloadGate: CompletableDeferred<Unit>? = null
    private var restartGate: CompletableDeferred<Unit>? = null

    fun gateDownload(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { downloadGate = it }

    /** Hold an accepted restart before the part is opened: a process that dies there leaves what `onRestart` left. */
    fun gateRestart(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { restartGate = it }

    override suspend fun probe(url: String): ProbeResult {
        probedUrls += url
        return probeAnswers[url] ?: ProbeResult.Unavailable
    }

    /** What [revalidate] answers by URL; `Unavailable` for any other. */
    val revalidateAnswers = mutableMapOf<String, RevalidationResult>()

    /** Every revalidated URL, in call order. */
    val revalidatedUrls = mutableListOf<String>()

    // The stores' own revalidation is tested against the real transport; this answers the tests above them.
    override suspend fun revalidate(
        url: String,
        kept: RemoteFile,
    ): RevalidationResult {
        revalidatedUrls += url
        revalidateGate?.await()
        return revalidateAnswers[url] ?: RevalidationResult.Unavailable
    }

    private var revalidateGate: CompletableDeferred<Unit>? = null

    /** Hold every [revalidate] until the gate opens, so a test can see what goes on while a check is out. */
    fun gateRevalidate(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { revalidateGate = it }

    override suspend fun download(
        url: String,
        part: File,
        expected: RemoteFile,
        onRestart: suspend (RemoteFile) -> Boolean,
        onProgress: (receivedBytes: Long, totalBytes: Long) -> Unit,
    ): TransferResult {
        downloads += RequestedFileDownload(url, part, expected, part.length())
        val file = sentFile ?: expected
        val restarts = file != expected
        if (restarts) {
            if (!onRestart(file)) return TransferResult.Refused(file).also { part.delete() }
            restartGate?.await()
        }
        val stop = stopsByUrl[url] ?: stop
        val end = stop?.partBytes ?: file.sizeBytes
        if (stop != null && stop.partBytes == null) return stop.result.also { part.delete() }
        // The first write opens the part: from its start after a restart, after its bytes on a resume.
        val halfway = ((if (restarts) 0L else part.length()) + end) / 2
        part.resizeSparse(halfway)
        onProgress(halfway, file.sizeBytes)
        if (gateOutlivesCancel) withContext(NonCancellable) { downloadGate?.await() } else downloadGate?.await()
        part.resizeSparse(end)
        onProgress(end, file.sizeBytes)
        return stop?.result ?: TransferResult.Complete(file)
    }
}

/** A body of ASCII digits: the pattern shifts against the copy chunk, so a dropped or reordered chunk changes it. */
internal fun digitBytes(length: Int): ByteArray = ByteArray(length) { ('0' + it % 10).code.toByte() }

/** Set the file's length without writing its bytes, so a test can hold a file of gigabytes. */
internal fun File.resizeSparse(bytes: Long) {
    parentFile?.mkdirs()
    RandomAccessFile(this, "rw").use { it.setLength(bytes) }
}
