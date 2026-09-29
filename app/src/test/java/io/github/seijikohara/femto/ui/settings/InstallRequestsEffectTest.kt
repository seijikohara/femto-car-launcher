package io.github.seijikohara.femto.ui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.seijikohara.femto.testfixtures.FakeLifecycleOwner
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * The one-tap update's install requests run the install as a tap does, and
 * the effect collects them only while the screen is started. What keeps a
 * request from being made once the screen has stopped is the ViewModel's side
 * (UpdatesHidden on ON_STOP ends the mark; see SettingsViewModelTest), not
 * this effect.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InstallRequestsEffectTest {
    @get:Rule
    val rule = createComposeRule()

    private val lifecycleOwner = FakeLifecycleOwner()
    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var installs = 0

    @Before
    fun setUp() {
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                InstallRequestsEffect(requests = requests, onRequest = { installs++ })
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `a request while the screen is started starts the install`() {
        rule.runOnIdle { requests.tryEmit(Unit) }
        rule.waitForIdle()

        assertEquals(1, installs)
    }

    @Test
    fun `the effect collects only while the screen is started`() {
        assertEquals(1, requests.subscriptionCount.value)

        rule.runOnIdle { lifecycleOwner.moveTo(Lifecycle.State.CREATED) }
        rule.waitForIdle()
        assertEquals(0, requests.subscriptionCount.value)

        rule.runOnIdle { lifecycleOwner.moveTo(Lifecycle.State.RESUMED) }
        rule.waitForIdle()
        assertEquals(1, requests.subscriptionCount.value)
    }
}
