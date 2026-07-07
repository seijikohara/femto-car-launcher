package io.github.seijikohara.femto.data.fonts

import org.junit.Test
import kotlin.test.assertEquals

class WeightFromFileNameTest {
    @Test
    fun `a file with no recognised token defaults to the normal weight`() {
        assertEquals(400, weightFromFileName("NotoSansCJK-Regular.ttc"))
        assertEquals(400, weightFromFileName("SomeDisplayFace.ttf"))
    }

    @Test
    fun `an exact weight token maps to its CSS weight`() {
        assertEquals(700, weightFromFileName("Roboto-Bold.ttf"))
        assertEquals(300, weightFromFileName("Roboto-Light.ttf"))
    }

    @Test
    fun `ExtraBold is not shadowed by the shorter Bold substring it contains`() {
        assertEquals(800, weightFromFileName("Roboto-ExtraBold.ttf"))
    }

    @Test
    fun `SemiBold is not shadowed by the shorter Bold substring it contains`() {
        assertEquals(600, weightFromFileName("Roboto-SemiBold.ttf"))
    }

    @Test
    fun `ExtraLight is not shadowed by the shorter Light substring it contains`() {
        assertEquals(200, weightFromFileName("Roboto-ExtraLight.ttf"))
    }
}
