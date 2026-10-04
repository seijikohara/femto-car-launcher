package io.github.seijikohara.femto.testfixtures

import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

/**
 * An IO dispatcher a test can stop. While [hold] is off it runs work inline,
 * on the caller's thread; while on, it keeps each dispatched block until
 * [releaseNewestFirst] runs them. Running the newest block first lets a test
 * run two IO steps in the opposite order to the one they were started in,
 * the interleaving a real IO pool may produce.
 */
internal class HeldDispatcher : CoroutineDispatcher() {
    var hold = false
    private val held = ArrayDeque<Runnable>()

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = hold

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        held.addLast(block)
    }

    /** Stop holding, and run the held blocks now, on the caller's thread, newest first. */
    fun releaseNewestFirst() {
        hold = false
        generateSequence { held.removeLastOrNull() }.forEach(Runnable::run)
    }
}
