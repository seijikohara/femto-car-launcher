package io.github.seijikohara.femto.data.map.offline

import io.github.seijikohara.femto.data.common.download.RemoteFileFeed
import java.io.File

internal sealed interface IndexResult {
    /** The body is now in the target file. */
    data class Fresh(
        val etag: String?,
    ) : IndexResult

    data object NotModified : IndexResult

    data object Unavailable : IndexResult
}

/** The transport the region repository consumes: the file transport plus the region index. */
internal interface RegionFeed : RemoteFileFeed {
    /** Fetch the region index into [target], revalidated against [etag] when one is given. */
    suspend fun index(
        target: File,
        etag: String?,
    ): IndexResult
}
