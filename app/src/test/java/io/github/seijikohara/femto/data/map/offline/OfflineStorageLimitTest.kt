package io.github.seijikohara.femto.data.map.offline

import org.junit.Test
import kotlin.test.assertEquals

class OfflineStorageLimitTest {
    @Test
    fun `no limit allows any size`() {
        assertEquals(StorageDecision.Allowed, storageDecision(null, 900, 0, 0, 58_444_771_328L))
    }

    @Test
    fun `a total exactly at the limit is allowed`() {
        assertEquals(
            StorageDecision.Allowed,
            storageDecision(1_000_000_000L, 137_648_640L, 0, 0, 862_351_360L),
        )
    }

    @Test
    fun `one byte over the limit is refused with the excess and the limit`() {
        assertEquals(
            StorageDecision.OverLimit(1L, 1_000_000_000L),
            storageDecision(1_000_000_000L, 137_648_641L, 0, 0, 862_351_360L),
        )
    }

    @Test
    fun `a file larger than the whole limit reports the excess in Long bytes`() {
        assertEquals(
            StorageDecision.OverLimit(8_444_771_328L, 50_000_000_000L),
            storageDecision(50_000_000_000L, 0, 0, 0, 58_444_771_328L),
        )
    }

    @Test
    fun `another started download counts in full`() {
        assertEquals(StorageDecision.OverLimit(500L, 1_000L), storageDecision(1_000L, 400, 600, 0, 500))
    }

    @Test
    fun `an update that shrinks the file goes through even above a lowered limit`() {
        assertEquals(StorageDecision.Allowed, storageDecision(1_000L, 5_000, 0, 900, 800))
    }

    @Test
    fun `an update that grows the file is checked against the limit`() {
        assertEquals(StorageDecision.OverLimit(4_100L, 1_000L), storageDecision(1_000L, 5_000, 0, 900, 1_000))
    }

    @Test
    fun `the limit options are decimal gigabytes in ascending order`() {
        assertEquals(
            listOf(
                null,
                1_000_000_000L,
                2_000_000_000L,
                5_000_000_000L,
                10_000_000_000L,
                20_000_000_000L,
                50_000_000_000L,
            ),
            OfflineStorageLimit.entries.map { it.bytes },
        )
    }
}
