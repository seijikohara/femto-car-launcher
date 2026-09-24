package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.update.DownloadResult
import io.github.seijikohara.femto.data.update.FeedResult
import io.github.seijikohara.femto.data.update.UpdateChannel
import io.github.seijikohara.femto.data.update.UpdateFailure
import io.github.seijikohara.femto.data.update.UpdateFeed
import kotlinx.coroutines.CompletableDeferred
import java.io.File

/** One [FakeUpdateFeed.download] call: what was fetched, and where it was written. */
internal data class RequestedDownload(
    val url: String,
    val target: File,
)

/**
 * In-memory [UpdateFeed] for repository tests. [latest] answers
 * [latestResult]; [download] reports half progress, then writes
 * [downloadBody] to the target — or, with [downloadFailure] set, fails with
 * that reason and writes nothing. [gateLatest] and [gateDownload] hold the next
 * call open, so a test can observe the in-flight state and race a second
 * caller against it.
 */
internal class FakeUpdateFeed(
    var latestResult: FeedResult = FeedResult.NoInformation,
    var downloadBody: ByteArray = FakeApkBody,
    var downloadFailure: UpdateFailure? = null,
) : UpdateFeed {
    /** The channel of every lookup, in call order. */
    val latestCalls = mutableListOf<UpdateChannel>()

    /** Every download, in call order. */
    val downloads = mutableListOf<RequestedDownload>()

    private var latestGate: CompletableDeferred<Unit>? = null
    private var downloadGate: CompletableDeferred<Unit>? = null

    fun gateLatest(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { latestGate = it }

    fun gateDownload(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { downloadGate = it }

    override suspend fun latest(channel: UpdateChannel): FeedResult {
        latestCalls += channel
        latestGate?.await()
        return latestResult
    }

    override suspend fun download(
        url: String,
        target: File,
        expectedSize: Long,
        onProgress: (Float) -> Unit,
    ): DownloadResult {
        downloads += RequestedDownload(url, target)
        onProgress(HALF_PROGRESS)
        downloadGate?.await()
        downloadFailure?.let { return DownloadResult.Failed(it) }
        target.parentFile?.mkdirs()
        target.writeBytes(downloadBody)
        onProgress(1f)
        return DownloadResult.Saved
    }

    companion object {
        /** The fraction [download] reports before its gate. */
        const val HALF_PROGRESS = 0.5f
    }
}
