package io.github.seijikohara.femto.data.location

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.getSystemService
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * How long a request for the current position waits before it gives up. A receiver that is already fixing answers at
 * once; one that has to start takes a while to find its first satellites. The platform fails a request by itself after
 * a time it does not name (under a minute), which would leave the wait on screen open-ended.
 */
internal const val CURRENT_FIX_TIME_LIMIT_MS = 25_000L

/** Where the device is now, as [CurrentFixSource] answers it. */
internal data class CurrentFix(
    val latitude: Double,
    val longitude: Double,
)

/**
 * The current position on request, for a reader that cannot wait for the location flow's next fix. The flow forwards a
 * fix only once the vehicle has moved the minimum distance the user set, so a parked vehicle's last fix grows old, and
 * the platform slows the fixes of a coarse-only grant down. A request is answered apart from both.
 */
internal fun interface CurrentFixSource {
    /**
     * Return the current position, or null when no provider found one within the time limit. Cancelling the caller
     * withdraws the request.
     */
    suspend fun currentFixOrNull(): CurrentFix?
}

/**
 * [CurrentFixSource] on the platform's `getCurrentLocation`, asked of every provider the location flow reads
 * ([LocationProviders]) at once: the first fix with real coordinates ([isUsableFix]) is the answer, and the requests
 * still out are withdrawn. The flow prefers neither provider, and neither does this. The answer is null once every
 * provider has answered with none, or after [timeLimitMs].
 */
internal class PlatformCurrentFixSource(
    context: Context,
    private val timeLimitMs: Long = CURRENT_FIX_TIME_LIMIT_MS,
) : CurrentFixSource {
    private val locationManager: LocationManager = checkNotNull(context.applicationContext.getSystemService())

    override suspend fun currentFixOrNull(): CurrentFix? =
        withTimeoutOrNull(timeLimitMs) {
            channelFlow { LocationProviders.forEach { provider -> launch { send(currentLocationOrNull(provider)) } } }
                .filterNotNull()
                .firstOrNull { isUsableFix(it, lastAcceptedElapsedNanos = null) }
        }?.let { CurrentFix(latitude = it.latitude, longitude = it.longitude) }

    // One provider's answer; null from a provider that has none to give. The platform answers null for a provider
    // switched off and for one out of time, and throws for one a coarse-only grant withholds (GPS_PROVIDER) or the
    // device lacks, which must not keep the other provider's answer away.
    private suspend fun currentLocationOrNull(provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            val skip = { error: RuntimeException ->
                logProviderFailure("getCurrentLocation", provider, error)
                continuation.resume(null)
            }
            try {
                LocationManagerCompat.getCurrentLocation(
                    locationManager,
                    provider,
                    signal,
                    DirectExecutor,
                ) { fix: Location? -> continuation.resume(fix) }
            } catch (denied: SecurityException) {
                skip(denied)
            } catch (missing: IllegalArgumentException) {
                skip(missing)
            }
        }
}

// The answer only resumes a coroutine, which is safe from the platform's own callback thread.
private val DirectExecutor = Executor { it.run() }
