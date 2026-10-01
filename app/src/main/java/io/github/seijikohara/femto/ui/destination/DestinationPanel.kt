package io.github.seijikohara.femto.ui.destination

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.Navigation
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Trash2
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.common.hasRecordAudioPermission
import io.github.seijikohara.femto.data.location.VehicleMotion
import io.github.seijikohara.femto.data.places.PlaceTarget
import io.github.seijikohara.femto.data.places.SavedPlace
import io.github.seijikohara.femto.data.voice.VoiceState
import io.github.seijikohara.femto.ui.home.components.GlassConfig
import io.github.seijikohara.femto.ui.home.components.MaximizePanel
import io.github.seijikohara.femto.ui.home.components.PanelIconButton
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.PreviewTextStress
import io.github.seijikohara.femto.ui.theme.drawerBody
import io.github.seijikohara.femto.ui.theme.eyebrow

internal const val DESTINATION_QUERY_TEST_TAG = "destination-query"

/**
 * The destination panel (issue #389 phase 1), opened by the dock's Navigation
 * button: one glass maximize panel in the calendar / weather family. The
 * header link opens the maps app exactly as the Navigation button used to
 * ([onOpenMaps]); the body sets a destination and hands it to whichever app
 * handles `geo:` ([onNavigate]).
 *
 * Text entry, saving and deleting are off while moving ([DestinationUiState.typingAllowed]):
 * while moving the field and those controls disable, and a line says typing
 * waits for a stop. The mic and the saved places work in every motion state,
 * so a driver can still set a destination without typing.
 *
 * A landscape panel puts the entry beside the saved places; a portrait panel
 * stacks them, the entry above, matching how [io.github.seijikohara.femto.ui.home.components.CalendarPanel]
 * reflows. Pure UI: [DestinationPanelHost] owns the ViewModel.
 */
@Composable
internal fun DestinationPanel(
    uiState: DestinationUiState,
    currentPoint: PlaceTarget.Point?,
    currentAddress: String,
    onAction: (DestinationAction) -> Unit,
    onMicTap: () -> Unit,
    onNavigate: (target: PlaceTarget, label: String) -> Unit,
    onOpenMaps: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState = rememberHazeState(),
    glassConfig: GlassConfig = GlassConfig(),
) = MaximizePanel(
    title = stringResource(R.string.destination_title),
    onClose = onClose,
    onOpenExternal = onOpenMaps,
    openExternalLabel = stringResource(R.string.destination_open_maps),
    modifier = modifier,
    hazeState = hazeState,
    glassConfig = glassConfig,
) {
    // Closing the panel (or the panel leaving composition any other way) stops
    // a mic still listening: nothing on screen would show the microphone open.
    val currentOnAction by rememberUpdatedState(onAction)
    DisposableEffect(Unit) {
        onDispose { currentOnAction(DestinationAction.StopListening) }
    }
    // Read off the BoxWithConstraints receiver here: inside the layouts below
    // the outer receiver is out of reach (see CalendarPanel).
    val portrait = maxHeight > maxWidth
    val entry: @Composable (Modifier) -> Unit = { entryModifier ->
        DestinationEntry(
            uiState = uiState,
            currentPoint = currentPoint,
            currentAddress = currentAddress,
            onAction = onAction,
            onMicTap = onMicTap,
            onNavigate = onNavigate,
            modifier = entryModifier,
        )
    }
    val places: @Composable (Modifier) -> Unit = { placesModifier ->
        SavedPlaces(
            places = uiState.places,
            typingAllowed = uiState.typingAllowed,
            onNavigate = onNavigate,
            onDelete = { onAction(DestinationAction.DeletePlace(it)) },
            modifier = placesModifier,
        )
    }
    if (portrait) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(FemtoDimens.ScreenPadding),
        ) {
            entry(Modifier.fillMaxWidth())
            places(Modifier.fillMaxWidth().weight(1f))
        }
    } else {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(FemtoDimens.ScreenPadding),
        ) {
            // The entry scrolls on its own: a short landscape panel (800x480)
            // cannot fit the field, the hint and both button rows at once.
            entry(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()))
            places(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

// The query field with its mic, the motion or voice line, Navigate, and the
// two save buttons.
@Composable
private fun DestinationEntry(
    uiState: DestinationUiState,
    currentPoint: PlaceTarget.Point?,
    currentAddress: String,
    onAction: (DestinationAction) -> Unit,
    onMicTap: () -> Unit,
    onNavigate: (target: PlaceTarget, label: String) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap),
) {
    val query = uiState.query.trim()
    val navigate = { if (query.isNotEmpty()) onNavigate(PlaceTarget.Query(query), "") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap),
    ) {
        OutlinedTextField(
            value = uiState.query,
            onValueChange = { onAction(DestinationAction.QueryChanged(it)) },
            enabled = uiState.typingAllowed,
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = FemtoDimens.MinTouchTarget)
                    .testTag(DESTINATION_QUERY_TEST_TAG),
            textStyle = MaterialTheme.typography.drawerBody(),
            placeholder = {
                Text(
                    text = stringResource(R.string.destination_query_hint),
                    style = MaterialTheme.typography.drawerBody(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = { FemtoIcon(imageVector = Lucide.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { navigate() }),
        )
        // Hidden where the device has no speech recognizer: a mic that can
        // never listen is a dead control.
        if (uiState.voice != VoiceState.Unavailable) {
            MicButton(listening = uiState.voice is VoiceState.Listening, onClick = onMicTap)
        }
    }
    StatusLine(voice = uiState.voice, typingAllowed = uiState.typingAllowed)
    Button(
        onClick = navigate,
        enabled = query.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().heightIn(min = FemtoDimens.MinTouchTarget),
    ) {
        FemtoIcon(
            imageVector = Lucide.Navigation,
            contentDescription = null,
            modifier = Modifier.size(FemtoDimens.InlineIconSize),
        )
        Text(
            text = stringResource(R.string.destination_navigate),
            modifier = Modifier.padding(start = 10.dp),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap),
    ) {
        FilledTonalButton(
            onClick = { onAction(DestinationAction.SaveQuery) },
            enabled = uiState.typingAllowed && query.isNotEmpty(),
            modifier = Modifier.weight(1f).heightIn(min = FemtoDimens.MinTouchTarget),
        ) {
            Text(text = stringResource(R.string.destination_save_query))
        }
        FilledTonalButton(
            onClick = {
                currentPoint?.let { onAction(DestinationAction.SaveCurrentLocation(it, currentAddress)) }
            },
            enabled = uiState.typingAllowed && currentPoint != null,
            modifier = Modifier.weight(2f).heightIn(min = FemtoDimens.MinTouchTarget),
        ) {
            Text(text = stringResource(R.string.destination_save_location))
        }
    }
}

// One line under the field: the motion gate's explanation while moving, else
// the voice step (live transcript or failure); nothing when idle.
@Composable
private fun StatusLine(
    voice: VoiceState,
    typingAllowed: Boolean,
    modifier: Modifier = Modifier,
) {
    val text =
        when {
            voice is VoiceState.Listening -> {
                listOf(stringResource(R.string.assistant_voice_listening), voice.partial)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
            }

            voice is VoiceState.Failed -> {
                stringResource(voice.messageRes)
            }

            !typingAllowed -> {
                stringResource(R.string.destination_moving_hint)
            }

            else -> {
                null
            }
        }
    text?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
    }
}

@Composable
private fun MicButton(
    listening: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = PanelIconButton(
    icon = Lucide.Mic,
    description = stringResource(R.string.assistant_voice_mic),
    onClick = onClick,
    modifier =
        modifier
            .clip(CircleShape)
            .background(
                if (listening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
            ),
    tint = if (listening) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
)

// The saved places in insertion order (newest last). A row tap hands the
// place off in any motion state; the trailing delete shows only while stopped.
@Composable
private fun SavedPlaces(
    places: List<SavedPlace>,
    typingAllowed: Boolean,
    onNavigate: (target: PlaceTarget, label: String) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap),
) {
    Text(
        text = stringResource(R.string.destination_saved_places).uppercase(),
        style = MaterialTheme.typography.eyebrow(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (places.isEmpty()) {
        Text(
            text = stringResource(R.string.destination_no_saved_places),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(places, key = { it.id }) { place ->
                SavedPlaceRow(
                    place = place,
                    showDelete = typingAllowed,
                    onNavigate = { onNavigate(place.target, place.label) },
                    onDelete = { onDelete(place.id) },
                )
            }
        }
    }
}

@Composable
private fun SavedPlaceRow(
    place: SavedPlace,
    showDelete: Boolean,
    onNavigate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
) {
    Row(
        modifier =
            Modifier
                .weight(1f)
                .heightIn(min = FemtoDimens.MinTouchTarget)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onNavigate)
                .padding(horizontal = FemtoDimens.CardSectionGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap),
    ) {
        FemtoIcon(
            imageVector = Lucide.MapPin,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(FemtoDimens.InlineIconSize),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = place.label,
                style = MaterialTheme.typography.drawerBody(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // A query place whose label differs from its query shows the
            // query, so "Office" still says where it goes.
            (place.target as? PlaceTarget.Query)?.text?.takeIf { it != place.label }?.let { query ->
                Text(
                    text = query,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    if (showDelete) {
        PanelIconButton(
            icon = Lucide.Trash2,
            description = stringResource(R.string.destination_delete_place, place.label),
            onClick = onDelete,
        )
    }
}

/**
 * Binds [DestinationViewModel] to [DestinationPanel] and owns the
 * RECORD_AUDIO request, the same way the assistant sheet does: the mic asks
 * for the permission on its first tap, never at startup, and a denial leaves
 * the typed and saved paths in place. A hand-off clears the query and closes
 * the panel, as the apps panel closes on a launch.
 */
@Composable
internal fun DestinationPanelHost(
    currentPoint: PlaceTarget.Point?,
    currentAddress: String,
    onNavigate: (target: PlaceTarget, label: String) -> Unit,
    onOpenMaps: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState = rememberHazeState(),
    glassConfig: GlassConfig = GlassConfig(),
) {
    val context = LocalContext.current
    val viewModel: DestinationViewModel = viewModel(factory = DestinationViewModelFactory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.onAction(DestinationAction.StartListening)
        }
    DestinationPanel(
        uiState = uiState,
        currentPoint = currentPoint,
        currentAddress = currentAddress,
        onAction = viewModel::onAction,
        onMicTap = {
            when {
                uiState.voice is VoiceState.Listening -> viewModel.onAction(DestinationAction.StopListening)
                context.hasRecordAudioPermission() -> viewModel.onAction(DestinationAction.StartListening)
                else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        onNavigate = { target, label ->
            onNavigate(target, label)
            viewModel.onAction(DestinationAction.ClearQuery)
            onClose()
        },
        onOpenMaps = onOpenMaps,
        onClose = onClose,
        modifier = modifier,
        hazeState = hazeState,
        glassConfig = glassConfig,
    )
}

@PreviewLightDark
@PreviewTextStress
@Composable
private fun DestinationPanelStoppedPreview() {
    FemtoTheme {
        DestinationPanel(
            uiState = PreviewState.copy(motion = VehicleMotion.PARKED, query = "Central Station"),
            currentPoint = PlaceTarget.Point(35.681236, 139.767125),
            currentAddress = "1-9-1 Marunouchi",
            onAction = {},
            onMicTap = {},
            onNavigate = { _, _ -> },
            onOpenMaps = {},
            onClose = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@PreviewLightDark
@Composable
private fun DestinationPanelMovingPreview() {
    FemtoTheme {
        DestinationPanel(
            uiState = PreviewState,
            currentPoint = PlaceTarget.Point(35.681236, 139.767125),
            currentAddress = "1-9-1 Marunouchi",
            onAction = {},
            onMicTap = {},
            onNavigate = { _, _ -> },
            onOpenMaps = {},
            onClose = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private val PreviewState =
    DestinationUiState(
        query = "",
        voice = VoiceState.Idle,
        places =
            listOf(
                SavedPlace(1L, "Office", PlaceTarget.Query("1st & Pike, Seattle")),
                SavedPlace(2L, "Home", PlaceTarget.Point(35.681236, 139.767125)),
            ),
        motion = VehicleMotion.MOVING,
    )
