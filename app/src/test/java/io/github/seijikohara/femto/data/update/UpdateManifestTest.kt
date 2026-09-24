package io.github.seijikohara.femto.data.update

import io.github.seijikohara.femto.testfixtures.fakeUpdateManifest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateManifestTest {
    @Test
    fun `a manifest for the build's channel is usable`() {
        assertTrue(fakeUpdateManifest(26092402, channel = UpdateChannel.NIGHTLY).isUsableFor(UpdateChannel.NIGHTLY))
    }

    @Test
    fun `a manifest for the other channel is not usable`() {
        assertFalse(fakeUpdateManifest(26092402, channel = UpdateChannel.NIGHTLY).isUsableFor(UpdateChannel.STABLE))
    }

    @Test
    fun `a manifest of an unsupported schema version is not usable`() {
        val manifest = fakeUpdateManifest(26092402, schemaVersion = SUPPORTED_MANIFEST_SCHEMA_VERSION + 1)

        assertFalse(manifest.isUsableFor(UpdateChannel.STABLE))
    }

    @Test
    fun `a sha256 that is not 64 lowercase hex digits is not usable`() {
        // The integrity check fails closed: a digest it cannot compare is no digest.
        listOf("", "0123", "F".repeat(64), "g".repeat(64), "a".repeat(65)).forEach { sha256 ->
            assertFalse(fakeUpdateManifest(26092402, sha256 = sha256).isUsableFor(UpdateChannel.STABLE), sha256)
        }
    }

    @Test
    fun `an APK without a positive size is not usable`() {
        assertFalse(fakeUpdateManifest(26092402, size = 0).isUsableFor(UpdateChannel.STABLE))
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
