package io.github.seijikohara.femto.data.map.offline

import android.util.Log
import io.github.seijikohara.femto.data.common.download.RemoteFileApi
import io.github.seijikohara.femto.data.common.download.RemoteFileFeed
import io.github.seijikohara.femto.data.common.download.copyChunks
import io.github.seijikohara.femto.data.common.download.etagOrNull
import io.github.seijikohara.femto.data.common.download.openForWriting
import io.github.seijikohara.femto.data.common.download.requestTo
import io.github.seijikohara.femto.data.common.download.sendableEtagOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection.HTTP_NOT_MODIFIED
import java.net.HttpURLConnection.HTTP_OK

private const val TAG = "RegionFeedApi"

internal const val REGION_INDEX_URL = "https://download.geofabrik.de/index-v1.json"

/**
 * The largest index body kept. The real index is a few megabytes; a body past
 * this is no index, and it never replaces the cached one.
 */
internal const val MAX_REGION_INDEX_BYTES = 32L * 1024 * 1024

/**
 * Client for the region host: the index, plus the file transport ([RemoteFileApi])
 * for the region files. Every request is pinned to [hostPrefix]; a URL elsewhere
 * is answered without a request.
 */
internal class RegionFeedApi(
    private val client: OkHttpClient,
    private val userAgent: String,
    private val hostPrefix: String = REGION_HOST_PREFIX,
    private val indexUrl: String = REGION_INDEX_URL,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RegionFeed,
    RemoteFileFeed by RemoteFileApi(client, userAgent, hostPrefix, ioDispatcher) {
    override suspend fun index(
        target: File,
        etag: String?,
    ): IndexResult =
        withContext(ioDispatcher) {
            if (indexUrl.startsWith(hostPrefix)) fetchIndex(target, etag) else IndexResult.Unavailable
        }

    private suspend fun fetchIndex(
        target: File,
        etag: String?,
    ): IndexResult {
        val staging = File(target.parentFile, target.name + ".tmp")
        return try {
            client.newCall(indexRequest(etag)).execute().use { response ->
                when (val code = response.code) {
                    HTTP_NOT_MODIFIED -> IndexResult.NotModified
                    HTTP_OK if response.replacesIndex(staging, target) -> IndexResult.Fresh(response.etagOrNull())
                    else -> IndexResult.Unavailable.also { Log.w(TAG, "index HTTP $code not kept") }
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "index fetch failed", e)
            IndexResult.Unavailable
        } finally {
            // Renamed into place when it was whole; on any other end, a cancel included, it must not linger.
            staging.delete()
        }
    }

    // True when the whole body fit under MAX_REGION_INDEX_BYTES and replaced
    // [target]. The count is taken as copied, after OkHttp's inflation: a
    // compressed body announces no length to trust.
    private suspend fun Response.replacesIndex(
        staging: File,
        target: File,
    ): Boolean =
        openForWriting(staging, append = false)
            .use { output -> body.byteStream().copyChunks(output, limit = MAX_REGION_INDEX_BYTES + 1) }
            .let { copiedBytes -> copiedBytes <= MAX_REGION_INDEX_BYTES && staging.renameTo(target) }

    // Compression is left to OkHttp here: nothing compares the index's size.
    private fun indexRequest(etag: String?): Request =
        requestTo(indexUrl, userAgent)
            .apply { etag?.sendableEtagOrNull()?.let { header("If-None-Match", it) } }
            .build()
}
