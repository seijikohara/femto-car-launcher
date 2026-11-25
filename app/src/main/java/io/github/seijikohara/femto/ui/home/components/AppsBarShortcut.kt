package io.github.seijikohara.femto.ui.home.components

/**
 * Dock shortcut referenced by [io.github.seijikohara.femto.ui.home.HomeAction.Shortcut].
 *
 * Each one opens whichever app the device uses for the job, never a
 * hard-coded package; `HomeViewModel` maps it to the `HomeEvent` that says how.
 * The production dock renders bespoke navigation buttons in [DashboardDock]
 * rather than the generic tile.
 */
internal enum class AppsBarShortcut {
    /** The phone app, opened at its dial pad. */
    Phone,

    /** The app the user has elected as the default music app. */
    Music,
}
