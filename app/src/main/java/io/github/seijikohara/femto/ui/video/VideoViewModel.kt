package io.github.seijikohara.femto.ui.video

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.seijikohara.femto.data.common.WhileUiSubscribed
import io.github.seijikohara.femto.data.common.catchAsDefault
import io.github.seijikohara.femto.data.location.LocationGraph
import io.github.seijikohara.femto.data.location.VehicleMotion
import io.github.seijikohara.femto.data.video.ContentResolverVideoSourceGrants
import io.github.seijikohara.femto.data.video.VideoPreferences
import io.github.seijikohara.femto.data.video.VideoSettings
import io.github.seijikohara.femto.data.video.VideoSettingsStore
import io.github.seijikohara.femto.data.video.VideoSourceGrants
import io.github.seijikohara.femto.data.video.adoptSource
import io.github.seijikohara.femto.data.video.videoPictureVisibleFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "VideoViewModel"

/**
 * Owns the dashboard's video player (issue #390). The player lives here, not
 * in composition, so the audio keeps playing while the launcher is off
 * screen: the store drives what is loaded ([followStore]) for as long as the
 * ViewModel lives, whether or not anything collects [uiState]. The surface is
 * the UI's to attach and detach through [surfaceHost].
 */
internal class VideoViewModel(
    private val store: VideoSettingsStore,
    private val grants: VideoSourceGrants,
    motion: Flow<VehicleMotion>,
    private val player: VideoPlayer,
    // The grants cross into the document's provider, a Binder call that can
    // block; tests pass their own dispatcher.
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    // Whether the file the store names is loaded: false with no file, with the
    // window off, or with the read grant gone.
    private val loaded = MutableStateFlow(false)

    // Counts picks, so picking the file already recorded (after it failed to
    // play, say) still loads it again: the store alone would not change.
    private val picks = MutableStateFlow(0)

    /** Where the UI attaches and detaches the player's surface. */
    val surfaceHost: VideoPlayer get() = player

    val uiState: StateFlow<VideoUiState> =
        combine(
            store.settings.catchAsDefault(TAG, "video settings", VideoSettings.Default),
            videoPictureVisibleFlow(motion, store.settings.map { it.hidePictureWhileDriving }),
            loaded,
            player.isPlaying,
            player.failed,
        ) { settings, pictureVisible, isLoaded, playing, failed ->
            VideoUiState(
                windowEnabled = settings.windowEnabled,
                fileReady = isLoaded && !failed,
                pictureVisible = pictureVisible,
                playing = playing,
            )
        }.stateIn(viewModelScope, WhileUiSubscribed, VideoUiState.Off)

    init {
        viewModelScope.launch { followStore() }
    }

    fun onAction(action: VideoAction) {
        when (action) {
            VideoAction.TogglePlayback -> {
                if (player.isPlaying.value) player.pause() else player.play()
            }

            VideoAction.PickFile -> {
                // The host launches the picker; nothing to do here.
            }

            is VideoAction.FilePicked -> {
                viewModelScope.launch {
                    if (withContext(ioDispatcher) { store.adoptSource(action.uri, grants) }) picks.update { it + 1 }
                }
            }

            VideoAction.Close -> {
                // Stopped here at once rather than on the store's write, which
                // may take a moment to land.
                player.stop()
                loaded.value = false
                viewModelScope.launch { store.setWindowEnabled(false) }
            }
        }
    }

    override fun onCleared() = player.release()

    // Load the file the store names while the window is on, and stop the
    // player otherwise. A file whose read grant is gone is not loaded: the
    // window asks for a new one instead.
    private suspend fun followStore() =
        combine(
            store.settings
                .catchAsDefault(TAG, "video settings", VideoSettings.Default)
                .map { settings -> settings.sourceUri?.takeIf { settings.windowEnabled } }
                .distinctUntilChanged(),
            picks,
        ) { uri, _ -> uri }
            .collectLatest { uri ->
                val playable = uri?.takeIf { withContext(ioDispatcher) { grants.holds(it) } }
                if (playable == null) player.stop() else player.load(playable)
                loaded.value = playable != null
            }
}

internal class VideoViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras,
    ): T {
        @Suppress("UNCHECKED_CAST")
        return VideoViewModel(
            store = VideoPreferences(application),
            grants = ContentResolverVideoSourceGrants(application),
            // The dashboard's own location pipeline: one GPS registration
            // shared with every other motion gate.
            motion = LocationGraph.get(application).vehicleMotion(),
            player = ExoVideoPlayer(application),
        ) as T
    }
}
