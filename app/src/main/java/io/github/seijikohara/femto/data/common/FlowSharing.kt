package io.github.seijikohara.femto.data.common

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlin.coroutines.cancellation.CancellationException

/**
 * How long a shared upstream stays alive after its last UI subscriber leaves:
 * the grace [WhileUiSubscribed] gives a configuration change. Internal so tests
 * step past it.
 */
internal const val UI_SUBSCRIPTION_GRACE_MS = 5_000L

/**
 * Shared `stateIn` / `shareIn` start policy for flows the UI subscribes to.
 *
 * The 5 s grace keeps the upstream (location callbacks, broadcast receivers,
 * DataStore reads) alive across a configuration change or Activity recreation
 * — the new subscriber reattaches before the timeout — while still parking the
 * upstream when the launcher genuinely leaves the foreground.
 */
internal val WhileUiSubscribed: SharingStarted = SharingStarted.WhileSubscribed(UI_SUBSCRIPTION_GRACE_MS)

/**
 * [WhileUiSubscribed] for state that must never show stale, without the grace:
 * the upstream stops as soon as the last UI subscriber leaves, and the cached
 * value goes with it (`stopTimeoutMillis = 0`, `replayExpirationMillis = 0`).
 * A UI that comes back, even a second later, starts from the initial value
 * and waits for a fresh one. A grace would keep the upstream, and its value,
 * alive through the return, and that value can predate what happened in the
 * meantime. The price is that every return, a configuration change included,
 * restarts the upstream from scratch. For state that a stale value makes
 * unsafe: the dashboard's update prompt, which a stale value would put up
 * again while the vehicle moves.
 */
internal val WhileUiSubscribedFresh: SharingStarted =
    SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0)

/**
 * Replace a failure of [source], one slot of a ViewModel's combined UI state,
 * with that slot's neutral [default], logged under [tag]. One broken
 * repository then degrades only its own slot. Otherwise it would fail the
 * whole combine, and the `stateIn` behind it would crash the HOME process.
 *
 * Cancellation is rethrown to keep structured concurrency intact. By design,
 * the failed source then completes for the rest of the current subscription
 * epoch: its slot stays at [default] until [WhileUiSubscribed] tears the chain
 * down and a later subscriber collects the cold sources again from scratch.
 * There is no automatic retry within an epoch, because a broken system service
 * would turn a retry loop into a battery drain on the head unit.
 */
internal fun <T> Flow<T>.catchAsDefault(
    tag: String,
    source: String,
    default: T,
): Flow<T> =
    catch { e ->
        if (e is CancellationException) throw e
        Log.e(tag, "$source flow failed", e)
        emit(default)
    }
