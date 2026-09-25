package io.github.seijikohara.femto.data.common

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Shared `stateIn` / `shareIn` start policy for flows the UI subscribes to.
 *
 * The 5 s grace keeps the upstream (location callbacks, broadcast receivers,
 * DataStore reads) alive across a configuration change or Activity recreation
 * — the new subscriber reattaches before the timeout — while still parking the
 * upstream when the launcher genuinely leaves the foreground.
 */
internal val WhileUiSubscribed: SharingStarted = SharingStarted.WhileSubscribed(5_000)

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
