package io.github.seijikohara.femto.data.map.offline

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection.HTTP_GONE
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_NOT_MODIFIED
import java.net.HttpURLConnection.HTTP_OK
import java.net.HttpURLConnection.HTTP_PARTIAL

private const val TAG = "RegionFeedApi"

internal const val REGION_INDEX_URL = "https://download.geofabrik.de/index-v1.json"

/**
 * The largest index body kept. The real index is a few megabytes; a body past
 * this is no index, and it never replaces the cached one.
 */
internal const val MAX_REGION_INDEX_BYTES = 32L * 1024 * 1024

/** The bytes between two progress reports: a 58 GB file reports some 58,000 times, not once per chunk. */
internal const val DOWNLOAD_PROGRESS_STEP_BYTES = 1024L * 1024

private const val COPY_CHUNK_BYTES = 64 * 1024

// Not among HttpURLConnection's constants.
private const val HTTP_RANGE_NOT_SATISFIABLE = 416

// Range, Content-Length and the bytes on disk agree only for the bytes as
// stored; without it OkHttp asks for gzip and inflates the body out of sight.
private const val IDENTITY_ENCODING = "identity"

private val ContentRangePattern = Regex("bytes (\\d+)-(\\d+)/(\\d+)")

/**
 * Client for the region host: the index, a look at one file, and a resumable
 * download of a file of up to tens of gigabytes. Every request is pinned to
 * [hostPrefix]; a URL elsewhere is answered without a request.
 */
internal class RegionFeedApi(
    private val client: OkHttpClient,
    private val userAgent: String,
    private val hostPrefix: String = REGION_HOST_PREFIX,
    private val indexUrl: String = REGION_INDEX_URL,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RegionFeed {
    override suspend fun index(
        target: File,
        etag: String?,
    ): IndexResult =
        withContext(ioDispatcher) {
            if (indexUrl.startsWith(hostPrefix)) fetchIndex(target, etag) else IndexResult.Unavailable
        }

    override suspend fun probe(url: String): ProbeResult =
        withContext(ioDispatcher) {
            if (url.startsWith(hostPrefix)) fetchProbe(url) else ProbeResult.Unavailable
        }

    override suspend fun download(
        url: String,
        part: File,
        expected: RemoteFile,
        onRestart: suspend (RemoteFile) -> Boolean,
        onProgress: (receivedBytes: Long, totalBytes: Long) -> Unit,
    ): TransferResult =
        withContext(ioDispatcher) {
            val heldBytes = part.length()
            // A part already whole means the process died between the last byte and the rename.
            when {
                !url.startsWith(hostPrefix) -> TransferResult.Unavailable
                heldBytes > 0 && heldBytes == expected.sizeBytes -> TransferResult.Complete(expected)
                else -> transfer(url, part, expected, heldBytes, onRestart, onProgress)
            }
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

    private fun fetchProbe(url: String): ProbeResult =
        try {
            client.newCall(fileRequest(url).head().build()).execute().use { response ->
                when (val code = response.code) {
                    HTTP_OK -> response.remoteFileOrNull()?.let { ProbeResult.Found(it) } ?: ProbeResult.SizeUnknown
                    HTTP_NOT_FOUND, HTTP_GONE -> ProbeResult.Gone
                    else -> ProbeResult.Unavailable.also { Log.w(TAG, "probe HTTP $code") }
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "probe failed", e)
            ProbeResult.Unavailable
        }

    private suspend fun transfer(
        url: String,
        part: File,
        expected: RemoteFile,
        heldBytes: Long,
        onRestart: suspend (RemoteFile) -> Boolean,
        onProgress: (receivedBytes: Long, totalBytes: Long) -> Unit,
    ): TransferResult {
        // Without an ETag, If-Range has nothing to guard a resume with: bytes of
        // a newer file would be appended to an older one unnoticed. One that
        // cannot be sent counts as none.
        val resumeEtag = expected.etag?.sendableEtagOrNull()
        val resumeFrom = heldBytes.takeIf { it > 0 && resumeEtag != null }
        if (resumeFrom == null) part.delete()
        return try {
            client.newCall(downloadRequest(url, resumeFrom, resumeEtag)).execute().use { response ->
                // Only an answer that proves the part unusable deletes it. A redirect, a
                // busy or refusing server says nothing about the part, and one such answer
                // must not throw away gigabytes that a later resume can still use.
                when (val code = response.code) {
                    HTTP_PARTIAL -> response.appendRange(part, expected, resumeFrom, resumeEtag, onProgress)
                    HTTP_OK -> response.replaceWhole(part, expected, heldBytes, onRestart, onProgress)
                    HTTP_NOT_FOUND, HTTP_GONE -> TransferResult.Gone.discarding(part)
                    HTTP_RANGE_NOT_SATISFIABLE -> TransferResult.Unavailable.discarding(part)
                    else -> TransferResult.Unavailable.also { Log.w(TAG, "download HTTP $code; part kept") }
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "download request failed", e)
            TransferResult.Interrupted
        }
    }

    // A 206 continues the part only when it starts at the part's end and
    // belongs to the expected file; any other range would splice two files.
    // Caching proxies can hold two versions of the file at once and answer
    // If-Range with a range of the other one, so an ETag other than the one
    // sent marks another file even when the sizes match. A 206 without a
    // sendable ETag is judged by its range alone.
    private suspend fun Response.appendRange(
        part: File,
        expected: RemoteFile,
        resumeFrom: Long?,
        sentEtag: String?,
        onProgress: (receivedBytes: Long, totalBytes: Long) -> Unit,
    ): TransferResult =
        header("Content-Range")
            ?.let(::byteRangeOrNull)
            ?.takeIf { it.first == resumeFrom && it.total == expected.sizeBytes }
            ?.takeIf { etagOrNull().let { etag -> etag == null || etag == sentEtag } }
            ?.let { copyInto(part, from = it.first, byteCount = it.length, file = expected, onProgress) }
            ?: run {
                Log.w(TAG, "range ${header("Content-Range")} of ${header("ETag")} refused")
                TransferResult.Unavailable.discarding(part)
            }

    // A 200 is a whole file, perhaps not the expected one: the caller weighs a
    // different file, or any restart of a part that held bytes, against its
    // limits before a byte of it is kept.
    private suspend fun Response.replaceWhole(
        part: File,
        expected: RemoteFile,
        heldBytes: Long,
        onRestart: suspend (RemoteFile) -> Boolean,
        onProgress: (receivedBytes: Long, totalBytes: Long) -> Unit,
    ): TransferResult =
        remoteFileOrNull()
            ?.let { file ->
                val restarts = heldBytes > 0 || file.sizeBytes != expected.sizeBytes || file.etag != expected.etag
                if (restarts && !onRestart(file)) {
                    TransferResult.Refused(file).discarding(part)
                } else {
                    copyInto(part, from = 0, byteCount = file.sizeBytes, file = file, onProgress)
                }
            }
            ?: TransferResult.Unavailable.discarding(part).also { Log.w(TAG, "whole file without a length") }

    // Writes the body into [part] from byte [from] (0 truncates it), at most
    // [byteCount] bytes, so a body longer than announced never grows the part
    // past what the caller's storage checks allowed.
    private suspend fun Response.copyInto(
        part: File,
        from: Long,
        byteCount: Long,
        file: RemoteFile,
        onProgress: (receivedBytes: Long, totalBytes: Long) -> Unit,
    ): TransferResult {
        // Mutable because the chunk callback advances it: each report waits for a
        // full step past the previous one, counted from the part's starting length.
        var reportedBytes = from

        fun report(receivedBytes: Long) {
            reportedBytes = receivedBytes
            onProgress(receivedBytes, file.sizeBytes)
        }

        return try {
            val receivedBytes =
                from +
                    openForWriting(part, append = from > 0).use { output ->
                        body.byteStream().copyChunks(output, limit = byteCount) { copied ->
                            if (from + copied - reportedBytes >= DOWNLOAD_PROGRESS_STEP_BYTES) report(from + copied)
                        }
                    }
            if (receivedBytes != reportedBytes) report(receivedBytes)
            // A body that ends early leaves the part short of the file, whatever
            // its own length said; a resume picks up from there.
            if (receivedBytes == file.sizeBytes) TransferResult.Complete(file) else TransferResult.Interrupted
        } catch (e: FileWriteException) {
            Log.w(TAG, "part write failed", e)
            TransferResult.StorageFailed
        } catch (e: IOException) {
            Log.w(TAG, "download interrupted", e)
            TransferResult.Interrupted
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

    private fun requestTo(url: String): Request.Builder =
        Request
            .Builder()
            .url(url)
            .header("User-Agent", userAgent)

    // Compression is left to OkHttp here: nothing compares the index's size.
    private fun indexRequest(etag: String?): Request =
        requestTo(indexUrl)
            .apply { etag?.sendableEtagOrNull()?.let { header("If-None-Match", it) } }
            .build()

    private fun fileRequest(url: String): Request.Builder = requestTo(url).header("Accept-Encoding", IDENTITY_ENCODING)

    private fun downloadRequest(
        url: String,
        resumeFrom: Long?,
        etag: String?,
    ): Request =
        fileRequest(url)
            .apply {
                if (resumeFrom != null && etag != null) {
                    header("Range", "bytes=$resumeFrom-")
                    header("If-Range", etag)
                }
            }.build()
}

/** The client the app uses: no cache, no redirects. */
internal fun regionHttpClient(): OkHttpClient =
    OkHttpClient
        .Builder()
        .cache(null)
        // A redirect could lead off the one host every request is pinned to; it is a failure instead.
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

private data class ByteRange(
    val first: Long,
    val last: Long,
    val total: Long,
) {
    val length: Long get() = last - first + 1
}

// Digits past Long are no range at all, so each is read with toLongOrNull.
private fun byteRangeOrNull(contentRange: String): ByteRange? =
    ContentRangePattern
        .matchEntire(contentRange.trim())
        ?.groupValues
        ?.drop(1)
        ?.mapNotNull { it.toLongOrNull() }
        ?.takeIf { it.size == 3 }
        ?.let { (first, last, total) -> ByteRange(first, last, total) }
        ?.takeIf { it.first <= it.last && it.last < it.total }

// Read from the headers, not body.contentLength(): a HEAD has no body to measure.
private fun Response.remoteFileOrNull(): RemoteFile? =
    header("Content-Length")
        ?.toLongOrNull()
        ?.takeIf { it > 0 }
        ?.let { size ->
            RemoteFile(
                sizeBytes = size,
                etag = etagOrNull(),
                lastModifiedEpochMs = headers.getInstant("Last-Modified")?.toEpochMilli(),
            )
        }

// OkHttp throws on a request header value with a character outside tab and
// 0x20-0x7e, so an ETag that could not be sent back counts as absent: the
// whole file is asked for, and the index is fetched without revalidation.
private fun String.sendableEtagOrNull(): String? = takeIf { etag -> etag.all { it == '\t' || it in ' '..'~' } }

private fun Response.etagOrNull(): String? = header("ETag")?.sendableEtagOrNull()

// The part goes with this outcome: what it held cannot be resumed against the answer.
private fun <T : TransferResult> T.discarding(part: File): T = also { part.delete() }

// Marks an IOException from the file side: a full disk or an unwritable
// directory is this device's problem, not a dropped connection, and the
// download reports it apart.
private class FileWriteException(
    cause: IOException,
) : IOException(cause)

private inline fun <T> writing(block: () -> T): T =
    try {
        block()
    } catch (e: IOException) {
        throw FileWriteException(e)
    }

private fun openForWriting(
    file: File,
    append: Boolean,
): OutputStream =
    writing {
        file.parentFile?.mkdirs()
        FileOutputStream(file, append)
    }

// Copies at most [limit] bytes and returns how many. A read failure surfaces
// as the IOException it is, a write failure as FileWriteException.
private suspend fun InputStream.copyChunks(
    output: OutputStream,
    limit: Long,
    onChunk: (copiedBytes: Long) -> Unit = {},
): Long {
    val buffer = ByteArray(COPY_CHUNK_BYTES)
    // An index loop: the count advances per chunk, and the loop ends on the
    // limit or at the end of the body, whichever comes first.
    var copied = 0L
    while (copied < limit) {
        // A blocking read ignores cancellation; checking per chunk lets a cancel
        // stop between chunks instead of streaming on to the end.
        currentCoroutineContext().ensureActive()
        // At most one chunk, so the Int holds whatever the limit is.
        val read = read(buffer, 0, minOf(COPY_CHUNK_BYTES.toLong(), limit - copied).toInt())
        if (read == -1) break
        writing { output.write(buffer, 0, read) }
        copied += read
        onChunk(copied)
    }
    return copied
}
