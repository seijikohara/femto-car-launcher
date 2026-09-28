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
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "UpdateFeedApi"

// The manifest's fixed asset name: every release of both channels publishes it
// beside its APK (.github/actions/update-manifest), so its URL is a permalink.
private const val MANIFEST_ASSET_NAME = "femto-car-launcher-update.json"

// Without a usable Retry-After (absent, negative, or the HTTP-date form GitHub
// does not send), wait a minute: GitHub's rate-limit guidance asks for at
// least that.
private const val RATE_LIMIT_FALLBACK_MS = 60_000L

// A longer Retry-After is taken as a day: far beyond any wait GitHub asks for,
// and it keeps the conversion to milliseconds from overflowing into a horizon
// in the past.
private const val MAX_RETRY_AFTER_SECONDS = 24L * 60 * 60

/**
 * The largest manifest body read. A manifest is a few hundred bytes; a body
 * past this is no manifest, and is never read whole.
 */
internal const val MAX_MANIFEST_BYTES = 64 * 1024

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

/** What one APK download did; the repository verifies a [Saved] file before offering it. */
internal sealed interface DownloadResult {
    /** The body landed at the target. */
    data object Saved : DownloadResult

    /** Nothing landed; [reason] is [UpdateFailure.NETWORK] or [UpdateFailure.STORAGE]. */
    data class Failed(
        val reason: UpdateFailure,
    ) : DownloadResult
}

/** The slice of [UpdateFeedApi] the repository consumes; a seam for JVM tests. */
internal interface UpdateFeed {
    suspend fun latest(channel: UpdateChannel): FeedResult

    suspend fun download(
        url: String,
        target: File,
        expectedSize: Long,
        onProgress: (Float) -> Unit,
    ): DownloadResult
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
                                    ?.takeIf { it >= 0 }
                                    ?.coerceAtMost(MAX_RETRY_AFTER_SECONDS)
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
                            response.body.source().let { body ->
                                if (body.request(MAX_MANIFEST_BYTES + 1L)) {
                                    Log.w(TAG, "manifest body over $MAX_MANIFEST_BYTES bytes")
                                    FeedResult.NoInformation
                                } else {
                                    manifestOrNoInformation(body.readUtf8())
                                }
                            }
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
     * per percent. A file that lands is not yet trusted: the caller verifies it.
     */
    override suspend fun download(
        url: String,
        target: File,
        expectedSize: Long,
        onProgress: (Float) -> Unit,
    ): DownloadResult =
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
                        return@use DownloadResult.Failed(UpdateFailure.NETWORK)
                    }
                    storing {
                        target.parentFile?.mkdirs()
                        part.outputStream()
                    }.use { output -> copyCapped(response.body.byteStream(), output, expectedSize, onProgress) }
                    if (part.renameTo(target)) {
                        DownloadResult.Saved
                    } else {
                        Log.w(TAG, "rename to ${target.name} failed")
                        DownloadResult.Failed(UpdateFailure.STORAGE)
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
                }.getOrElse { failure ->
                    DownloadResult.Failed(
                        if (failure is StorageException) UpdateFailure.STORAGE else UpdateFailure.NETWORK,
                    )
                }
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

// The fixed tag the nightly job republishes its release under.
private const val NIGHTLY_TAG = "nightly"

// GitHub's release permalinks: /latest/download/ follows the release GitHub
// marks latest — CI marks every stable release latest and keeps nightlies out
// of it — while the nightly job republishes under the fixed NIGHTLY_TAG.
private fun UpdateChannel.releasePath(): String =
    when (this) {
        UpdateChannel.STABLE -> "latest/download"
        UpdateChannel.NIGHTLY -> "download/$NIGHTLY_TAG"
    }

/**
 * The web page of [channel]'s latest release under [feedBase]: where a person
 * downloads the same APK by hand when the in-app path cannot finish. A build
 * without a channel of its own (null) gets the list of every release.
 */
internal fun releasePageUrl(
    feedBase: String,
    channel: UpdateChannel?,
): String =
    feedBase.trimEnd('/').let { base ->
        when (channel) {
            UpdateChannel.STABLE -> "$base/latest"
            UpdateChannel.NIGHTLY -> "$base/tag/$NIGHTLY_TAG"
            null -> base
        }
    }

// Marks an IOException from the local file side: a full disk or an unwritable
// directory is this device's problem, not an outage, and is reported as such.
// Internal, like copyCapped, so the read/write split is JVM-unit-testable: a
// full disk cannot be staged in a unit test.
internal class StorageException(
    cause: IOException,
) : IOException(cause)

private inline fun <T> storing(block: () -> T): T =
    try {
        block()
    } catch (e: IOException) {
        throw StorageException(e)
    }

// Copies at most one byte past [expectedSize]: that byte already proves the
// body cannot match the manifest, and reading on would only fill storage (the
// caller's size check rejects the file). Reads fail as the network, writes as
// storage.
internal suspend fun copyCapped(
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
        storing { output.write(buffer, 0, read) }
        copied += read
        val percent = (copied * 100 / total).coerceAtMost(100)
        if (percent != reportedPercent) {
            reportedPercent = percent
            onProgress(percent / 100f)
        }
    }
}
