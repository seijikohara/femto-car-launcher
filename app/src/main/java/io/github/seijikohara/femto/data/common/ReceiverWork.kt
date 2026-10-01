package io.github.seijikohara.femto.data.common

import android.content.BroadcastReceiver
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * How long a receiver's work may run under goAsync(). A receiver may take
 * about 10 seconds, the time between goAsync() and finish() included, before
 * the system calls the app unresponsive (BroadcastReceiver#goAsync). The work
 * gets less, so finish() always lands inside that limit. The budget cancels
 * only the wait: work the receiver joined (the updater's record writes) runs
 * on in its own scope on purpose, since a record write cancelled halfway is
 * worse than one finished after the broadcast, and the launcher's process
 * normally outlives the broadcast; the budget only keeps a stuck write from
 * holding the broadcast into an ANR.
 */
internal const val RECEIVER_WORK_BUDGET_MS = 8_000L

// The receivers' work outlives their onReceive call, but not the process. The
// app keeps no application-wide scope, so this one serves every receiver.
private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Run [work] past onReceive, off the main thread, under goAsync(), and finish
 * the broadcast once the work is done, has failed, or has used up
 * [RECEIVER_WORK_BUDGET_MS]. Until then the broadcast keeps the process
 * running, so a slow disk write cannot outlive the receiver. Failures are
 * logged under [tag].
 */
internal fun BroadcastReceiver.finishAsync(
    tag: String,
    work: suspend () -> Unit,
) {
    val pending = goAsync()
    receiverScope.launchFinishing(tag, pending::finish, work)
}

/** [finishAsync]'s worker, in this scope: [finish] runs exactly once, after [work]. */
internal fun CoroutineScope.launchFinishing(
    tag: String,
    finish: () -> Unit,
    work: suspend () -> Unit,
): Job =
    launch {
        try {
            withTimeoutOrNull(RECEIVER_WORK_BUDGET_MS) { work() }
                ?: Log.w(tag, "work outlasted the broadcast's time budget")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Logged, not rethrown: an escape into the scope, which has no
            // handler, would crash the process, and the home screen with it.
            Log.w(tag, "work failed", e)
        } finally {
            finish()
        }
    }
