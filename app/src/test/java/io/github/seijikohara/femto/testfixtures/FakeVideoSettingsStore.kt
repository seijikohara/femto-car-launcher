package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.video.VideoSettings
import io.github.seijikohara.femto.data.video.VideoSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [VideoSettingsStore]: every setter mutates a [MutableStateFlow]
 * synchronously, so a test sees the write with no DataStore IO. Like the real
 * store, [resetToDefaults] restores the two switches and keeps the picked file.
 * [dropsSourceWrites] models a store that loses the file record (a full disk,
 * a corrupted file): the write returns, and nothing changes.
 */
internal class FakeVideoSettingsStore(
    initial: VideoSettings = VideoSettings.Default,
    private val dropsSourceWrites: Boolean = false,
) : VideoSettingsStore {
    private val state = MutableStateFlow(initial)

    override val settings: StateFlow<VideoSettings> = state

    /** The persisted values right now, for assertions. */
    val current: VideoSettings get() = state.value

    override suspend fun setWindowEnabled(value: Boolean) = state.update { it.copy(windowEnabled = value) }

    override suspend fun setHidePictureWhileDriving(value: Boolean) =
        state.update { it.copy(hidePictureWhileDriving = value) }

    override suspend fun setSourceUri(value: String?) {
        if (!dropsSourceWrites) state.update { it.copy(sourceUri = value) }
    }

    override suspend fun resetToDefaults() =
        state.update {
            it.copy(
                windowEnabled = VideoSettings.Default.windowEnabled,
                hidePictureWhileDriving = VideoSettings.Default.hidePictureWhileDriving,
            )
        }
}
