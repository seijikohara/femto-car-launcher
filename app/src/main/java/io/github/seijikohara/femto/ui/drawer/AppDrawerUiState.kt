package io.github.seijikohara.femto.ui.drawer

import io.github.seijikohara.femto.data.apps.AppEntry

/**
 * Render state for the app drawer.
 *
 * [Content] with an empty [Content.apps] list is the legitimate
 * "no apps installed" case — it is distinct from [Loading], which
 * means the query has not finished yet, and from [Error], which means
 * the query failed. The drawer surface previously collapsed all three
 * into a blank grid, leaving the user unable to tell a slow or failed
 * query from a genuinely empty device.
 */
internal sealed interface AppDrawerUiState {
    data object Loading : AppDrawerUiState

    data class Content(
        val apps: List<AppEntry>,
        /**
         * Top-N most-recently-launched apps, most-recent-first (see
         * `RecentAppsPreferences.RECENT_APPS_MAX_COUNT`). Empty on a fresh
         * install / before the first drawer launch — the Recent row hides
         * itself entirely in that case rather than rendering blank.
         */
        val recentApps: List<AppEntry> = emptyList(),
    ) : AppDrawerUiState

    data object Error : AppDrawerUiState
}

/**
 * Drawer events handled by [AppDrawerViewModel]. [Refresh] re-runs the app
 * query: dispatched on every sheet open (so installs/uninstalls since the
 * last open appear) and from the error state's retry affordance.
 */
internal sealed interface AppDrawerAction {
    data object Refresh : AppDrawerAction
}
