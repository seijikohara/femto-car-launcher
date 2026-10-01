package io.github.seijikohara.femto.ui.video

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.seijikohara.femto.testfixtures.FakeLifecycleOwner
import io.github.seijikohara.femto.testfixtures.FakeVideoPlayer
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The player's surface attaches only while it is composed and the launcher is
 * on screen (started), and detaches otherwise; playback itself is the
 * player's, so a detach never touches it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VideoSurfaceTest {
    @get:Rule
    val rule = createComposeRule()

    private val player = FakeVideoPlayer()
    private val lifecycleOwner = FakeLifecycleOwner()
    private var shown by mutableStateOf(true)

    private fun setSurface() =
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                FemtoTheme {
                    if (shown) VideoSurface(host = player, modifier = Modifier.size(160.dp, 90.dp))
                }
            }
        }

    @Test
    fun `a composed surface on screen is attached`() {
        setSurface()
        rule.waitForIdle()

        assertNotNull(player.attached)
    }

    @Test
    fun `the launcher leaving the screen detaches the surface`() {
        setSurface()
        rule.waitForIdle()

        lifecycleOwner.moveTo(Lifecycle.State.CREATED)
        rule.waitForIdle()

        assertNull(player.attached)
    }

    @Test
    fun `the launcher coming back attaches the surface again`() {
        setSurface()
        rule.waitForIdle()
        lifecycleOwner.moveTo(Lifecycle.State.CREATED)
        rule.waitForIdle()

        lifecycleOwner.moveTo(Lifecycle.State.RESUMED)
        rule.waitForIdle()

        assertNotNull(player.attached)
    }

    @Test
    fun `a surface leaving the composition detaches`() {
        setSurface()
        rule.waitForIdle()

        shown = false
        rule.waitForIdle()

        assertNull(player.attached)
    }
}
