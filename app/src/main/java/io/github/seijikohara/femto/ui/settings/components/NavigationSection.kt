package io.github.seijikohara.femto.ui.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.routing.RouteRowTime
import io.github.seijikohara.femto.ui.settings.SettingsAction
import io.github.seijikohara.femto.ui.settings.SettingsUiState

// The Navigation category's rows (issue #389): what the route preview does
// besides the line on the map, which no switch here turns off, and under
// Guidance what the turn card shows, its two refinements only while it is on,
// and how the map draws the vehicle along the route. Settings lists the
// category only where routing is offered (visibleSettingsCategories). See
// AppearanceSection's header comment on why there is no title / reset wiring
// here.
@Composable
internal fun NavigationSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier) {
    SettingsGroup(header = stringResource(R.string.settings_navigation_route)) {
        item { shapes ->
            SwitchRow(
                title = stringResource(R.string.settings_navigation_reroute),
                checked = uiState.navigation.rerouteAutomatically,
                onCheckedChange = { onAction(SettingsAction.SetRerouteAutomatically(it)) },
                shapes = shapes,
                summary = stringResource(R.string.settings_navigation_reroute_desc),
            )
        }
        item { shapes ->
            SwitchRow(
                title = stringResource(R.string.settings_navigation_other_routes),
                checked = uiState.navigation.suggestOtherRoutes,
                onCheckedChange = { onAction(SettingsAction.SetSuggestOtherRoutes(it)) },
                shapes = shapes,
                summary = stringResource(R.string.settings_navigation_other_routes_desc),
            )
        }
        item { shapes ->
            SwitchRow(
                title = stringResource(R.string.settings_navigation_restore),
                checked = uiState.navigation.restoreRouteAfterRestart,
                onCheckedChange = { onAction(SettingsAction.SetRestoreRouteAfterRestart(it)) },
                shapes = shapes,
                summary = stringResource(R.string.settings_navigation_restore_desc),
            )
        }
        item { shapes ->
            ConnectedChoiceRow(
                title = stringResource(R.string.settings_navigation_row_time),
                options =
                    listOf(
                        RouteRowTime.ARRIVAL to stringResource(R.string.settings_navigation_row_time_arrival),
                        RouteRowTime.REMAINING to stringResource(R.string.settings_navigation_row_time_remaining),
                    ),
                selected = uiState.navigation.routeRowTime,
                onSelect = { onAction(SettingsAction.SetRouteRowTime(it)) },
                shapes = shapes,
                summary = stringResource(R.string.settings_navigation_row_time_desc),
            )
        }
    }
    SettingsGroup(header = stringResource(R.string.settings_navigation_guidance)) {
        item { shapes ->
            SwitchRow(
                title = stringResource(R.string.settings_navigation_turn_card),
                checked = uiState.navigation.turnCard,
                onCheckedChange = { onAction(SettingsAction.SetTurnCard(it)) },
                shapes = shapes,
                summary = stringResource(R.string.settings_navigation_turn_card_desc),
            )
        }
        animatedItem(visible = uiState.navigation.turnCard) { shapes ->
            SwitchRow(
                title = stringResource(R.string.settings_navigation_turn_card_next_after),
                checked = uiState.navigation.turnCardNextAfter,
                onCheckedChange = { onAction(SettingsAction.SetTurnCardNextAfter(it)) },
                shapes = shapes,
                summary = stringResource(R.string.settings_navigation_turn_card_next_after_desc),
            )
        }
        animatedItem(visible = uiState.navigation.turnCard) { shapes ->
            SwitchRow(
                title = stringResource(R.string.settings_navigation_turn_card_straight),
                checked = uiState.navigation.turnCardStraight,
                onCheckedChange = { onAction(SettingsAction.SetTurnCardStraight(it)) },
                shapes = shapes,
                summary = stringResource(R.string.settings_navigation_turn_card_straight_desc),
            )
        }
        item { shapes ->
            SwitchRow(
                title = stringResource(R.string.settings_navigation_vehicle_on_line),
                checked = uiState.navigation.vehicleOnRouteLine,
                onCheckedChange = { onAction(SettingsAction.SetVehicleOnRouteLine(it)) },
                shapes = shapes,
                summary = stringResource(R.string.settings_navigation_vehicle_on_line_desc),
            )
        }
    }
}
