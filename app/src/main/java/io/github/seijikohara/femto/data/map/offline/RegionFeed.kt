package io.github.seijikohara.femto.data.map.offline

import java.io.File

/** What the server says about one region file. */
internal data class RemoteFile(
    val sizeBytes: Long,
    /** As the server sent it, quotes and weak prefix included, so `If-Range` can carry it back unchanged. */
    val etag: String?,
    val lastModifiedEpochMs: Long?,
)

/** What a look at a region file found. Its size and ETag are a preview: the download's own answer is the authority. */
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

internal sealed interface IndexResult {
    /** The body is now in the target file. */
    data class Fresh(
        val etag: String?,
    ) : IndexResult

    data object NotModified : IndexResult

    data object Unavailable : IndexResult
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
 * The transport the region repository consumes; a seam for JVM tests. Every
 * outcome is a value: nothing throws except cancellation.
 */
internal interface RegionFeed {
    /** Fetch the region index into [target], revalidated against [etag] when one is given. */
    suspend fun index(
        target: File,
        etag: String?,
    ): IndexResult

    suspend fun probe(url: String): ProbeResult

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
