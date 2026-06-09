package io.github.seijikohara.femto.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

private const val TAG = "GoogleFontsApi"

// Google's metadata endpoints guard JSON responses with an XSSI prefix
// (`)]}'`) on some routes and omit it on others. Strip it defensively so both
// `/metadata/fonts` (no prefix) and `/download/list` (prefixed) decode.
private fun stripXssiPrefix(body: String): String = body.removePrefix(")]}'").trimStart()

/**
 * Read-only client for the public Google Fonts catalog and font files. Needs no
 * API key and no Google Play Services: the catalog comes from the public
 * metadata route and the typeface files download straight from `fonts.gstatic`,
 * so the launcher works on head units that lack Play Services.
 */
internal class GoogleFontsApi(
    private val client: OkHttpClient,
    // `fonts.google.com` serves the catalog + per-family file manifest; the TTF
    // bytes live on `fonts.gstatic.com` (URLs come from the manifest itself).
    private val metadataBaseUrl: String = "https://fonts.google.com/",
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Fetch the full catalog (~1.9k families), popularity-sorted. */
    suspend fun catalog(): List<GoogleFontFamily>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(metadataBaseUrl.trimEnd('/') + "/metadata/fonts").build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "catalog HTTP ${response.code}")
                        return@use null
                    }
                    response.body.string().let { body ->
                        json
                            .decodeFromString<CatalogResponse>(stripXssiPrefix(body))
                            .familyMetadataList
                            .sortedBy { it.popularity }
                    }
                }
            }.onFailure { Log.w(TAG, "catalog failed", it) }
                .getOrNull()
        }

    /**
     * Resolve the best single download for [family]: the upright variable font
     * when one exists (one file spanning every weight, full CJK coverage), else
     * the static weights. Returns null when the manifest cannot be read.
     */
    suspend fun plan(family: String): FontDownloadPlan? =
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = family.replace(" ", "%20")
                val request =
                    Request.Builder().url(metadataBaseUrl.trimEnd('/') + "/download/list?family=$encoded").build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "manifest HTTP ${response.code} for $family")
                        return@use null
                    }
                    response.body.string().let { body ->
                        json
                            .decodeFromString<DownloadListResponse>(stripXssiPrefix(body))
                            .manifest
                            .fileRefs
                            .let(::planFrom)
                    }
                }
            }.onFailure { Log.w(TAG, "manifest failed for $family", it) }
                .getOrNull()
        }

    /** Download [url] into [target], replacing any partial file. Returns success. */
    suspend fun downloadTo(
        url: String,
        target: File,
    ): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "download HTTP ${response.code} for $url")
                        return@use false
                    }
                    val body = response.body
                    target.parentFile?.mkdirs()
                    // Stream to a temp file then atomically rename so a crash mid-download
                    // never leaves a truncated TTF that Typeface loading would reject.
                    val tmp = File(target.parentFile, target.name + ".part")
                    tmp.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
                    tmp.renameTo(target)
                }
            }.onFailure { Log.w(TAG, "download failed for $url", it) }
                .getOrDefault(false)
        }

    private fun planFrom(refs: List<FileRef>): FontDownloadPlan? {
        val ttf = refs.filter { it.filename.endsWith(".ttf", ignoreCase = true) }
        // Prefer the upright variable font: a single file carries every weight and
        // the complete glyph set, which is essential for CJK faces the CSS API
        // would otherwise split into hundreds of unicode-range chunks.
        ttf
            .firstOrNull { it.filename.contains("VariableFont") && !it.filename.contains("Italic", ignoreCase = true) }
            ?.let { return FontDownloadPlan.Variable(it.url) }
        // No variable font: collect the upright static weights we render at.
        val statics =
            StaticWeightSuffix.entries
                .mapNotNull { suffix ->
                    ttf
                        .filter { !it.filename.contains("Italic", ignoreCase = true) }
                        .firstOrNull { it.filename.endsWith("-${suffix.token}.ttf") }
                        ?.let { suffix.weight to it.url }
                }.toMap()
        return statics.takeIf { it.isNotEmpty() }?.let(FontDownloadPlan::Static)
    }

    @Serializable
    private data class CatalogResponse(
        val familyMetadataList: List<GoogleFontFamily> = emptyList(),
    )

    @Serializable
    private data class DownloadListResponse(
        val manifest: Manifest = Manifest(),
    )

    @Serializable
    private data class Manifest(
        val fileRefs: List<FileRef> = emptyList(),
    )

    @Serializable
    private data class FileRef(
        val filename: String = "",
        val url: String = "",
    )
}

/**
 * What to fetch for a family. [Variable] is one URL covering every weight via
 * the `wght` axis; [Static] is a weight-keyed set of single-weight files.
 */
internal sealed interface FontDownloadPlan {
    data class Variable(
        val url: String,
    ) : FontDownloadPlan

    data class Static(
        val urlByWeight: Map<Int, String>,
    ) : FontDownloadPlan
}

// Upright static-weight filename suffixes Google uses, mapped to CSS weights.
private enum class StaticWeightSuffix(
    val token: String,
    val weight: Int,
) {
    THIN("Thin", 100),
    EXTRA_LIGHT("ExtraLight", 200),
    LIGHT("Light", 300),
    REGULAR("Regular", 400),
    MEDIUM("Medium", 500),
    SEMI_BOLD("SemiBold", 600),
    BOLD("Bold", 700),
    EXTRA_BOLD("ExtraBold", 800),
    BLACK("Black", 900),
}
