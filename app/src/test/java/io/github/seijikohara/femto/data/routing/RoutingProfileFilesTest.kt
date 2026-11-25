package io.github.seijikohara.femto.data.routing

import io.github.seijikohara.femto.testfixtures.shippedAssetFile
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutingProfileFilesTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `the runtime files are copied side by side and the profile is returned`() {
        val profile = profileFiles().prepare()

        assertEquals(File(dir(), CAR_PROFILE), profile)
        assertEquals(listOf(CAR_PROFILE, "lookups.dat"), dir().list()?.sorted())
        assertEquals(shippedAsset("lookups.dat").readText(), File(dir(), "lookups.dat").readText())
    }

    @Test
    fun `a copy that differs from the asset is rewritten`() {
        val profile = profileFiles().prepare()
        profile.writeText("stale")

        profileFiles().prepare()

        assertEquals(shippedAsset(CAR_PROFILE).readText(), profile.readText())
    }

    // The engine instantiates the profile's model by name; proguard-rules.pro keeps every subclass of OsmPathModel
    // with its no-argument constructor, so a profile that named any other class would break only in a minified build.
    @Test
    fun `the shipped profile names a path model the keep rule covers`() {
        val modelClass =
            MODEL_DECLARATION
                .find(shippedAsset(CAR_PROFILE).readText())
                ?.groupValues
                ?.get(1)
                ?: error("no model in $CAR_PROFILE")

        val model = Class.forName(modelClass)

        assertTrue(Class.forName("btools.router.OsmPathModel").isAssignableFrom(model))
        model.getDeclaredConstructor()
    }

    // The engine replaces the turn instruction mode the app sets with the profile's own value, unless that value is 1
    // ("automatic"). A profile update that assigned anything else would switch the turn hints off or change their
    // form, and no unit test runs a real search that would show it.
    @Test
    fun `the shipped profile leaves the turn instruction mode to the app`() {
        val assigned =
            TURN_INSTRUCTION_MODE_ASSIGNMENT
                .find(shippedAsset(CAR_PROFILE).readText())
                ?.groupValues
                ?.get(1)

        assertEquals("1", assigned)
    }

    private fun profileFiles() = RoutingProfileFiles(dir()) { path -> shippedAssetFile(path).inputStream() }

    private fun dir(): File = File(tempFolder.root, "profiles")

    private fun shippedAsset(name: String): File = shippedAssetFile("$ROUTING_ASSET_DIR/$name")

    private companion object {
        val MODEL_DECLARATION = Regex("""(?m)^---model:(\S+)""")
        val TURN_INSTRUCTION_MODE_ASSIGNMENT = Regex("""(?m)^assign\s+turnInstructionMode\s*=\s*(\S+)""")
    }
}
