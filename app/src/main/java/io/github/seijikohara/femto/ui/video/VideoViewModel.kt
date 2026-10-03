package io.github.seijikohara.femto.ui.video

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.seijikohara.femto.data.common.WhileUiSubscribed
import io.github.seijikohara.femto.data.common.WhileUiSubscribedFresh
import io.github.seijikohara.femto.data.common.catchAsDefault
import io.github.seijikohara.femto.data.location.LocationGraph
import io.github.seijikohara.femto.data.location.VehicleMotion
import io.github.seijikohara.femto.data.video.ContentResolverVideoSourceGrants
import io.github.seijikohara.femto.data.video.VideoPickRefusal
import io.github.seijikohara.femto.data.video.VideoPreferences
import io.github.seijikohara.femto.data.video.VideoSettings
import io.github.seijikohara.femto.data.video.VideoSettingsStore
import io.github.seijikohara.femto.data.video.VideoSourceGrants
import io.github.seijikohara.femto.data.video.adoptSourceOrRefusal
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
    // The file the store names as followStore found it: none (no file, or the
    // window off), loaded, or unavailable (its read grant gone).
    private val source = MutableStateFlow(VideoFileState.NONE)

    // Counts picks, so picking the file already recorded (after it failed to
    // play, say) still loads it again: the store alone would not change.
    private val picks = MutableStateFlow(0)

    // The last pick here that could not be kept, if any; shown only while the
    // record it was refused against stands (a pick in Settings moves it).
    private val pickRefusal = MutableStateFlow<VideoPickRefusal?>(null)

    // Set by Close until the store reads the window off: the window and its
    // panel go at once instead of waiting for the write, so neither draws a
    // "Pick a video" frame in between. Cleared on the store's own off (see
    // init), so turning the window back on in Settings shows it again. A
    // write the store loses leaves the window hidden until Settings turns it
    // off and on.
    private val closing = MutableStateFlow(false)

    /** Where the UI attaches and detaches the player's surface; nothing more of the player. */
    val surfaceHost: VideoSurfaceHost get() = player

    val uiState: StateFlow<VideoUiState> =
        combine(
            combine(
                store.settings.catchAsDefault(TAG, "video settings", VideoSettings.Default),
                closing,
            ) { settings, closed -> if (closed) settings.copy(windowEnabled = false) else settings },
            source,
            player.isPlaying,
            player.failed,
            pickRefusal,
        ) { settings, found, playing, failed, refusal ->
            VideoUiState(
                windowEnabled = settings.windowEnabled,
                // A loaded file the player failed on cannot be opened either.
                file = if (found == VideoFileState.READY && failed) VideoFileState.UNAVAILABLE else found,
                playing = playing,
                pickFailed = refusal?.stillApplies(settings) == true,
            )
        }.stateIn(viewModelScope, WhileUiSubscribed, VideoUiState.Off)

    /**
     * Whether the picture may show (videoPictureVisibleFlow), apart from
     * [uiState] and shared with [WhileUiSubscribedFresh]: a dashboard that
     * comes back starts from hidden and judges the motion afresh. Held in
     * [uiState], whose grace replays the last value, a launcher returning
     * while the vehicle moves would attach the surface and draw a frame
     * before the gate spoke again. [uiState] keeps its grace, so a rotation
     * does not drop the window state an open panel depends on.
     */
    val pictureVisible: StateFlow<Boolean> =
        videoPictureVisibleFlow(motion, store.settings.map { it.hidePictureWhileDriving })
            .stateIn(viewModelScope, WhileUiSubscribedFresh, false)

    init {
        viewModelScope.launch { followStore() }
        viewModelScope.launch {
            store.settings
                .catchAsDefault(TAG, "video settings", VideoSettings.Default)
                .collect { if (!it.windowEnabled) closing.value = false }
        }
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
                    pickRefusal.value = null
                    val refusal = withContext(ioDispatcher) { store.adoptSourceOrRefusal(action.uri, grants) }
                    if (refusal == null) picks.update { it + 1 } else pickRefusal.value = refusal
                }
            }

            VideoAction.Close -> {
                // Hidden and stopped here at once rather than on the store's
                // write, which may take a moment to land.
                closing.value = true
                player.stop()
                source.value = VideoFileState.NONE
                viewModelScope.launch { store.setWindowEnabled(false) }
            }
        }
    }

    override fun onCleared() = player.release()

    // Load the file the store names while the window is on, and stop the
    // player otherwise. A file whose read grant is gone is not loaded: the
    // window says it cannot be opened and asks for a new one.
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
                source.value =
                    when {
                        playable != null -> VideoFileState.READY
                        uri != null -> VideoFileState.UNAVAILABLE
                        else -> VideoFileState.NONE
                    }
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
