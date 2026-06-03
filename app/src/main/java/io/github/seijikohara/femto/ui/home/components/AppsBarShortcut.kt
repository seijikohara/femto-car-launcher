package io.github.seijikohara.femto.ui.home.components

/**
 * Apps shortcut category referenced by [io.github.seijikohara.femto.ui.home.HomeAction.Shortcut].
 *
 * `intentCategory` matches the value the launcher dispatches via
 * `HomeEvent.LaunchAppCategory`, deferring the actual app pick to whichever
 * app the user has elected as the default for that category. The production
 * footer renders bespoke navigation buttons in [DashboardFooter] rather than
 * the generic tile.
 */
internal enum class AppsBarShortcut(
    val intentCategory: String,
) {
    // TODO: Phone maps to APP_CONTACTS, not a dialer. A proper dialer entry
    //  needs ACTION_DIAL plumbing; align the category and footer label then.
    Phone("android.intent.category.APP_CONTACTS"),
    Music("android.intent.category.APP_MUSIC"),
}
