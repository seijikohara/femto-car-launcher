package io.github.seijikohara.femto.ui.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.display.GoogleMapType
import io.github.seijikohara.femto.data.display.MAX_MAP_ZOOM
import io.github.seijikohara.femto.data.display.MIN_MAP_ZOOM
import io.github.seijikohara.femto.data.display.MapBackend
import io.github.seijikohara.femto.data.display.MapboxStyle
import io.github.seijikohara.femto.ui.settings.SettingsAction
import io.github.seijikohara.femto.ui.settings.SettingsUiState
import io.github.seijikohara.femto.ui.theme.FemtoDimens

private const val MIN_MAP_TILT = 0
private const val MAX_MAP_TILT = 60
private const val MIN_MAP_MARKER_POS = 0
private const val MAX_MAP_MARKER_POS = 100

// The Map category's rows; see AppearanceSection's header comment on why
// there is no title / reset wiring here. The token / key / Map ID entry
// dialogs sit as siblings of the row Column (below), not nested inside it —
// they are plain AlertDialogs and render in their own window regardless of
// where they are declared.
@Composable
internal fun MapSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showTokenDialog by remember { mutableStateOf(false) }
    var showGoogleKeyDialog by remember { mutableStateOf(false) }
    var showGoogleMapIdDialog by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        // Selecting Mapbox without a token, or Google Maps without an API key, opens
        // the respective entry dialog instead of persisting the backend switch —
        // MapSection owns this interception because the dialogs live here.
        ChoiceRow(
            title = stringResource(R.string.settings_map_backend),
            options =
                listOf(
                    MapBackend.OSM to stringResource(R.string.settings_map_backend_osm),
                    MapBackend.MAPBOX to stringResource(R.string.settings_map_backend_mapbox),
                    MapBackend.GOOGLEMAPS to stringResource(R.string.settings_map_backend_googlemaps),
                ),
            selected = uiState.mapBackend,
            onSelect = { backend ->
                onAction(SettingsAction.SetMapBackend(backend))
                when {
                    backend == MapBackend.MAPBOX && uiState.mapboxAccessToken.isBlank() -> {
                        showTokenDialog = true
                    }

                    backend == MapBackend.GOOGLEMAPS && uiState.googleMapsApiKey.isBlank() -> {
                        showGoogleKeyDialog = true
                    }
                }
            },
        )
        AnimatedVisibility(visible = uiState.mapBackend == MapBackend.MAPBOX) {
            Column {
                ChoiceRow(
                    title = stringResource(R.string.settings_mapbox_style),
                    options =
                        listOf(
                            MapboxStyle.STANDARD to stringResource(R.string.settings_mapbox_style_standard),
                            MapboxStyle.SATELLITE to stringResource(R.string.settings_mapbox_style_satellite),
                            MapboxStyle.STREETS to stringResource(R.string.settings_mapbox_style_streets),
                        ),
                    selected = uiState.mapboxStyle,
                    onSelect = { onAction(SettingsAction.SetMapboxStyle(it)) },
                )
                SwitchRow(
                    title = stringResource(R.string.settings_mapbox_traffic),
                    checked = uiState.mapboxTraffic,
                    onCheckedChange = { onAction(SettingsAction.SetMapboxTraffic(it)) },
                )
                SettingRow(
                    title = stringResource(R.string.settings_mapbox_token),
                    summary = mapboxTokenSummary(uiState.mapboxAccessToken),
                    modifier = Modifier.clickable { showTokenDialog = true },
                ) {
                    TrailingIcon(Lucide.ChevronRight)
                }
                Text(
                    text = stringResource(R.string.settings_map_accent_osm_only_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
        AnimatedVisibility(visible = uiState.mapBackend == MapBackend.GOOGLEMAPS) {
            Column {
                ChoiceRow(
                    title = stringResource(R.string.settings_google_maps_type),
                    options =
                        listOf(
                            GoogleMapType.ROADMAP to stringResource(R.string.settings_google_maps_type_roadmap),
                            GoogleMapType.SATELLITE to stringResource(R.string.settings_google_maps_type_satellite),
                            GoogleMapType.HYBRID to stringResource(R.string.settings_google_maps_type_hybrid),
                            GoogleMapType.TERRAIN to stringResource(R.string.settings_google_maps_type_terrain),
                        ),
                    selected = uiState.googleMapsMapType,
                    onSelect = { onAction(SettingsAction.SetGoogleMapsMapType(it)) },
                )
                SwitchRow(
                    title = stringResource(R.string.settings_google_maps_traffic),
                    checked = uiState.googleMapsTraffic,
                    onCheckedChange = { onAction(SettingsAction.SetGoogleMapsTraffic(it)) },
                )
                SettingRow(
                    title = stringResource(R.string.settings_google_maps_key),
                    summary = googleMapsKeySummary(uiState.googleMapsApiKey),
                    modifier = Modifier.clickable { showGoogleKeyDialog = true },
                ) {
                    TrailingIcon(Lucide.ChevronRight)
                }
                SettingRow(
                    title = stringResource(R.string.settings_google_maps_map_id),
                    summary = googleMapsMapIdSummary(uiState.googleMapsMapId),
                    modifier = Modifier.clickable { showGoogleMapIdDialog = true },
                ) {
                    TrailingIcon(Lucide.ChevronRight)
                }
                Text(
                    text = stringResource(R.string.settings_map_accent_osm_only_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
        AnimatedVisibility(visible = uiState.mapBackend == MapBackend.OSM) {
            Column {
                SettingsSubheader(stringResource(R.string.settings_subheader_map_rendering))
                SwitchRow(
                    title = stringResource(R.string.settings_group_map_3d),
                    checked = uiState.map3dBuildings,
                    onCheckedChange = { onAction(SettingsAction.SetMap3dBuildings(it)) },
                )
                SwitchRow(
                    title = stringResource(R.string.settings_group_map_terrain),
                    checked = uiState.mapTerrain,
                    onCheckedChange = { onAction(SettingsAction.SetMapTerrain(it)) },
                    summary = stringResource(R.string.settings_map_terrain_desc),
                )
            }
        }
        SettingsSubheader(stringResource(R.string.settings_subheader_map_camera))
        SliderRow(
            title = stringResource(R.string.settings_group_map_tilt),
            valueLabel = stringResource(R.string.settings_map_tilt_value, uiState.mapTiltDeg),
            value = uiState.mapTiltDeg,
            range = MIN_MAP_TILT..MAX_MAP_TILT,
            onValueChange = { onAction(SettingsAction.SetMapTilt(it)) },
        )
        SliderRow(
            title = stringResource(R.string.settings_group_map_zoom),
            valueLabel = stringResource(R.string.settings_map_zoom_value, uiState.mapZoom),
            value = uiState.mapZoom,
            range = MIN_MAP_ZOOM..MAX_MAP_ZOOM,
            onValueChange = { onAction(SettingsAction.SetMapZoom(it)) },
        )
        SwitchRow(
            title = stringResource(R.string.settings_group_map_north_up),
            checked = uiState.mapNorthUp,
            onCheckedChange = { onAction(SettingsAction.SetMapNorthUp(it)) },
            summary = stringResource(R.string.settings_map_north_up_desc),
        )
        SliderRow(
            title = stringResource(R.string.settings_group_map_marker_pos),
            valueLabel = stringResource(R.string.settings_map_marker_pos_value, uiState.mapMarkerPos),
            value = uiState.mapMarkerPos,
            range = MIN_MAP_MARKER_POS..MAX_MAP_MARKER_POS,
            onValueChange = { onAction(SettingsAction.SetMapMarkerPos(it)) },
        )
    }
    if (showTokenDialog) {
        var draft by remember { mutableStateOf(uiState.mapboxAccessToken) }
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            title = { Text(stringResource(R.string.settings_mapbox_token)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.settings_mapbox_token_hint)) },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = draft.isNotBlank(),
                    onClick = {
                        onAction(SettingsAction.SaveMapboxToken(draft))
                        showTokenDialog = false
                    },
                ) { Text(stringResource(R.string.settings_mapbox_token_save)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onAction(SettingsAction.ClearMapboxToken)
                        showTokenDialog = false
                    },
                ) { Text(stringResource(R.string.settings_mapbox_token_clear)) }
            },
        )
    }
    if (showGoogleKeyDialog) {
        var draft by remember { mutableStateOf(uiState.googleMapsApiKey) }
        AlertDialog(
            onDismissRequest = { showGoogleKeyDialog = false },
            title = { Text(stringResource(R.string.settings_google_maps_key)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_google_maps_key_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.settings_google_maps_key)) },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = draft.isNotBlank(),
                    onClick = {
                        onAction(SettingsAction.SaveGoogleMapsKey(draft))
                        showGoogleKeyDialog = false
                    },
                    modifier = Modifier.heightIn(min = FemtoDimens.MinTouchTarget),
                ) { Text(stringResource(R.string.settings_google_maps_key_save)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onAction(SettingsAction.ClearGoogleMapsKey)
                        showGoogleKeyDialog = false
                    },
                    modifier = Modifier.heightIn(min = FemtoDimens.MinTouchTarget),
                ) { Text(stringResource(R.string.settings_google_maps_key_clear)) }
            },
        )
    }
    if (showGoogleMapIdDialog) {
        var draft by remember { mutableStateOf(uiState.googleMapsMapId) }
        AlertDialog(
            onDismissRequest = { showGoogleMapIdDialog = false },
            title = { Text(stringResource(R.string.settings_google_maps_map_id)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_google_maps_map_id_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.settings_google_maps_map_id)) },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = true,
                    onClick = {
                        onAction(SettingsAction.SetGoogleMapsMapId(draft))
                        showGoogleMapIdDialog = false
                    },
                    modifier = Modifier.heightIn(min = FemtoDimens.MinTouchTarget),
                ) { Text(stringResource(R.string.settings_google_maps_map_id_save)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onAction(SettingsAction.ClearGoogleMapsMapId)
                        showGoogleMapIdDialog = false
                    },
                    modifier = Modifier.heightIn(min = FemtoDimens.MinTouchTarget),
                ) { Text(stringResource(R.string.settings_google_maps_map_id_clear)) }
            },
        )
    }
}

// Masks all but the last four characters of the token so it is not fully visible
// on a shared in-car screen, while still letting the user confirm which key is set.
@Composable
private fun mapboxTokenSummary(token: String): String =
    if (token.isBlank()) {
        stringResource(R.string.settings_mapbox_token_unset)
    } else {
        "••••" + token.takeLast(4)
    }

// Same masking pattern as mapboxTokenSummary, applied to the Google Maps API key.
@Composable
private fun googleMapsKeySummary(key: String): String =
    if (key.isBlank()) {
        stringResource(R.string.settings_google_maps_key_unset)
    } else {
        "••••" + key.takeLast(4)
    }

// The Map ID is not secret (it only names a console-defined style), so it is
// shown verbatim — no masking, unlike the API key above.
@Composable
private fun googleMapsMapIdSummary(mapId: String): String =
    mapId.ifBlank { stringResource(R.string.settings_google_maps_map_id_unset) }
