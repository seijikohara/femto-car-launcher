package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.update.SUPPORTED_MANIFEST_SCHEMA_VERSION
import io.github.seijikohara.femto.data.update.UpdateChannel
import io.github.seijikohara.femto.data.update.UpdateManifest
import java.security.MessageDigest
import java.util.Locale

/** Stand-in APK bytes: the updater only ever compares their size and SHA-256. */
internal val FakeApkBody: ByteArray = "femto-car-launcher stand-in apk".encodeToByteArray()

/**
 * A manifest describing [body] the way CI's update-manifest action publishes
 * one. The digest is computed here, independently of the production code, so a
 * wrong digest format there cannot agree with itself; override [size] or
 * [sha256] to describe a file other than [body].
 */
internal fun fakeUpdateManifest(
    versionCode: Int,
    channel: UpdateChannel = UpdateChannel.STABLE,
    body: ByteArray = FakeApkBody,
    schemaVersion: Int = SUPPORTED_MANIFEST_SCHEMA_VERSION,
    size: Long = body.size.toLong(),
    sha256: String = sha256Of(body),
): UpdateManifest =
    UpdateManifest(
        schemaVersion = schemaVersion,
        channel = channel.id,
        versionCode = versionCode,
        versionName = "v$versionCode",
        apk =
            UpdateManifest.Apk(
                name = "femto-car-launcher-v$versionCode.apk",
                size = size,
                sha256 = sha256,
                url = "https://example.test/releases/download/v$versionCode/femto-car-launcher-v$versionCode.apk",
            ),
    )

private fun sha256Of(bytes: ByteArray): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { String.format(Locale.ROOT, "%02x", it) }
