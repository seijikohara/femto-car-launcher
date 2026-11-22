package io.github.seijikohara.femto.data.common.download

import kotlinx.serialization.Serializable
import java.io.File

/** What the server says about one file. Serializable for the routing data, which keeps it beside each tile. */
@Serializable
internal data class RemoteFile(
    val sizeBytes: Long,
    /** As the server sent it, quotes and weak prefix included, so `If-Range` can carry it back unchanged. */
    val etag: String?,
    val lastModifiedEpochMs: Long?,
)

/** What a look at a file found. Its size and ETag are a preview: the download's own answer is the authority. */
internal sealed interface ProbeResult {
    data class Found(
        val file: RemoteFile,
    ) : ProbeResult

    /** 404 or 410. */
    data object Gone : ProbeResult

    /** 200 without a usable Content-Length. */
    data object SizeUnknown : ProbeResult

    /** No answer, a redirect, or any other status. */
    data object Unavailable : ProbeResult
}

/** What a check of a kept file against the server found. */
internal sealed interface RevalidationResult {
    /** The server still has the file that was kept. */
    data object NotModified : RevalidationResult

    /** The server has another file now. */
    data class Changed(
        val file: RemoteFile,
    ) : RevalidationResult

    /** 404 or 410. */
    data object Gone : RevalidationResult

    /** No answer, a redirect, a changed file without a usable Content-Length, or any other status. */
    data object Unavailable : RevalidationResult
}

/** How one download ended, and what it left in the part file. */
internal sealed interface TransferResult {
    /** The part file holds all of [file]. */
    data class Complete(
        val file: RemoteFile,
    ) : TransferResult

    /** The connection failed; the part is kept. */
    data object Interrupted : TransferResult

    /** The part could not be written; what was written is kept. */
    data object StorageFailed : TransferResult

    /** The server sent another file and the caller declined it; the part is gone. */
    data class Refused(
        val file: RemoteFile,
    ) : TransferResult

    /** 404 or 410; the part is gone. */
    data object Gone : TransferResult

    /** A redirect or an unexpected status: the part is kept. A range that no longer matches: the part is gone. */
    data object Unavailable : TransferResult
}

/**
 * The transport for large files on one host: a look at a file, a check of a kept one, and a resumable download.
 * Shared by the saved map regions and the routing data; a seam for JVM tests. Every outcome is a value: nothing
 * throws except cancellation.
 */
internal interface RemoteFileFeed {
    suspend fun probe(url: String): ProbeResult

    /**
     * Ask whether the server still has [kept], with a conditional request on its ETag. A kept file without an ETag
     * that can be sent back is compared with what a plain look at the file finds.
     */
    suspend fun revalidate(
        url: String,
        kept: RemoteFile,
    ): RevalidationResult

    /**
     * Download [url] into [part], resuming from the bytes it already holds when
     * [expected] carries an ETag. When the server sends a file other than
     * [expected], [onRestart] decides whether it replaces the part. A cancel
     * leaves the part in place; the caller decides whether to delete it.
     */
    suspend fun download(
        url: String,
        part: File,
        expected: RemoteFile,
        onRestart: suspend (RemoteFile) -> Boolean,
        onProgress: (receivedBytes: Long, totalBytes: Long) -> Unit,
    ): TransferResult
}
