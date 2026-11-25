package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.ui.settings.RoutingDataSummary
import io.github.seijikohara.femto.ui.settings.SettingsUiState

/**
 * The Settings state of a build and a map backend that offer routing, with no routing data on the device yet: the
 * state in which the Navigation category and the routing data row show.
 */
internal fun routingOfferedSettingsState(): SettingsUiState =
    SettingsUiState.Initial.copy(routingData = RoutingDataSummary(tileCount = 0, totalBytes = 0L))
