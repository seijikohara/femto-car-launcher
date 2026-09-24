package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.update.FeedResult
import io.github.seijikohara.femto.data.update.UpdateChannel
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
 * [latestResult]; [download] writes [downloadBody] to the target (a null body
 * fails the transfer) after reporting half progress. [gateLatest] and
 * [gateDownload] hold the next call open, so a test can observe the in-flight
 * state and race a second caller against it.
 */
internal class FakeUpdateFeed(
    var latestResult: FeedResult = FeedResult.NoInformation,
    var downloadBody: ByteArray? = FakeApkBody,
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
    ): Boolean {
        downloads += RequestedDownload(url, target)
        onProgress(HALF_PROGRESS)
        downloadGate?.await()
        val body = downloadBody ?: return false
        target.parentFile?.mkdirs()
        target.writeBytes(body)
        onProgress(1f)
        return true
    }

    companion object {
        /** The fraction [download] reports before its gate. */
        const val HALF_PROGRESS = 0.5f
    }
}
