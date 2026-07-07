package io.github.seijikohara.femto.ui.home

import android.content.ComponentName
import io.github.seijikohara.femto.data.dock.DockNavId
import io.github.seijikohara.femto.data.dock.DockStatusId
import io.github.seijikohara.femto.data.music.MusicCommand
import io.github.seijikohara.femto.ui.home.components.AppsBarShortcut

internal sealed interface HomeAction {
    data class LaunchApp(
        val componentName: ComponentName,
    ) : HomeAction

    data object OpenAppDrawer : HomeAction

    data object OpenMaps : HomeAction

    data object ConnectMusicPlayer : HomeAction

    /** Open the app behind the current media session (the music card's source icon). */
    data class LaunchMusicSource(
        val packageName: String,
    ) : HomeAction

    data class Shortcut(
        val target: AppsBarShortcut,
    ) : HomeAction

    data class Music(
        val command: MusicCommand,
    ) : HomeAction

    /**
     * The empty music card's Play affordance. The host best-effort resumes the
     * last session via a synthetic media key and unconditionally also launches
     * the user's default music app, so the tap always visibly responds — there
     * is no callback confirming whether any app resumed from the key alone.
     */
    data object PlayDefaultMusic : HomeAction

    data object OpenBrowser : HomeAction

    data object OpenCalendar : HomeAction

    data object OpenWeather : HomeAction

    /** Step the persisted map zoom by [delta] (the on-map +/- buttons; clamped by the host). */
    data class AdjustMapZoom(
        val delta: Int,
    ) : HomeAction

    /** Flip the persisted north-up ⇄ heading-up camera orientation (the compass tap). */
    data object ToggleMapNorthUp : HomeAction

    data object OpenSettings : HomeAction

    data object OpenAssistant : HomeAction

    data object ResetTrip : HomeAction

    /** Toggle the transient passenger unlock (keep the cockpit while moving). Not persisted. */
    data class SetPassengerUnlock(
        val unlocked: Boolean,
    ) : HomeAction

    /** The dock's long-press menu: swap a nav button one step within the visible order. */
    data class MoveDockNav(
        val id: DockNavId,
        val direction: Int,
    ) : HomeAction

    /** The dock's long-press menu: drop a nav button from the visible order. */
    data class HideDockNav(
        val id: DockNavId,
    ) : HomeAction

    /** The status cluster's long-press menu: swap an indicator one step within the visible order. */
    data class MoveDockStatus(
        val id: DockStatusId,
        val direction: Int,
    ) : HomeAction

    /** The status cluster's long-press menu: drop an indicator from the visible order. */
    data class HideDockStatus(
        val id: DockStatusId,
    ) : HomeAction

    /** The dock/status long-press menu's "Reset dock" entry — same recovery path as Settings. */
    data object ResetDock : HomeAction
}
