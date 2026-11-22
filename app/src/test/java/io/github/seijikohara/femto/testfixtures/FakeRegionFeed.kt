package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.map.offline.IndexResult
import io.github.seijikohara.femto.data.map.offline.RegionFeed
import java.io.File

/**
 * In-memory [RegionFeed] for repository tests: the file half of [FakeRemoteFileFeed] plus the region index.
 *
 * [index] writes [indexBody] to the target and answers [indexEtag]; a request that revalidates with that ETag gets
 * `NotModified`, and with [indexAvailable] off every request fails.
 */
internal class FakeRegionFeed(
    var indexBody: ByteArray = sampleRegionIndex(),
) : FakeRemoteFileFeed(),
    RegionFeed {
    var indexEtag: String? = INDEX_ETAG
    var indexAvailable = true

    /** The ETag of every index request, in call order. */
    val indexEtags = mutableListOf<String?>()

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

    companion object {
        /** The ETag every fresh index carries unless a test changes [indexEtag]. */
        const val INDEX_ETAG = "\"index-1\""
    }
}
