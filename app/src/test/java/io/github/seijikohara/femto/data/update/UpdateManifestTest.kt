package io.github.seijikohara.femto.data.update

import io.github.seijikohara.femto.testfixtures.FAKE_FEED_BASE
import io.github.seijikohara.femto.testfixtures.fakeUpdateManifest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateManifestTest {
    @Test
    fun `a manifest for the build's channel is usable`() {
        val manifest = fakeUpdateManifest(26092402, channel = UpdateChannel.NIGHTLY)

        assertTrue(manifest.isUsableFor(UpdateChannel.NIGHTLY, FAKE_FEED_BASE))
    }

    @Test
    fun `a manifest for the other channel is not usable`() {
        val manifest = fakeUpdateManifest(26092402, channel = UpdateChannel.NIGHTLY)

        assertFalse(manifest.isUsableFor(UpdateChannel.STABLE, FAKE_FEED_BASE))
    }

    @Test
    fun `a manifest of an unsupported schema version is not usable`() {
        val manifest = fakeUpdateManifest(26092402, schemaVersion = SUPPORTED_MANIFEST_SCHEMA_VERSION + 1)

        assertFalse(manifest.isUsableFor(UpdateChannel.STABLE, FAKE_FEED_BASE))
    }

    @Test
    fun `a sha256 that is not 64 lowercase hex digits is not usable`() {
        // The integrity check fails closed: a digest it cannot compare is no digest.
        listOf("", "0123", "F".repeat(64), "g".repeat(64), "a".repeat(65)).forEach { sha256 ->
            val manifest = fakeUpdateManifest(26092402, sha256 = sha256)

            assertFalse(manifest.isUsableFor(UpdateChannel.STABLE, FAKE_FEED_BASE), sha256)
        }
    }

    @Test
    fun `an APK without a positive size is not usable`() {
        assertFalse(fakeUpdateManifest(26092402, size = 0).isUsableFor(UpdateChannel.STABLE, FAKE_FEED_BASE))
    }

    @Test
    fun `an APK larger than the size cap is not usable`() {
        // The download reads up to one byte past the size it expects, so a
        // bogus size would otherwise fill the cache.
        val atCap = fakeUpdateManifest(26092402, size = MAX_APK_SIZE_BYTES)
        val pastCap = fakeUpdateManifest(26092402, size = MAX_APK_SIZE_BYTES + 1)

        assertTrue(atCap.isUsableFor(UpdateChannel.STABLE, FAKE_FEED_BASE))
        assertFalse(pastCap.isUsableFor(UpdateChannel.STABLE, FAKE_FEED_BASE))
    }

    @Test
    fun `an APK on another host than the feed's is not usable`() {
        // Every downloading client would send its address and User-Agent there.
        val manifest = fakeUpdateManifest(26092402, url = "https://elsewhere.test/femto-car-launcher-v26092402.apk")

        assertFalse(manifest.isUsableFor(UpdateChannel.STABLE, FAKE_FEED_BASE))
    }

    @Test
    fun `an APK over another scheme than the feed's is not usable`() {
        val cleartext = FAKE_FEED_BASE.replaceFirst("https://", "http://")
        val manifest = fakeUpdateManifest(26092402, url = "$cleartext/download/v26092402/femto-car-launcher.apk")

        assertFalse(manifest.isUsableFor(UpdateChannel.STABLE, FAKE_FEED_BASE))
    }

    @Test
    fun `an APK URL that is not an HTTP URL is not usable`() {
        val manifest = fakeUpdateManifest(26092402, url = "file:///sdcard/femto-car-launcher.apk")

        assertFalse(manifest.isUsableFor(UpdateChannel.STABLE, FAKE_FEED_BASE))
    }

    @Test
    fun `a manifest round-trips through its JSON form`() {
        val manifest = fakeUpdateManifest(26092402)

        assertEquals(manifest, parseUpdateManifest(manifest.toJson()))
    }

    @Test
    fun `fields a later schema-1 manifest adds do not make it unreadable`() {
        val manifest = fakeUpdateManifest(26092402)
        val extended = manifest.toJson().replaceFirst("{", "{\"minSdk\":33,")

        assertEquals(manifest, parseUpdateManifest(extended))
    }
}
