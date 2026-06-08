package io.github.seijikohara.femto.ui.home

import android.content.ComponentName

/**
 * One-shot side-effect signals emitted by [HomeViewModel] for the host to act on.
 *
 * Events are distinct from [HomeAction] (input) and [HomeUiState] (output): they
 * carry transient navigation / system-call requests that must not be replayed
 * on configuration change. Subscribers collect them via a `SharedFlow` so the
 * latest value is not retained.
 */
internal sealed interface HomeEvent {
    data object OpenDrawer : HomeEvent

    /** Launch a known launcher activity (typically picked from the app drawer or a home shortcut). */
    data class LaunchComponent(
        val component: ComponentName,
    ) : HomeEvent

    /**
     * Launch whichever app is registered for an
     * [android.content.Intent] selector category (e.g. `CATEGORY_APP_MAPS`).
     * Categories let the launcher defer to whichever app is installed and
     * elected by the user, rather than hard-coding a package.
     */
    data class LaunchAppCategory(
        val intentCategory: String,
    ) : HomeEvent

    /**
     * Open whichever maps app handles a `geo:` URI, centred on the given
     * coordinates. The event carries only the position; the host builds the
     * package-agnostic `geo:` intent so the user's elected maps app resolves it,
     * rather than hard-coding a provider or scheme.
     */
    data class LaunchGeo(
        val latitude: Double,
        val longitude: Double,
    ) : HomeEvent

    /** Open the system "Notification listener access" settings so the user can grant our NLS. */
    data object OpenNotificationListenerSettings : HomeEvent

    /** Open the in-app settings screen (units, theme, font, system links). */
    data object OpenInAppSettings : HomeEvent

    /** Open the voice-assistant bottom sheet so the user picks which assistant intent to fire. */
    data object OpenAssistantSheet : HomeEvent
}
