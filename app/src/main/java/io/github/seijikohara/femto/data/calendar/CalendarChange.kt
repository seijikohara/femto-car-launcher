@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package io.github.seijikohara.femto.data.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce

private const val TAG = "CalendarChange"
private const val CHANGE_DEBOUNCE_MS = 500L

/**
 * Emit Unit whenever the calendar provider changes, so callers can re-query.
 * Shared by CalendarRepository (events) and CalendarCatalog (calendar list).
 *
 * Registering an observer on the calendar provider requires `READ_CALENDAR`;
 * without it `registerContentObserver` throws `SecurityException`. On a
 * launcher that would crash the home screen on every cold start until the
 * user grants the calendar, so a denied (or racing-revoked) grant skips
 * registration rather than throwing.
 */
internal fun calendarChangeFlow(context: Context): Flow<Unit> =
    callbackFlow {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    trySend(Unit)
                }
            }
        val registered =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED &&
                runCatching {
                    context.contentResolver.registerContentObserver(
                        CalendarContract.Events.CONTENT_URI,
                        // notifyForDescendants =
                        true,
                        observer,
                    )
                }.onFailure {
                    // Mirror readWindow's split: the revoke race is expected and
                    // silent, but any other fault leaves the card stale until the
                    // next rebuild-key change — that needs a trail.
                    if (it !is SecurityException) Log.e(TAG, "calendar observer registration failed", it)
                }.isSuccess
        awaitClose {
            if (registered) context.contentResolver.unregisterContentObserver(observer)
        }
    }.debounce(CHANGE_DEBOUNCE_MS)
