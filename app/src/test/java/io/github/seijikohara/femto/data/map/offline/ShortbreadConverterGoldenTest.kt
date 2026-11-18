package io.github.seijikohara.femto.data.map.offline

import io.github.seijikohara.femto.testfixtures.OfflineTileFixtureNames
import io.github.seijikohara.femto.testfixtures.gunzipped
import io.github.seijikohara.femto.testfixtures.offlineTileResource
import io.github.seijikohara.femto.testfixtures.resolved
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.util.zip.GZIPOutputStream
import kotlin.test.assertEquals
import kotlin.test.fail

/** Converts each Shortbread fixture tile and compares it with the reference conversion stored beside it. */
@RunWith(Parameterized::class)
class ShortbreadConverterGoldenTest(
    private val name: String,
) {
    @Test
    fun `the converted tile equals the reference conversion`() {
        // `<region>-<z>-<x>-<y>`: the zoom is the first number after the region.
        val zoom = name.split('-')[1].toInt()
        val converted = checkNotNull(ShortbreadConverter.convertOrNull(zoom, offlineTileResource("$name.sb.mvt.gz")))

        if (System.getenv("FEMTO_UPDATE_GOLDENS") == "1") {
            // The unit-test working directory is `app/`.
            File("src/test/resources/offlinemap/$name.omt.mvt.gz").outputStream().use { file ->
                GZIPOutputStream(file).use { it.write(converted) }
            }
            fail("Golden $name was rewritten; rerun without FEMTO_UPDATE_GOLDENS")
        }

        val golden = gunzipped(offlineTileResource("$name.omt.mvt.gz"))
        assertEquals(
            checkNotNull(VectorTileCodec.decodeOrNull(golden)).resolved(),
            checkNotNull(VectorTileCodec.decodeOrNull(converted)).resolved(),
        )
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun names(): List<String> = OfflineTileFixtureNames
    }
}
