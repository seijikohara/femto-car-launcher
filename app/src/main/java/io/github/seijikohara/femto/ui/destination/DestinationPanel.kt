package io.github.seijikohara.femto.ui.destination

import android.Manifest
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Route
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.common.hasRecordAudioPermission
import io.github.seijikohara.femto.data.location.VehicleMotion
import io.github.seijikohara.femto.data.places.PlaceTarget
import io.github.seijikohara.femto.data.places.ReceivedPlace
import io.github.seijikohara.femto.data.places.SavedPlace
import io.github.seijikohara.femto.data.places.capPlaceName
import io.github.seijikohara.femto.data.routing.RouteConditions
import io.github.seijikohara.femto.data.voice.VoiceState
import io.github.seijikohara.femto.ui.common.fileSize
import io.github.seijikohara.femto.ui.home.components.GlassConfig
import io.github.seijikohara.femto.ui.home.components.MaximizePanel
import io.github.seijikohara.femto.ui.home.components.PanelIconButton
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.FemtoLinearProgressIndicator
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.PreviewTextStress
import io.github.seijikohara.femto.ui.theme.drawerBody
import io.github.seijikohara.femto.ui.theme.eyebrow

internal const val DESTINATION_QUERY_TEST_TAG = "destination-query"
internal const val DESTINATION_RENAME_TEST_TAG = "destination-rename"

/**
 * The destination panel (issue #389 phase 1), opened by the dock's Navigation
 * button: one glass maximize panel in the calendar / weather family. The
 * header link opens the maps app exactly as the Navigation button used to
 * ([onOpenMaps]); the body sets a destination and hands it to whichever app
 * handles `geo:` ([onNavigate]).
 *
 * Text entry, saving, renaming and deleting are off while moving ([DestinationUiState.typingAllowed]):
 * while moving the field disables, the save, rename and delete controls go, an open rename field closes
 * without saving, and a line says typing waits for a stop. The mic and the saved places work in every motion state,
 * so a driver can still set a destination without typing.
 *
 * With no app to take a destination ([DestinationUiState.handOffAvailable]),
 * Open in maps app, the keyboard's Go key and the saved places hand off
 * nothing, and the same line says why; typing and saving still work for later.
 * Open in maps app carries the external-link icon of the header's link, since
 * it too leaves the launcher.
 *
 * Where routing is available ([DestinationUiState.routeAvailable]), a Show
 * route action sits under Open in maps app and on each saved place that can
 * be routed to ([DestinationUiState.canRoute]); it works in every motion
 * state, like a tap on a saved place. Two chips under it pick the roads the
 * next route avoids ([RouteConditionChips]). A route starts from [lastFix],
 * which the ViewModel checks for age; Save current location keeps its point
 * as it is. While a route is
 * under way or has failed, [RouteStatus] takes the panel's body over. Closing
 * the panel stops the route under way; a configuration change, after which
 * the panel comes straight back, does not. [visible] says whether the host
 * still wants the panel shown: it turns false as the panel starts to close,
 * while the exit animation still draws it.
 *
 * A landscape panel puts the entry beside the saved places; a portrait panel
 * stacks them, the entry above, matching how [io.github.seijikohara.femto.ui.home.components.CalendarPanel]
 * reflows. Pure UI: [DestinationPanelHost] owns the ViewModel.
 */
@Composable
internal fun DestinationPanel(
    uiState: DestinationUiState,
    lastFix: LastFix?,
    currentAddress: String,
    visible: Boolean,
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
    // Closing the panel (or the panel leaving composition any other way)
    // cancels a mic still listening: nothing on screen would show the
    // microphone open, and a late result must not refill the next open.
    // A route under way stops only when the panel closes: nothing would show it
    // running, and its result would land after the driver gave up on it. A
    // configuration change (a rotation) disposes the panel too, but the panel
    // comes straight back on the same ViewModel, which keeps the download or
    // the search running and shows it again; stopping it there would drop the
    // driver back at the entry with no word of why.
    //
    // The effect lives only while the host wants the panel shown, so it ends
    // as the panel starts to close, not when the exit animation lets go of it:
    // a rotation during that animation disposes the panel as a configuration
    // change would, though the panel, already closed, does not come back, and
    // the route would run on unseen and close the next open on its result.
    val activity = LocalActivity.current
    val currentOnAction by rememberUpdatedState(onAction)
    val currentRoute by rememberUpdatedState(uiState.route)
    val currentPickedPoint by rememberUpdatedState(uiState.pickedPoint)
    val currentRenaming by rememberUpdatedState(uiState.renaming)
    val currentReceivedUnreadable by rememberUpdatedState(uiState.receivedUnreadable)
    //
    // A point picked on the map goes with the panel too, so the next open from
    // the dock starts on the search, and so does a name half typed: the next open
    // shows no field the driver forgot. The line that a received place could not
    // be read goes as well: it speaks of that share alone.
    if (visible) {
        DisposableEffect(Unit) {
            onDispose {
                currentOnAction(DestinationAction.CancelListening)
                val closing = activity?.isChangingConfigurations != true
                if (closing && currentRoute != RouteStep.Idle) currentOnAction(DestinationAction.DismissRoute)
                if (closing && currentPickedPoint != null) currentOnAction(DestinationAction.ClearPickedPoint)
                if (closing && currentRenaming != null) currentOnAction(DestinationAction.CancelRename)
                if (closing && currentReceivedUnreadable) currentOnAction(DestinationAction.ClearReceivedUnreadable)
            }
        }
    }
    // Every hand-off cancels recognition first: the launch can pause the
    // activity before the panel's exit animation disposes it.
    val handOff: (PlaceTarget, String) -> Unit = { target, label ->
        onAction(DestinationAction.CancelListening)
        onNavigate(target, label)
    }
    val route: (PlaceTarget) -> Unit = { target ->
        onAction(DestinationAction.CancelListening)
        onAction(DestinationAction.RouteTo(target, lastFix))
    }
    // Read off the BoxWithConstraints receiver here: inside the layouts below
    // the outer receiver is out of reach (see CalendarPanel).
    val portrait = maxHeight > maxWidth
    val entry: @Composable (Modifier) -> Unit = { entryModifier ->
        uiState.pickedPoint?.let { point ->
            PickedPointEntry(
                point = point,
                uiState = uiState,
                onAction = onAction,
                onNavigate = handOff,
                onRoute = route,
                modifier = entryModifier,
            )
        } ?: DestinationEntry(
            uiState = uiState,
            lastFix = lastFix,
            currentAddress = currentAddress,
            onAction = onAction,
            onMicTap = onMicTap,
            onNavigate = handOff,
            onRoute = route,
            modifier = entryModifier,
        )
    }
    val places: @Composable (Modifier) -> Unit = { placesModifier ->
        SavedPlaces(
            places = uiState.places,
            renaming = uiState.renaming,
            typingAllowed = uiState.typingAllowed,
            handOffAvailable = uiState.handOffAvailable,
            canRoute = uiState::canRoute,
            onNavigate = handOff,
            onRoute = route,
            onAction = onAction,
            modifier = placesModifier,
        )
    }
    // On Done the host closes the panel, and the entry comes back for the exit
    // fade rather than a status with nothing left to say.
    if (uiState.route != RouteStep.Idle && uiState.route != RouteStep.Done) {
        RouteStatus(step = uiState.route, minHeight = maxHeight, onAction = onAction, modifier = Modifier.fillMaxSize())
    } else if (portrait) {
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

// The query field with its mic, the motion or voice line, Open in maps app
// (with Show route under it where text can be routed to), and the two save
// buttons.
@Composable
private fun DestinationEntry(
    uiState: DestinationUiState,
    lastFix: LastFix?,
    currentAddress: String,
    onAction: (DestinationAction) -> Unit,
    onMicTap: () -> Unit,
    onNavigate: (target: PlaceTarget, label: String) -> Unit,
    onRoute: (PlaceTarget) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap),
) {
    val query = uiState.query.trim()
    val canNavigate = query.isNotEmpty() && uiState.handOffAvailable
    val navigate = { if (canNavigate) onNavigate(PlaceTarget.Query(query), "") }
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
    StatusLine(
        voice = uiState.voice,
        typingAllowed = uiState.typingAllowed,
        handOffAvailable = uiState.handOffAvailable,
        receivedUnreadable = uiState.receivedUnreadable,
    )
    val queryTarget = PlaceTarget.Query(query)
    // One action to a row: side by side, half a row cuts "Open in maps app" at
    // the body text floor on a head unit and on a phone.
    Button(
        onClick = navigate,
        enabled = canNavigate,
        modifier = Modifier.fillMaxWidth().heightIn(min = FemtoDimens.MinTouchTarget),
    ) {
        ButtonLabel(icon = Lucide.ExternalLink, text = stringResource(R.string.destination_navigate))
    }
    // Needs no app to hand off to: the route is drawn on the launcher's own map.
    if (uiState.canRoute(queryTarget)) {
        FilledTonalButton(
            onClick = { onRoute(queryTarget) },
            enabled = query.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().heightIn(min = FemtoDimens.MinTouchTarget),
        ) {
            ButtonLabel(icon = Lucide.Route, text = stringResource(R.string.destination_route))
        }
    }
    // Shown wherever routing is, not only under Show route: without a geocoder the
    // saved points still route, under these conditions.
    if (uiState.routeAvailable) RouteConditionChips(conditions = uiState.conditions, onAction = onAction)
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
                lastFix?.point?.let { onAction(DestinationAction.SaveCurrentLocation(it, currentAddress)) }
            },
            enabled = uiState.typingAllowed && lastFix != null,
            modifier = Modifier.weight(2f).heightIn(min = FemtoDimens.MinTouchTarget),
        ) {
            Text(text = stringResource(R.string.destination_save_location))
        }
    }
}

// A point picked with a long press on the map, or received from another app, in
// place of the text entry: its name if it came with one, its coordinates, Open
// in maps app (the point's `geo:` hand-off, naming the pin) with Show route under
// it, and the way to keep it or to go back to the search. Nothing here types, so
// only saving waits for a stop, as every save does.
@Composable
private fun PickedPointEntry(
    point: PlaceTarget.Point,
    uiState: DestinationUiState,
    onAction: (DestinationAction) -> Unit,
    onNavigate: (target: PlaceTarget, label: String) -> Unit,
    onRoute: (PlaceTarget) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap),
) {
    Text(
        text = stringResource(R.string.destination_picked_point).uppercase(),
        style = MaterialTheme.typography.eyebrow(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        modifier = Modifier.heightIn(min = FemtoDimens.MinTouchTarget),
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
                text = uiState.pickedLabel.ifEmpty { point.coordinateLabel() },
                style = MaterialTheme.typography.drawerBody(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // A named point keeps its coordinates in view, so the driver can tell where the name points. They sit
            // under the name inside the row, as a saved place's detail does: a line of their own below the row would
            // push the last row of actions past the bottom of the panel on the head-unit geometry.
            if (uiState.pickedLabel.isNotEmpty()) {
                Text(
                    text = point.coordinateLabel(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    if (!uiState.handOffAvailable) {
        Text(
            text = stringResource(R.string.destination_no_handler),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // One action to a row, as in the search entry.
    Button(
        onClick = { onNavigate(point, uiState.pickedLabel) },
        enabled = uiState.handOffAvailable,
        modifier = Modifier.fillMaxWidth().heightIn(min = FemtoDimens.MinTouchTarget),
    ) {
        ButtonLabel(icon = Lucide.ExternalLink, text = stringResource(R.string.destination_navigate))
    }
    if (uiState.canRoute(point)) {
        FilledTonalButton(
            onClick = { onRoute(point) },
            modifier = Modifier.fillMaxWidth().heightIn(min = FemtoDimens.MinTouchTarget),
        ) {
            ButtonLabel(icon = Lucide.Route, text = stringResource(R.string.destination_route))
        }
        RouteConditionChips(conditions = uiState.conditions, onAction = onAction)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap),
    ) {
        FilledTonalButton(
            onClick = { onAction(DestinationAction.SavePickedPoint) },
            enabled = uiState.typingAllowed,
            modifier = Modifier.weight(1f).heightIn(min = FemtoDimens.MinTouchTarget),
        ) {
            Text(text = stringResource(R.string.destination_save_point))
        }
        FilledTonalButton(
            onClick = { onAction(DestinationAction.ClearPickedPoint) },
            modifier = Modifier.weight(1f).heightIn(min = FemtoDimens.MinTouchTarget),
        ) {
            ButtonLabel(icon = Lucide.Search, text = stringResource(R.string.destination_search_instead))
        }
    }
}

// The roads the next route avoids, as two chips under the route action. A tap
// types nothing, so the chips work in every motion state, as a saved place
// does; the choice is kept for later routes and their reroutes.
@Composable
private fun RouteConditionChips(
    conditions: RouteConditions,
    onAction: (DestinationAction) -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap),
) {
    ConditionChip(
        label = stringResource(R.string.destination_avoid_motorways),
        selected = conditions.avoidMotorways,
        onClick = { onAction(DestinationAction.SetAvoidMotorways(!conditions.avoidMotorways)) },
        modifier = Modifier.weight(1f),
    )
    ConditionChip(
        label = stringResource(R.string.destination_avoid_tolls),
        selected = conditions.avoidTolls,
        onClick = { onAction(DestinationAction.SetAvoidTolls(!conditions.avoidTolls)) },
        modifier = Modifier.weight(1f),
    )
}

@Composable
private fun ConditionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = FilterChip(
    selected = selected,
    onClick = onClick,
    label = { Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    // The chip's own height is below the automotive tap floor (AGENTS.md#automotive-overrides).
    modifier = modifier.heightIn(min = FemtoDimens.MinTouchTarget),
    leadingIcon =
        if (selected) {
            { FemtoIcon(imageVector = Lucide.Check, contentDescription = null) }
        } else {
            null
        },
)

// One line under the field: the voice step (live transcript or failure), else
// a received place that could not be read, else the missing hand-off app, else
// the motion gate's explanation while moving; nothing when idle. The unreadable
// place answers what the driver just did in another app, so it comes first. The
// missing app outranks the motion line, whose promise that saved places work now
// would not hold.
@Composable
private fun StatusLine(
    voice: VoiceState,
    typingAllowed: Boolean,
    handOffAvailable: Boolean,
    receivedUnreadable: Boolean,
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

            receivedUnreadable -> {
                stringResource(R.string.destination_received_unreadable)
            }

            !handOffAvailable -> {
                stringResource(R.string.destination_no_handler)
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
// place off in any motion state while an app can take it; a trailing Show route
// shows on each place that can be routed to, in any motion state; the
// trailing rename and delete show only while stopped, and the row being renamed
// is a name field in place of the place.
@Composable
private fun SavedPlaces(
    places: List<SavedPlace>,
    renaming: RenameDraft?,
    typingAllowed: Boolean,
    handOffAvailable: Boolean,
    canRoute: (PlaceTarget) -> Boolean,
    onNavigate: (target: PlaceTarget, label: String) -> Unit,
    onRoute: (PlaceTarget) -> Unit,
    onAction: (DestinationAction) -> Unit,
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
                    renameText = renaming?.takeIf { it.id == place.id }?.text,
                    editable = typingAllowed,
                    navigable = handOffAvailable,
                    routable = canRoute(place.target),
                    // The title is the pin's label for a point: the name the driver gave, else what it was saved as.
                    onNavigate = { onNavigate(place.target, place.title) },
                    onRoute = { onRoute(place.target) },
                    onRenameStart = { onAction(DestinationAction.StartRename(place.id)) },
                    onRenameChange = { onAction(DestinationAction.RenameChanged(it)) },
                    onRenameSave = { onAction(DestinationAction.SaveRename) },
                    onRenameCancel = { onAction(DestinationAction.CancelRename) },
                    onDelete = { onAction(DestinationAction.DeletePlace(place.id)) },
                )
            }
        }
    }
}

// The narrowest text column a saved place's row keeps beside its buttons. Below it the buttons move to a line of
// their own, so a place can still be told from its neighbours: with route, rename and delete (64 dp each) the text
// column is 142 dp at 800x480 and Display size MEDIUM, which stays on one line, and 42 dp at LARGE (600x360 dp
// effective), where the title would shrink to a letter.
private val SavedPlaceTextMinWidth = 100.dp

// What the clickable part of the row spends besides the text: the pin icon, the gap after it and the side padding.
private val SavedPlaceTextChrome = FemtoDimens.InlineIconSize + FemtoDimens.CardSectionGap * 3

// The narrowest name field a rename row keeps beside its Save and Cancel buttons; below it the buttons drop under
// the field. The field is 246 dp at 800x480 and MEDIUM and 146 dp at LARGE, which is the one that drops.
private val RenameFieldMinWidth = 160.dp

// The title line is the place's own name, else what it was saved as; the line under it says what it was saved as
// when a name replaces it. The trailing 64 dp buttons (route, rename, delete) take what they need and the text gets
// the rest, which ellipsizes; where that rest would be too narrow the buttons go to a second line (see
// SavedPlaceDisplayRow).
@Composable
private fun SavedPlaceRow(
    place: SavedPlace,
    renameText: String?,
    editable: Boolean,
    navigable: Boolean,
    routable: Boolean,
    onNavigate: () -> Unit,
    onRoute: () -> Unit,
    onRenameStart: () -> Unit,
    onRenameChange: (String) -> Unit,
    onRenameSave: () -> Unit,
    onRenameCancel: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) = if (renameText != null) {
    SavedPlaceRenameRow(
        text = renameText,
        storedName = place.name,
        savedAs = place.label,
        onTextChange = onRenameChange,
        onSave = onRenameSave,
        onCancel = onRenameCancel,
        modifier = modifier,
    )
} else {
    SavedPlaceDisplayRow(
        place = place,
        editable = editable,
        navigable = navigable,
        routable = routable,
        onNavigate = onNavigate,
        onRoute = onRoute,
        onRenameStart = onRenameStart,
        onDelete = onDelete,
        modifier = modifier,
    )
}

// The row turned into a field: Done on the keyboard saves, the two buttons save or give up. An unnamed place starts
// with an empty field whose placeholder says what the place was saved as (clearing the field later returns the row
// to that); a named place starts with its name selected, so typing replaces it and a tap places the cursor. The text
// sits in a local field value for the selection; the ViewModel's draft is the one that is saved, and the same cap
// cuts both. The field value is saved across the row leaving composition (scrolled out of the list, or the activity
// recreated), and only an untouched draft (still the stored name) gets the select-all: a rebuilt draft that holds
// what the driver typed keeps the cursor at its end, so the next key does not replace it. The field has no floating
// label: M3 would draw it in bodySmall, below the body floor. Its purpose is the placeholder's, as the search
// field's is; a content description would be announced in place of the typed text.
@Composable
private fun SavedPlaceRenameRow(
    text: String,
    storedName: String?,
    savedAs: String,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) = BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    // Beside the field the two buttons take 128 dp; under it the field gets the whole row.
    val stacked = maxWidth - FemtoDimens.MinTouchTarget * 2 < RenameFieldMinWidth
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        val untouched = text == storedName.orEmpty()
        mutableStateOf(
            TextFieldValue(
                text = text,
                selection = if (untouched) TextRange(0, text.length) else TextRange(text.length),
            ),
        )
    }
    val focusRequester = remember { FocusRequester() }
    // The keyboard comes up with the field: a rename is typing, and the tap on the pencil was the request. The key
    // brings it back when a change of layout rebuilds the field.
    LaunchedEffect(stacked) { focusRequester.requestFocus() }
    val nameField: @Composable (Modifier) -> Unit = { fieldModifier ->
        OutlinedTextField(
            value = field,
            onValueChange = { value ->
                val capped = capPlaceName(value.text)
                field = if (capped == value.text) value else TextFieldValue(capped, TextRange(capped.length))
                onTextChange(capped)
            },
            modifier =
                fieldModifier
                    .heightIn(min = FemtoDimens.MinTouchTarget)
                    .focusRequester(focusRequester)
                    .testTag(DESTINATION_RENAME_TEST_TAG),
            textStyle = MaterialTheme.typography.drawerBody(),
            placeholder = {
                Text(
                    text = stringResource(R.string.destination_rename_placeholder, savedAs),
                    style = MaterialTheme.typography.drawerBody(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave() }),
        )
    }
    val buttons: @Composable () -> Unit = {
        PanelIconButton(
            icon = Lucide.Check,
            description = stringResource(R.string.destination_rename_save),
            onClick = onSave,
            tint = MaterialTheme.colorScheme.primary,
        )
        PanelIconButton(
            icon = Lucide.X,
            description = stringResource(R.string.destination_rename_cancel),
            onClick = onCancel,
        )
    }
    if (stacked) {
        Column(modifier = Modifier.fillMaxWidth()) {
            nameField(Modifier.fillMaxWidth())
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { buttons() }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            nameField(Modifier.weight(1f))
            buttons()
        }
    }
}

// Side by side: the place, then its buttons. When the buttons would leave the text column narrower than
// SavedPlaceTextMinWidth they sit under the place instead, end-aligned, and the text takes the whole width.
@Composable
private fun SavedPlaceDisplayRow(
    place: SavedPlace,
    editable: Boolean,
    navigable: Boolean,
    routable: Boolean,
    onNavigate: () -> Unit,
    onRoute: () -> Unit,
    onRenameStart: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) = BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    val buttonCount = (if (routable) 1 else 0) + (if (editable) 2 else 0)
    val textWidth = maxWidth - FemtoDimens.MinTouchTarget * buttonCount - SavedPlaceTextChrome
    val stacked = buttonCount > 0 && textWidth < SavedPlaceTextMinWidth
    val info: @Composable (Modifier) -> Unit = { infoModifier ->
        SavedPlaceInfo(place = place, navigable = navigable, onNavigate = onNavigate, modifier = infoModifier)
    }
    val buttons: @Composable () -> Unit = {
        if (routable) {
            PanelIconButton(
                icon = Lucide.Route,
                description = stringResource(R.string.destination_route_place, place.title),
                onClick = onRoute,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (editable) {
            PanelIconButton(
                icon = Lucide.Pencil,
                description = stringResource(R.string.destination_rename_place, place.title),
                onClick = onRenameStart,
            )
            PanelIconButton(
                icon = Lucide.Trash2,
                description = stringResource(R.string.destination_delete_place, place.title),
                onClick = onDelete,
            )
        }
    }
    if (stacked) {
        Column(modifier = Modifier.fillMaxWidth()) {
            info(Modifier.fillMaxWidth())
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { buttons() }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            info(Modifier.weight(1f))
            buttons()
        }
    }
}

// The pin, the title and the line under it: the part of the row a tap hands off.
@Composable
private fun SavedPlaceInfo(
    place: SavedPlace,
    navigable: Boolean,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier =
        modifier
            .heightIn(min = FemtoDimens.MinTouchTarget)
            .clip(MaterialTheme.shapes.medium)
            // The row reads as the place's name; the label says the tap leaves the launcher.
            .clickable(
                enabled = navigable,
                onClickLabel = stringResource(R.string.destination_open_place, place.title),
                onClick = onNavigate,
            ).alpha(if (navigable) 1f else FemtoDimens.DisabledContentAlpha)
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
            text = place.title,
            style = MaterialTheme.typography.drawerBody(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        place.detail?.let { detail ->
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RowScope.ButtonLabel(
    icon: ImageVector,
    text: String,
) {
    FemtoIcon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(FemtoDimens.InlineIconSize),
    )
    Text(
        text = text,
        modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// Caps the status column on a wide panel, so a sentence stays a readable line
// length rather than running across a head unit's whole width.
private val RouteStatusMaxWidth = 560.dp

/**
 * The route under way, or why it did not come about: one sentence, a progress
 * bar while something runs, and the buttons that step on. Each failure keeps
 * its own sentence; a version mismatch offers the data again. Centred in a
 * column at least [minHeight] tall (the panel body's), which scrolls when a
 * large font makes it taller.
 */
@Composable
private fun RouteStatus(
    step: RouteStep,
    minHeight: Dp,
    onAction: (DestinationAction) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier.verticalScroll(rememberScrollState()).heightIn(min = minHeight),
    verticalArrangement = Arrangement.spacedBy(FemtoDimens.ScreenPadding, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    // The cap first: a fill applied before it would pin the width to the panel's.
    val widthModifier = Modifier.widthIn(max = RouteStatusMaxWidth).fillMaxWidth()
    Text(
        text = routeStepMessage(step),
        style = MaterialTheme.typography.drawerBody(),
        textAlign = TextAlign.Center,
        modifier = widthModifier,
    )
    when (step) {
        is RouteStep.Downloading -> {
            FemtoLinearProgressIndicator(
                progress = { if (step.totalBytes > 0) step.receivedBytes.toFloat() / step.totalBytes else 0f },
                modifier = widthModifier,
            )
        }

        RouteStep.FindingPosition, RouteStep.Locating, RouteStep.CheckingData, RouteStep.Computing -> {
            FemtoLinearProgressIndicator(modifier = widthModifier)
        }

        else -> {}
    }
    Row(
        modifier = widthModifier,
        horizontalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap),
    ) {
        val buttonModifier = Modifier.weight(1f).heightIn(min = FemtoDimens.MinTouchTarget)
        val dismiss = { onAction(DestinationAction.DismissRoute) }
        when (step) {
            // An update never blocks the route: the stored data routes at once.
            is RouteStep.ConfirmDownload if step.kind == RouteDownload.NEWER -> {
                FilledTonalButton(onClick = { onAction(DestinationAction.UseStoredData) }, modifier = buttonModifier) {
                    Text(text = stringResource(R.string.destination_route_use_stored))
                }
                Button(onClick = { onAction(DestinationAction.ConfirmDownload) }, modifier = buttonModifier) {
                    Text(text = stringResource(R.string.destination_route_update))
                }
            }

            is RouteStep.ConfirmDownload -> {
                FilledTonalButton(onClick = dismiss, modifier = buttonModifier) {
                    Text(text = stringResource(R.string.destination_route_cancel))
                }
                Button(onClick = { onAction(DestinationAction.ConfirmDownload) }, modifier = buttonModifier) {
                    Text(text = stringResource(R.string.destination_route_download))
                }
            }

            RouteStep.Failed(RouteFailure.DATA_VERSION_MISMATCH) -> {
                FilledTonalButton(onClick = dismiss, modifier = buttonModifier) {
                    Text(text = stringResource(R.string.destination_route_cancel))
                }
                Button(onClick = { onAction(DestinationAction.DownloadAgain) }, modifier = buttonModifier) {
                    Text(text = stringResource(R.string.destination_route_download_again))
                }
            }

            is RouteStep.Failed -> {
                Button(onClick = dismiss, modifier = buttonModifier) {
                    Text(text = stringResource(R.string.destination_route_dismiss))
                }
            }

            else -> {
                FilledTonalButton(onClick = dismiss, modifier = buttonModifier) {
                    Text(text = stringResource(R.string.destination_route_cancel))
                }
            }
        }
    }
}

@Composable
private fun routeStepMessage(step: RouteStep): String =
    when (step) {
        RouteStep.FindingPosition -> {
            stringResource(R.string.destination_route_finding_position)
        }

        RouteStep.Locating -> {
            stringResource(R.string.destination_route_locating)
        }

        RouteStep.CheckingData -> {
            stringResource(R.string.destination_route_checking)
        }

        is RouteStep.ConfirmDownload -> {
            stringResource(
                when (step.kind) {
                    RouteDownload.MISSING -> R.string.destination_route_download_prompt
                    RouteDownload.NEWER -> R.string.destination_route_update_prompt
                    RouteDownload.MISSING_AND_NEWER -> R.string.destination_route_download_and_update_prompt
                },
                fileSize(step.bytes),
            )
        }

        is RouteStep.Downloading -> {
            stringResource(
                R.string.destination_route_downloading,
                fileSize(step.receivedBytes.coerceAtMost(step.totalBytes)),
                fileSize(step.totalBytes),
            )
        }

        RouteStep.Computing -> {
            stringResource(R.string.destination_route_computing)
        }

        is RouteStep.Failed -> {
            stringResource(step.reason.messageRes())
        }

        RouteStep.Idle, RouteStep.Done -> {
            ""
        }
    }

private fun RouteFailure.messageRes(): Int =
    when (this) {
        RouteFailure.NO_FIX -> R.string.destination_route_no_fix
        RouteFailure.NO_LOCATION_PERMISSION -> R.string.destination_route_no_permission
        RouteFailure.PLACE_NOT_FOUND -> R.string.destination_route_failed_place_not_found
        RouteFailure.LOOKUP_FAILED -> R.string.destination_route_failed_lookup
        RouteFailure.NO_ROAD_DATA -> R.string.destination_route_failed_no_road_data
        RouteFailure.NO_SPACE -> R.string.destination_route_failed_no_space
        RouteFailure.TIMED_OUT -> R.string.destination_route_failed_timed_out
        RouteFailure.TIMED_OUT_UNDER_CONDITIONS -> R.string.destination_route_timed_out_conditions
        RouteFailure.DATA_VERSION_MISMATCH -> R.string.destination_route_failed_data_version
        RouteFailure.DOWNLOAD_FAILED -> R.string.destination_route_failed_download
        RouteFailure.NO_ROUTE -> R.string.destination_route_failed
        RouteFailure.NO_ROUTE_UNDER_CONDITIONS -> R.string.destination_route_failed_conditions
    }

/**
 * Binds [DestinationViewModel] to [DestinationPanel] and owns the
 * RECORD_AUDIO request, the same way the assistant sheet does: the mic asks
 * for the permission on its first tap, never at startup, and a denial leaves
 * the typed and saved paths in place. A hand-off (recognition already
 * cancelled by the panel) clears the query and closes the panel, as the apps
 * panel closes on a launch. The host also asks the ViewModel to look for an
 * app that takes the hand-off, on opening and on every return to the launcher,
 * and closes the panel the same way once a route is found. [visible] is the
 * panel's ([DestinationPanel]): the flag that shows and hides this host.
 *
 * [pickedPoint] is a point a long press on the map has just picked: the host
 * hands it to the ViewModel, which keeps it from then on, and reports it consumed
 * through [onConsumePickedPoint], so the same pick is not handed over twice.
 *
 * [receivedPlace] is a place another app sent, still waiting in the inbox: the
 * host hands it to the ViewModel and, in the same step, reports it taken through
 * [onConsumeReceivedPlace], which empties the inbox. The ViewModel outlives a
 * rotation, so a place handed over is never lost to one, and an emptied inbox
 * opens nothing again after a recreation.
 */
@Composable
internal fun DestinationPanelHost(
    lastFix: LastFix?,
    currentAddress: String,
    visible: Boolean,
    onNavigate: (target: PlaceTarget, label: String) -> Unit,
    onOpenMaps: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    pickedPoint: PlaceTarget.Point? = null,
    onConsumePickedPoint: () -> Unit = {},
    receivedPlace: ReceivedPlace? = null,
    onConsumeReceivedPlace: (ReceivedPlace) -> Unit = {},
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
    // An observer added while the activity is resumed receives ON_RESUME at
    // once, so this runs as the panel opens, and again whenever the user comes
    // back from another app, which is where a maps app gets installed.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onAction(DestinationAction.CheckHandOff) }
    // A found route is the dashboard's now: the panel steps back and closes,
    // clearing the query as a hand-off does.
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(uiState.route) {
        if (uiState.route == RouteStep.Done) {
            viewModel.onAction(DestinationAction.DismissRoute)
            viewModel.onAction(DestinationAction.ClearQuery)
            currentOnClose()
        }
    }
    val currentOnConsumePickedPoint by rememberUpdatedState(onConsumePickedPoint)
    LaunchedEffect(pickedPoint) {
        pickedPoint?.let { point ->
            viewModel.onAction(DestinationAction.PickPoint(point))
            currentOnConsumePickedPoint()
        }
    }
    val currentOnConsumeReceivedPlace by rememberUpdatedState(onConsumeReceivedPlace)
    LaunchedEffect(receivedPlace) {
        receivedPlace?.let { place ->
            viewModel.onAction(DestinationAction.Receive(place))
            currentOnConsumeReceivedPlace(place)
        }
    }
    DestinationPanel(
        uiState = uiState,
        lastFix = lastFix,
        currentAddress = currentAddress,
        visible = visible,
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
            lastFix = PreviewFix,
            currentAddress = "1-9-1 Marunouchi",
            visible = true,
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
private fun DestinationPanelNoHandlerPreview() {
    FemtoTheme {
        DestinationPanel(
            uiState = PreviewState.copy(
                motion = VehicleMotion.PARKED,
                query = "Central Station",
                handOffAvailable = false,
            ),
            lastFix = PreviewFix,
            currentAddress = "1-9-1 Marunouchi",
            visible = true,
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
            lastFix = PreviewFix,
            currentAddress = "1-9-1 Marunouchi",
            visible = true,
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
private fun DestinationPanelRoutePreview() {
    FemtoTheme {
        DestinationPanel(
            uiState =
                PreviewState.copy(
                    motion = VehicleMotion.PARKED,
                    query = "Central Station",
                    routeAvailable = true,
                    geocoderAvailable = true,
                ),
            lastFix = PreviewFix,
            currentAddress = "1-9-1 Marunouchi",
            visible = true,
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
@PreviewTextStress
@Composable
private fun DestinationPanelNamedPlacesPreview() {
    FemtoTheme {
        DestinationPanel(
            uiState =
                PreviewState.copy(
                    motion = VehicleMotion.PARKED,
                    routeAvailable = true,
                    places =
                        listOf(
                            SavedPlace(
                                1L,
                                "1st & Pike, Seattle",
                                PlaceTarget.Query("1st & Pike, Seattle"),
                                name = "Work",
                            ),
                            SavedPlace(2L, "Home", PlaceTarget.Point(35.681236, 139.767125)),
                        ),
                ),
            lastFix = PreviewFix,
            currentAddress = "1-9-1 Marunouchi",
            visible = true,
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
@PreviewTextStress
@Composable
private fun DestinationPanelRenamePreview() {
    FemtoTheme {
        DestinationPanel(
            uiState =
                PreviewState.copy(
                    motion = VehicleMotion.PARKED,
                    routeAvailable = true,
                    renaming = RenameDraft(id = 1L, text = "Office"),
                ),
            lastFix = PreviewFix,
            currentAddress = "1-9-1 Marunouchi",
            visible = true,
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
@PreviewTextStress
@Composable
private fun DestinationPanelPickedPointPreview() {
    FemtoTheme {
        DestinationPanel(
            uiState =
                PreviewState.copy(
                    motion = VehicleMotion.PARKED,
                    routeAvailable = true,
                    pickedPoint = PlaceTarget.Point(35.658581, 139.745433),
                ),
            lastFix = PreviewFix,
            currentAddress = "1-9-1 Marunouchi",
            visible = true,
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
@PreviewTextStress
@Composable
private fun DestinationPanelRouteDownloadPreview() {
    FemtoTheme {
        DestinationPanel(
            uiState = PreviewState.copy(routeAvailable = true, route = RouteStep.ConfirmDownload(bytes = 48_200_000L)),
            lastFix = PreviewFix,
            currentAddress = "1-9-1 Marunouchi",
            visible = true,
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
@PreviewTextStress
@Composable
private fun RouteConditionChipsPreview() {
    FemtoTheme {
        // One chip on, one off: both states side by side.
        RouteConditionChips(conditions = RouteConditions(avoidTolls = true), onAction = {})
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

private val PreviewFix = LastFix(PlaceTarget.Point(35.681236, 139.767125), elapsedRealtimeNanos = 0L)
