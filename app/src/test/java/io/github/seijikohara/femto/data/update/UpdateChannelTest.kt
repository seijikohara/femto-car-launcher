package io.github.seijikohara.femto.data.update

import io.github.seijikohara.femto.BuildConfig
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UpdateChannelTest {
    @Test
    fun `every channel resolves from its own flavor name`() {
        UpdateChannel.entries.forEach { channel ->
            assertEquals(channel, UpdateChannel.fromFlavorOrNull(channel.id))
        }
    }

    @Test
    fun `a flavor outside the known channels resolves to none`() {
        assertNull(UpdateChannel.fromFlavorOrNull("stableFoss"))
    }

    @Test
    fun `the unit-test build's flavor resolves to the stable channel`() {
        // Guards the ids against a flavor rename in the Gradle build: a flavor
        // that no longer resolves would silently switch the updater off.
        assertEquals(UpdateChannel.STABLE, UpdateChannel.fromFlavorOrNull(BuildConfig.FLAVOR))
    }
}
