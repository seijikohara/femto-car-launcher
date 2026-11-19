package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.map.offline.IndexResult
import io.github.seijikohara.femto.data.map.offline.ProbeResult
import io.github.seijikohara.femto.data.map.offline.RegionFeed
import io.github.seijikohara.femto.data.map.offline.RemoteFile
import io.github.seijikohara.femto.data.map.offline.TransferResult
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import java.io.RandomAccessFile

/** One [FakeRegionFeed.download] call: what was asked for, and what the part held when it was. */
internal data class RequestedRegionDownload(
    val url: String,
    val part: File,
    val expected: RemoteFile,
    val heldBytes: Long,
)

/**
 * How a [FakeRegionFeed] download stops before the end: the part's length then, and the result. A null length
 * deletes the part, as the transport does for a missing file or a range that no longer matches.
 */
internal data class FakeDownloadStop(
    val partBytes: Long?,
    val result: TransferResult,
)

/**
 * In-memory [RegionFeed] for repository tests that keeps the transport's contract on the part file.
 *
 * [index] writes [indexBody] to the target and answers [indexEtag]; a request that revalidates with that ETag gets
 * `NotModified`, and with [indexAvailable] off every request fails. [probe] answers from [probeAnswers] by URL, and
 * `Unavailable` for any other. [download] stands for a server that sends [sentFile], or the expected file when that
 * is null: another file goes through `onRestart` first with the old part still on disk, as the transport calls it.
 * A refusal deletes the part; an acceptance waits at the gate of [gateRestart], if one is set, and only then opens
 * the part from its start, which truncates it, as the transport does when it opens its output stream. The part then
 * grows halfway to its end, which is reported; the download waits at the gate of [gateDownload], if one is set; and
 * the part grows to its end — the whole file, or the length of [stop] — which is reported again. Lengths are set
 * without writing bytes, so a part can stand for gigabytes.
 */
internal class FakeRegionFeed(
    var indexBody: ByteArray = sampleRegionIndex(),
) : RegionFeed {
    var indexEtag: String? = INDEX_ETAG
    var indexAvailable = true

    /** The ETag of every index request, in call order. */
    val indexEtags = mutableListOf<String?>()

    val probeAnswers = mutableMapOf<String, ProbeResult>()

    /** Every probed URL, in call order. */
    val probedUrls = mutableListOf<String>()

    var sentFile: RemoteFile? = null
    var stop: FakeDownloadStop? = null

    /** Every download, in call order. */
    val downloads = mutableListOf<RequestedRegionDownload>()

    private var downloadGate: CompletableDeferred<Unit>? = null
    private var restartGate: CompletableDeferred<Unit>? = null

    fun gateDownload(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { downloadGate = it }

    /** Hold an accepted restart before the part is opened: a process that dies there leaves what `onRestart` left. */
    fun gateRestart(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { restartGate = it }

    override suspend fun index(
        target: File,
        etag: String?,
    ): IndexResult {
        indexEtags += etag
        return when {
            !indexAvailable -> {
                IndexResult.Unavailable
            }

            etag != null && etag == indexEtag -> {
                IndexResult.NotModified
            }

            else -> {
                target.parentFile?.mkdirs()
                target.writeBytes(indexBody)
                IndexResult.Fresh(indexEtag)
            }
        }
    }

    override suspend fun probe(url: String): ProbeResult {
        probedUrls += url
        return probeAnswers[url] ?: ProbeResult.Unavailable
    }

    override suspend fun download(
        url: String,
        part: File,
        expected: RemoteFile,
        onRestart: suspend (RemoteFile) -> Boolean,
        onProgress: (receivedBytes: Long, totalBytes: Long) -> Unit,
    ): TransferResult {
        downloads += RequestedRegionDownload(url, part, expected, part.length())
        val file = sentFile ?: expected
        val restarts = file != expected
        if (restarts) {
            if (!onRestart(file)) return TransferResult.Refused(file).also { part.delete() }
            restartGate?.await()
        }
        val stop = stop
        val end = stop?.partBytes ?: file.sizeBytes
        if (stop != null && stop.partBytes == null) return stop.result.also { part.delete() }
        // The first write opens the part: from its start after a restart, after its bytes on a resume.
        val halfway = ((if (restarts) 0L else part.length()) + end) / 2
        part.resizeSparse(halfway)
        onProgress(halfway, file.sizeBytes)
        downloadGate?.await()
        part.resizeSparse(end)
        onProgress(end, file.sizeBytes)
        return stop?.result ?: TransferResult.Complete(file)
    }

    companion object {
        /** The ETag every fresh index carries unless a test changes [indexEtag]. */
        const val INDEX_ETAG = "\"index-1\""
    }
}

/** Set the file's length without writing its bytes, so a test can hold a file of gigabytes. */
internal fun File.resizeSparse(bytes: Long) {
    parentFile?.mkdirs()
    RandomAccessFile(this, "rw").use { it.setLength(bytes) }
}
