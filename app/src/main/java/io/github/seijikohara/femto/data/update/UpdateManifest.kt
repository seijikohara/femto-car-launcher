package io.github.seijikohara.femto.data.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The manifest schema this build reads (CI's update-manifest action writes
 * it). A manifest of any other version is no information, never a guess.
 */
internal const val SUPPORTED_MANIFEST_SCHEMA_VERSION = 1

// A SHA-256 digest as CI's sha256sum prints it: 64 lowercase hex digits.
private val Sha256Hex = Regex("[0-9a-f]{64}")

// ignoreUnknownKeys: a schema-1 manifest may gain fields this build does not
// know, and those must not make it unreadable.
private val ManifestJson = Json { ignoreUnknownKeys = true }

/**
 * The manifest CI publishes beside every release APK: which build the
 * channel's latest release carries, and the size and SHA-256 the downloaded
 * APK must match. No field has a default, so a manifest missing any of them —
 * the hash above all — fails to parse and reads as no information: an update
 * is never offered without a way to verify it.
 */
@Serializable
internal data class UpdateManifest(
    val schemaVersion: Int,
    val channel: String,
    val versionCode: Int,
    val versionName: String,
    val apk: Apk,
) {
    @Serializable
    data class Apk(
        val name: String,
        val size: Long,
        val sha256: String,
        val url: String,
    )
}

/**
 * True when a build of [channel] may act on this manifest: the supported
 * schema, the same channel, and an APK described well enough to verify.
 */
internal fun UpdateManifest.isUsableFor(channel: UpdateChannel): Boolean =
    schemaVersion == SUPPORTED_MANIFEST_SCHEMA_VERSION &&
        this.channel == channel.id &&
        apk.size > 0 &&
        Sha256Hex.matches(apk.sha256)

/** Decode [text] as a manifest; throws when it is not one. */
internal fun parseUpdateManifest(text: String): UpdateManifest = ManifestJson.decodeFromString<UpdateManifest>(text)

internal fun UpdateManifest.toJson(): String = ManifestJson.encodeToString(this)
