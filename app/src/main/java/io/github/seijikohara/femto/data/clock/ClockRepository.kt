package io.github.seijikohara.femto.data.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import io.github.seijikohara.femto.data.common.WhileUiSubscribed
import io.github.seijikohara.femto.data.location.LocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import java.time.ZoneId
import java.time.ZonedDateTime

internal class ClockRepository(
    private val context: Context,
    // Read per tick rather than captured at construction: the repository
    // outlives timezone changes (a phone mounted as car nav crosses borders),
    // and a captured ZoneId would pin the clock to the old zone until the
    // process dies.
    private val zoneProvider: () -> ZoneId = ZoneId::systemDefault,
    // Owns the single shared receiver subscription (mirrors LocationRepository);
    // tests inject their own scope to drive shareIn deterministically. The
    // default scope is process-lifetime by design — it is never cancelled;
    // [WhileUiSubscribed] parks the upstream (and unregisters the receiver) when
    // no collector is live.
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    // One receiver serves every collector: tickFlow() feeds the ViewModel, the
    // weather repository, and the calendar repository, and a cold flow would
    // register three receivers for the same system broadcast.
    private val shared: Flow<ClockTick> =
        callbackFlow {
            val emit: () -> Unit = { trySend(currentTick()) }
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        c: Context?,
                        intent: Intent?,
                    ) = emit()
                }
            emit()
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(Intent.ACTION_TIME_TICK).apply {
                    // TIME_TICK alone leaves a manual time set or a timezone
                    // change wrong for up to a minute; both broadcasts re-tick
                    // immediately so the dashboard follows without a restart.
                    addAction(Intent.ACTION_TIME_CHANGED)
                    addAction(Intent.ACTION_TIMEZONE_CHANGED)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            awaitClose { context.unregisterReceiver(receiver) }
        }.flowOn(Dispatchers.Main.immediate)
            .shareIn(scope, WhileUiSubscribed, replay = 1)

    fun tickFlow(): Flow<ClockTick> = shared

    private fun currentTick(): ClockTick =
        ZonedDateTime
            .now(zoneProvider())
            .let { ClockTick(time = it.toLocalTime().withSecond(0).withNano(0), date = it.toLocalDate()) }
}
