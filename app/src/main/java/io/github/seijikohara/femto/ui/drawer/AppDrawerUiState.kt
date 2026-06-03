package io.github.seijikohara.femto.ui.drawer

import io.github.seijikohara.femto.data.AppEntry

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
    ) : AppDrawerUiState

    data object Error : AppDrawerUiState
}
