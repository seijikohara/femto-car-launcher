package io.github.seijikohara.femto.testfixtures

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * A [LifecycleOwner] a test moves by hand, for composables that act on their
 * host's lifecycle: provide it through `LocalLifecycleOwner`. It starts
 * resumed, like a screen in front of the user. Move it on the main thread,
 * which under Robolectric is the test thread.
 */
internal class FakeLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }

    override val lifecycle: Lifecycle get() = registry

    fun moveTo(state: Lifecycle.State) {
        registry.currentState = state
    }
}
