package io.github.seijikohara.femto.data

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Process-wide nudge for permission-gated flows.
 *
 * A dangerous runtime permission (e.g. `BLUETOOTH_CONNECT`) has no
 * ContentObserver to watch, unlike the notification-listener setting that
 * [MusicSessionRepository] observes. The in-app permission dialog keeps the
 * activity merely PAUSED (not STOPPED), so a grant does not restart the
 * permission-gated collectors. This lightweight signal lets `MainActivity`
 * re-trigger a re-read once a permission result lands, without waiting for a
 * domain broadcast or an app restart.
 */
internal object SystemPermissionSignals {
    // Emitted when a runtime permission result lands so permission-gated flows
    // can re-read without waiting for a domain event or an app restart.
    val refreshes: MutableSharedFlow<Unit> = MutableSharedFlow(extraBufferCapacity = 1)
}
