package io.github.seijikohara.femto.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.time.ZoneId
import java.time.ZonedDateTime

internal class ClockRepository(
    private val context: Context,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    fun tickFlow(): Flow<ClockTick> =
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
                IntentFilter(Intent.ACTION_TIME_TICK),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            awaitClose { context.unregisterReceiver(receiver) }
        }.flowOn(Dispatchers.Main.immediate)

    private fun currentTick(): ClockTick =
        ZonedDateTime
            .now(zone)
            .let { ClockTick(time = it.toLocalTime().withSecond(0).withNano(0), date = it.toLocalDate()) }
}
