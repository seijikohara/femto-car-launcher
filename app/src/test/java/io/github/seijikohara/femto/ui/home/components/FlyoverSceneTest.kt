package io.github.seijikohara.femto.ui.home.components

import io.github.seijikohara.femto.data.location.TripScenePalette
import io.github.seijikohara.femto.ui.theme.TripSceneBackground
import org.junit.Test
import kotlin.test.assertEquals

class FlyoverSceneTest {
    // TripScenePalette.Dark.background is a hand-mirrored copy of the Color.kt
    // backdrop SSOT (data/ cannot import ui/). Pin them so editing the hex in one
    // place fails the build rather than silently drifting the default/test stand-in.
    @Test
    fun `dark palette backdrop mirrors the TripSceneBackground SSOT`() {
        val background = TripScenePalette.Dark.background
        assertEquals(TripSceneBackground.red, background[0], 1e-6f)
        assertEquals(TripSceneBackground.green, background[1], 1e-6f)
        assertEquals(TripSceneBackground.blue, background[2], 1e-6f)
    }
}
