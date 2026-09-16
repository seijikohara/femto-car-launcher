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
import io.github.seijikohara.femto.BuildConfig
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.display.GoogleMapType
import io.github.seijikohara.femto.data.display.GoogleMapsRendering
import io.github.seijikohara.femto.data.display.MAX_MAP_ZOOM
import io.github.seijikohara.femto.data.display.MIN_MAP_ZOOM
import io.github.seijikohara.femto.data.display.MapBackend
import io.github.seijikohara.femto.ui.home.components.isTileHostUrl
import io.github.seijikohara.femto.ui.settings.SettingsAction
import io.github.seijikohara.femto.ui.settings.SettingsDocument
import io.github.seijikohara.femto.ui.settings.SettingsUiState
import io.github.seijikohara.femto.ui.theme.FemtoDimens

private const val MIN_MAP_TILT = 0
private const val MAX_MAP_TILT = 60
private const val MIN_MAP_MARKER_POS = 0
private const val MAX_MAP_MARKER_POS = 100

// The Map category's rows; see AppearanceSection's header comment on why
// there is no title / reset wiring here. The key / Map ID entry dialogs sit
// as siblings of the row Column (below), not nested inside it — they are
// plain AlertDialogs and render in their own window regardless of where they
// are declared.
@Composable
internal fun MapSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onOpenDocument: (SettingsDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showGoogleKeyDialog by remember { mutableStateOf(false) }
    var showGoogleMapIdDialog by remember { mutableStateOf(false) }
    var showTileHostDialog by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        // Selecting Google Maps persists the backend switch immediately (sticky
        // selection; a missing key does not revert to OSM). When the key is still
        // blank, selecting also opens its entry dialog so the user can supply one
        // right away — MapSection owns this interception because the dialog lives
        // here.
        ChoiceRow(
            title = stringResource(R.string.settings_map_backend),
            options =
                listOf(
                    MapBackend.OSM to stringResource(R.string.settings_map_backend_osm),
                    MapBackend.GOOGLEMAPS to stringResource(R.string.settings_map_backend_googlemaps),
                ),
            selected = uiState.mapBackend,
            onSelect = { backend ->
                onAction(SettingsAction.SetMapBackend(backend))
                if (backend == MapBackend.GOOGLEMAPS && uiState.googleMapsApiKey.isBlank()) {
                    showGoogleKeyDialog = true
                }
            },
        )
        AnimatedVisibility(visible = uiState.mapBackend == MapBackend.GOOGLEMAPS) {
            Column {
                ChoiceRow(
                    title = stringResource(R.string.settings_google_maps_rendering),
                    options =
                        listOf(
                            GoogleMapsRendering.AUTO to
                                stringResource(R.string.settings_google_maps_rendering_auto),
                            GoogleMapsRendering.RASTER to
                                stringResource(R.string.settings_google_maps_rendering_raster),
                            GoogleMapsRendering.VECTOR to
                                stringResource(R.string.settings_google_maps_rendering_vector),
                        ),
                    selected = uiState.googleMapsRendering,
                    onSelect = { onAction(SettingsAction.SetGoogleMapsRendering(it)) },
                )
                Text(
                    text = stringResource(R.string.settings_google_maps_rendering_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
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
                // The key hint says in-vehicle use is subject to these terms; the
                // row makes them readable before a billing account is attached,
                // rather than leaving the reader to search for them.
                ActionRow(
                    title = stringResource(R.string.settings_google_maps_terms),
                    summary = stringResource(R.string.settings_google_maps_terms_summary),
                    onClick = { onOpenDocument(SettingsDocument.GOOGLE_MAPS_PLATFORM_TERMS) },
                )
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
                // The keyless default is a volunteer service with no availability
                // commitment; a self-hosted mirror must be reachable without a new
                // build, so the host is a setting rather than only a build field.
                SettingRow(
                    title = stringResource(R.string.settings_map_tile_host),
                    summary = mapTileHostSummary(uiState.mapTileHost),
                    modifier = Modifier.clickable { showTileHostDialog = true },
                ) {
                    TrailingIcon(Lucide.ChevronRight)
                }
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
    if (showTileHostDialog) {
        var draft by remember { mutableStateOf(uiState.mapTileHost) }
        AlertDialog(
            onDismissRequest = { showTileHostDialog = false },
            title = { Text(stringResource(R.string.settings_map_tile_host)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_map_tile_host_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        isError = draft.isNotBlank() && !isTileHostUrl(draft),
                        label = { Text(stringResource(R.string.settings_map_tile_host)) },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = isTileHostUrl(draft),
                    onClick = {
                        onAction(SettingsAction.SetMapTileHost(draft))
                        showTileHostDialog = false
                    },
                    modifier = Modifier.heightIn(min = FemtoDimens.MinTouchTarget),
                ) { Text(stringResource(R.string.settings_map_tile_host_save)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onAction(SettingsAction.ClearMapTileHost)
                        showTileHostDialog = false
                    },
                    modifier = Modifier.heightIn(min = FemtoDimens.MinTouchTarget),
                ) { Text(stringResource(R.string.settings_map_tile_host_clear)) }
            },
        )
    }
}

// The override is shown verbatim; blank names the build's default host so the
// user can see what "default" resolves to.
@Composable
private fun mapTileHostSummary(host: String): String =
    host.ifBlank { stringResource(R.string.settings_map_tile_host_unset, BuildConfig.MAP_TILE_HOST) }

// Masks all but the last four characters of the key so it is not fully visible
// on a shared in-car screen, while still letting the user confirm which key is set.
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
