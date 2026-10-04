package io.github.seijikohara.femto.ui.home

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.seijikohara.femto.data.display.DockPosition
import io.github.seijikohara.femto.data.display.DockWidth
import io.github.seijikohara.femto.data.display.DriverSide
import io.github.seijikohara.femto.data.display.MotionTier
import io.github.seijikohara.femto.ui.home.components.DockConfig
import io.github.seijikohara.femto.ui.home.components.GlassConfig
import io.github.seijikohara.femto.ui.home.components.MapConfig
import io.github.seijikohara.femto.ui.home.components.PanelVisibility
import io.github.seijikohara.femto.ui.locale.SpeedUnit
import io.github.seijikohara.femto.ui.locale.TemperatureUnit
import io.github.seijikohara.femto.ui.video.VideoAction
import io.github.seijikohara.femto.ui.video.VideoSurface
import io.github.seijikohara.femto.ui.video.VideoViewModel
import io.github.seijikohara.femto.ui.video.VideoViewModelFactory
import io.github.seijikohara.femto.ui.video.rememberVideoPicker

@Composable
internal fun HomeRoute(
    is24Hour: Boolean,
    showClockSeconds: Boolean,
    speedUnit: SpeedUnit,
    temperatureUnit: TemperatureUnit,
    mapConfig: MapConfig,
    panels: PanelVisibility,
    glassConfig: GlassConfig,
    onEvent: (HomeEvent) -> Unit,
    modifier: Modifier = Modifier,
    dockPosition: DockPosition = DockPosition.BOTTOM,
    dockWidth: DockWidth = DockWidth.COMPACT,
    dockConfig: DockConfig = DockConfig(),
    driverSide: DriverSide = DriverSide.RIGHT,
    musicShowAlbum: Boolean = true,
    musicShowArt: Boolean = true,
    motionTier: MotionTier = MotionTier.STANDARD,
    sheetOpen: Boolean = false,
    fullscreen: Boolean = false,
) {
    val context = LocalContext.current
    val viewModel: HomeViewModel =
        viewModel(factory = HomeViewModelFactory(context.applicationContext as Application))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val updatePrompt by viewModel.updatePrompt.collectAsStateWithLifecycle()
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event -> currentOnEvent(event) }
    }
    // The video window's player lives in its own ViewModel, so playback
    // outlives the composition while the launcher is off screen. Building it
    // builds no player: ExoPlayer comes with the first file the window loads.
    val videoViewModel: VideoViewModel =
        viewModel(factory = VideoViewModelFactory(context.applicationContext as Application))
    val video by videoViewModel.uiState.collectAsStateWithLifecycle()
    val videoPictureVisible by videoViewModel.pictureVisible.collectAsStateWithLifecycle()
    val pickVideo = rememberVideoPicker { uri -> videoViewModel.onAction(VideoAction.FilePicked(uri)) }
    val onVideoAction: (VideoAction) -> Unit = { action ->
        if (action == VideoAction.PickFile) pickVideo() else videoViewModel.onAction(action)
    }
    val videoSurface: @Composable (Modifier) -> Unit =
        remember(videoViewModel) {
            { surfaceModifier -> VideoSurface(host = videoViewModel.surfaceHost, modifier = surfaceModifier) }
        }
    HomeScreen(
        uiState = uiState,
        is24Hour = is24Hour,
        showClockSeconds = showClockSeconds,
        speedUnit = speedUnit,
        temperatureUnit = temperatureUnit,
        mapConfig = mapConfig,
        panels = panels,
        glassConfig = glassConfig,
        onAction = viewModel::onAction,
        modifier = modifier,
        dockPosition = dockPosition,
        dockWidth = dockWidth,
        dockConfig = dockConfig,
        driverSide = driverSide,
        musicShowAlbum = musicShowAlbum,
        musicShowArt = musicShowArt,
        spectrum = viewModel.audioSpectrum,
        motionTier = motionTier,
        online = online,
        updatePrompt = updatePrompt,
        sheetOpen = sheetOpen,
        fullscreen = fullscreen,
        video = video,
        videoPictureVisible = videoPictureVisible,
        onVideoAction = onVideoAction,
        videoSurface = videoSurface,
    )
}
