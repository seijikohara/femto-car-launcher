package io.github.seijikohara.femto.ui.home

import android.content.ComponentName
import io.github.seijikohara.femto.data.dock.DockNavId
import io.github.seijikohara.femto.data.dock.DockStatusId
import io.github.seijikohara.femto.data.music.MusicCommand
import io.github.seijikohara.femto.data.places.PlaceTarget
import io.github.seijikohara.femto.ui.home.components.AppsBarShortcut

internal sealed interface HomeAction {
    data class LaunchApp(
        val componentName: ComponentName,
    ) : HomeAction

    data object OpenAppDrawer : HomeAction

    /**
     * Open the maps app at the current position: the map's own tap and the
     * destination panel's header link.
     */
    data object OpenMaps : HomeAction

    /**
     * The dock's Navigation button: open the destination panel. Handled at the
     * dashboard overlay layer (DashboardContent intercepts it, as it does
     * [OpenAppDrawer]); a no-op if it ever reaches the ViewModel.
     */
    data object OpenDestinations : HomeAction

    /**
     * Hand [target] to the user's navigation app: the destination panel's
     * Navigate (a typed or dictated query) or a tap on a saved place. [label]
     * names the pin a saved point drops; a query ignores it.
     */
    data class Navigate(
        val target: PlaceTarget,
        val label: String = "",
    ) : HomeAction

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

    /**
     * The update prompt's "Later" (or its dismissal) for build [versionCode]:
     * the prompt never asks about it again, while the dock's dot and Settings
     * keep offering it.
     */
    data class UpdateLater(
        val versionCode: Int,
    ) : HomeAction

    /**
     * The update prompt's "Update" for build [versionCode]: the prompt never
     * asks about it again, and Settings opens on Updates to start the one-tap
     * update there.
     */
    data class UpdateNow(
        val versionCode: Int,
    ) : HomeAction

    /** Open the licences and credits screen (a tap on the map or weather credit). */
    data object OpenLicenses : HomeAction

    data object OpenAssistant : HomeAction

    data object ResetTrip : HomeAction

    /** The dock's long-press menu: swap a nav button one step within the visible order. */
    data class MoveDockNav(
        val id: DockNavId,
        val direction: Int,
    ) : HomeAction

    /**
     * The dock's edit-mode drag-reorder commit: the new full nav order (hidden
     * ids kept at their slots by the caller), persisted wholesale via
     * `DockSettingsStore.setNavOrder`.
     */
    data class SetDockNavOrder(
        val order: List<DockNavId>,
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
