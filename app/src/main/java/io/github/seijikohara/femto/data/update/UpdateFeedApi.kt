package io.github.seijikohara.femto.data.update

import android.util.Log
import io.github.seijikohara.femto.data.common.HTTP_TOO_MANY_REQUESTS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "UpdateFeedApi"

// The manifest's fixed asset name: every release of both channels publishes it
// beside its APK (.github/actions/update-manifest), so its URL is a permalink.
private const val MANIFEST_ASSET_NAME = "femto-car-launcher-update.json"

// Without a usable Retry-After (absent, or the HTTP-date form GitHub does not
// send), wait a minute: GitHub's rate-limit guidance asks for at least that.
private const val RATE_LIMIT_FALLBACK_MS = 60_000L

/** What one manifest lookup found; [UpdateRepository] turns it into an [UpdateState]. */
internal sealed interface FeedResult {
    /** The channel's latest release publishes a parseable manifest. */
    data class Found(
        val manifest: UpdateManifest,
    ) : FeedResult

    /**
     * No manifest to read: a 404 (a release that predates the manifest, or the
     * seconds in which the nightly job republishes its release) or a body that
     * is not a manifest. Nothing failed, so nothing is reported.
     */
    data object NoInformation : FeedResult

    /** The lookup could not be made; [reason] is [UpdateFailure.NETWORK] or [UpdateFailure.RATE_LIMITED]. */
    data class Unavailable(
        val reason: UpdateFailure,
    ) : FeedResult
}

/** The slice of [UpdateFeedApi] the repository consumes; a seam for JVM tests. */
internal interface UpdateFeed {
    suspend fun latest(channel: UpdateChannel): FeedResult

    suspend fun download(
        url: String,
        target: File,
        expectedSize: Long,
        onProgress: (Float) -> Unit,
    ): Boolean
}

/**
 * Client for the release feed: the manifest CI publishes beside every release
 * APK, read through GitHub's release download permalinks rather than the REST
 * API. The download URLs carry none of the REST API's 60-requests-per-hour
 * budget, which is shared with every unauthenticated client behind the same
 * public IP — a box tethered behind carrier NAT could find it spent by
 * strangers.
 *
 * [feedBase] is a releases page (`https://github.com/<owner>/<repo>/releases`),
 * so a fork or a local test server can stand in without a code change. Every
 * outcome is a value, never a throw (bar cancellation): the repository decides
 * what the user sees.
 */
internal class UpdateFeedApi(
    private val client: OkHttpClient,
    private val feedBase: String,
    private val userAgent: String,
    // Injectable clock for the rate-limit horizon (mirrors MetNorwayApi).
    private val nowMs: () -> Long = System::currentTimeMillis,
) : UpdateFeed {
    // Epoch-millis horizon announced by a 403 / 429; no lookup leaves before it.
    @Volatile
    private var retryAfterUntilMs: Long? = null

    override suspend fun latest(channel: UpdateChannel): FeedResult =
        withContext(Dispatchers.IO) {
            val suppressedUntil = retryAfterUntilMs
            if (suppressedUntil != null && nowMs() < suppressedUntil) {
                Log.w(TAG, "manifest lookup suppressed for another ${(suppressedUntil - nowMs()) / 1000}s")
                return@withContext FeedResult.Unavailable(UpdateFailure.RATE_LIMITED)
            }
            runCatching {
                val request = Request
                    .Builder()
                    .url(manifestUrl(channel))
                    .header("User-Agent", userAgent)
                    .build()
                client.newCall(request).execute().use { response ->
                    when {
                        response.code == HttpURLConnection.HTTP_NOT_FOUND -> {
                            Log.i(TAG, "no manifest published for ${channel.id}")
                            FeedResult.NoInformation
                        }

                        response.code == HttpURLConnection.HTTP_FORBIDDEN ||
                            response.code == HTTP_TOO_MANY_REQUESTS -> {
                            val waitMs =
                                response
                                    .header("Retry-After")
                                    ?.trim()
                                    ?.toLongOrNull()
                                    ?.let { it * 1000 } ?: RATE_LIMIT_FALLBACK_MS
                            retryAfterUntilMs = nowMs() + waitMs
                            Log.w(TAG, "manifest HTTP ${response.code}; lookups paused for ${waitMs / 1000}s")
                            FeedResult.Unavailable(UpdateFailure.RATE_LIMITED)
                        }

                        !response.isSuccessful -> {
                            Log.w(TAG, "manifest HTTP ${response.code}")
                            FeedResult.Unavailable(UpdateFailure.NETWORK)
                        }

                        else -> {
                            retryAfterUntilMs = null
                            // Read here, outside manifestOrNoInformation: a body cut
                            // short is an outage, not an unreadable manifest.
                            manifestOrNoInformation(response.body.string())
                        }
                    }
                }
            }.onFailure {
                // runCatching also traps cancellation; rethrow so a cancelled call
                // propagates instead of logging as a phantom outage.
                if (it is CancellationException) throw it
                Log.w(TAG, "manifest lookup failed", it)
            }.getOrElse { FeedResult.Unavailable(UpdateFailure.NETWORK) }
        }

    /**
     * Stream [url] into [target] through a `.part` file renamed into place, so
     * an interrupted transfer never leaves a truncated APK under the final name.
     * [onProgress] receives the fraction of [expectedSize] copied, at most once
     * per percent. Returns whether a file landed at [target]; the caller
     * verifies it.
     */
    override suspend fun download(
        url: String,
        target: File,
        expectedSize: Long,
        onProgress: (Float) -> Unit,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val part = File(target.parentFile, target.name + ".part")
            runCatching {
                val request = Request
                    .Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "download HTTP ${response.code}")
                        return@use false
                    }
                    target.parentFile?.mkdirs()
                    part.outputStream().use { output ->
                        copyCapped(response.body.byteStream(), output, expectedSize, onProgress)
                    }
                    part.renameTo(target).also { renamed ->
                        if (!renamed) Log.w(TAG, "rename to ${target.name} failed")
                    }
                }
            }
                // The part file never outlives the call: renamed into place on
                // success, discarded on a failure or a cancel, so the bytes of a
                // dead ~45 MB transfer never linger in the cache.
                .also { part.delete() }
                .onFailure {
                    if (it is CancellationException) throw it
                    Log.w(TAG, "download failed", it)
                }.getOrDefault(false)
        }

    private fun manifestUrl(channel: UpdateChannel): String =
        "${feedBase.trimEnd('/')}/${channel.releasePath()}/$MANIFEST_ASSET_NAME"

    // The feed answered: a body that is not a manifest is no information, not
    // an outage — asking again would read the same bytes.
    private fun manifestOrNoInformation(body: String): FeedResult =
        runCatching { parseUpdateManifest(body) }
            .onFailure { Log.w(TAG, "manifest unreadable", it) }
            .fold(onSuccess = { FeedResult.Found(it) }, onFailure = { FeedResult.NoInformation })
}

// GitHub's release permalinks: /latest/download/ follows the release GitHub
// marks latest — CI marks every stable release latest and keeps nightlies out
// of it — while the nightly job republishes under the fixed tag "nightly".
private fun UpdateChannel.releasePath(): String =
    when (this) {
        UpdateChannel.STABLE -> "latest/download"
        UpdateChannel.NIGHTLY -> "download/nightly"
    }

// Copies at most one byte past [expectedSize]: that byte already proves the
// body cannot match the manifest, and reading on would only fill storage (the
// caller's size check rejects the file).
private suspend fun copyCapped(
    input: InputStream,
    output: OutputStream,
    expectedSize: Long,
    onProgress: (Float) -> Unit,
) {
    val limit = expectedSize + 1
    val total = expectedSize.coerceAtLeast(1)
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var copied = 0L
    var reportedPercent = -1L
    while (copied < limit) {
        // A blocking read ignores cancellation; checking per chunk lets a
        // cancelled download stop instead of streaming on to the end.
        currentCoroutineContext().ensureActive()
        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), limit - copied).toInt())
        if (read == -1) break
        output.write(buffer, 0, read)
        copied += read
        val percent = (copied * 100 / total).coerceAtMost(100)
        if (percent != reportedPercent) {
            reportedPercent = percent
            onProgress(percent / 100f)
        }
    }
}
