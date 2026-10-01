package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.update.UpdateManifest
import io.github.seijikohara.femto.data.update.UpdateSettings
import io.github.seijikohara.femto.data.update.UpdateSettingsStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update

/**
 * In-memory [UpdateSettingsStore]: every setter mutates a [MutableStateFlow]
 * synchronously, so a test sees the write with no DataStore IO. Like the real
 * store, [resetToDefaults] restores only the auto-check setting, and
 * [recordPrompted] never lowers the record.
 *
 * [dropsAttemptWrites] models a store that loses the attempt record (a full
 * disk, a corrupted file). [gateReads] makes each read take its snapshot and
 * then wait, so two readers can be held on the same stale snapshot.
 * [gatePromptedWrites] holds each [recordPrompted] write until released, the
 * way a DataStore write takes a while, so a test can act while one is under
 * way.
 */
internal class FakeUpdateSettingsStore(
    initial: UpdateSettings = UpdateSettings.Default,
    private val dropsAttemptWrites: Boolean = false,
) : UpdateSettingsStore {
    private val state = MutableStateFlow(initial)
    private var readGate: CompletableDeferred<Unit>? = null
    private var promptedWriteGate: CompletableDeferred<Unit>? = null

    override val settings: Flow<UpdateSettings> =
        flow {
            val snapshot = state.value
            readGate?.await()
            emit(snapshot)
            // Later changes for a reader that keeps collecting; the first value
            // may repeat the snapshot.
            emitAll(state)
        }

    /** The persisted values right now, for assertions. */
    val current: UpdateSettings get() = state.value

    fun gateReads(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { readGate = it }

    fun gatePromptedWrites(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { promptedWriteGate = it }

    override suspend fun setAutoCheck(value: Boolean) = state.update { it.copy(autoCheck = value) }

    override suspend fun setLastCheckAttemptAt(epochMs: Long) {
        if (!dropsAttemptWrites) state.update { it.copy(lastCheckAttemptAt = epochMs) }
    }

    override suspend fun setPendingInstallVersionCode(versionCode: Int?) =
        state.update { it.copy(pendingInstallVersionCode = versionCode) }

    override suspend fun setOffer(manifest: UpdateManifest?) = state.update { it.copy(offer = manifest) }

    override suspend fun recordPrompted(versionCode: Int) {
        promptedWriteGate?.await()
        state.update { it.copy(promptedVersionCode = maxOf(versionCode, it.promptedVersionCode ?: versionCode)) }
    }

    override suspend fun setSkippedVersionCode(versionCode: Int?) =
        state.update { it.copy(skippedVersionCode = versionCode) }

    override suspend fun clearSkipBelow(versionCode: Int) =
        state.update { settings ->
            settings.copy(skippedVersionCode = settings.skippedVersionCode?.takeUnless { it < versionCode })
        }

    override suspend fun setRefusedVersionCode(versionCode: Int?) =
        state.update { it.copy(refusedVersionCode = versionCode) }

    override suspend fun resetToDefaults() = state.update { it.copy(autoCheck = UpdateSettings.Default.autoCheck) }
}
