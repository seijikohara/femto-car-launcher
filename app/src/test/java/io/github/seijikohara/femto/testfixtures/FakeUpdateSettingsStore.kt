package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.update.UpdateSettings
import io.github.seijikohara.femto.data.update.UpdateSettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [UpdateSettingsStore]: every setter mutates a [MutableStateFlow]
 * synchronously, so a test sees the write with no DataStore IO. Like the real
 * store, [resetToDefaults] restores only the auto-check setting.
 */
internal class FakeUpdateSettingsStore(
    initial: UpdateSettings = UpdateSettings.Default,
) : UpdateSettingsStore {
    private val state = MutableStateFlow(initial)

    override val settings: Flow<UpdateSettings> = state

    /** The persisted values right now, for assertions. */
    val current: UpdateSettings get() = state.value

    override suspend fun setAutoCheck(value: Boolean) = state.update { it.copy(autoCheck = value) }

    override suspend fun setLastCheckAttemptAt(epochMs: Long) = state.update { it.copy(lastCheckAttemptAt = epochMs) }

    override suspend fun setPendingInstallVersionCode(versionCode: Int?) =
        state.update { it.copy(pendingInstallVersionCode = versionCode) }

    override suspend fun resetToDefaults() = state.update { it.copy(autoCheck = UpdateSettings.Default.autoCheck) }
}
