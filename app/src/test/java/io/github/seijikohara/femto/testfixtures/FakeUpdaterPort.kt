package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.update.UpdateState
import io.github.seijikohara.femto.ui.settings.UpdaterPort
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [UpdaterPort]: a test stages any [UpdateState] (and the
 * "updated to" notice) by writing [state] / [updatedTo], and reads back how
 * often each action reached the updater. Acknowledging clears [updatedTo],
 * as the repository does.
 */
internal class FakeUpdaterPort(
    initial: UpdateState = UpdateState.Idle(lastAttemptAt = null),
) : UpdaterPort {
    override val state = MutableStateFlow(initial)

    override val updatedTo = MutableStateFlow<String?>(null)

    var checks = 0
        private set

    var downloads = 0
        private set

    var installs = 0
        private set

    override fun checkNow() {
        checks++
    }

    override fun download() {
        downloads++
    }

    override fun install() {
        installs++
    }

    var discards = 0
        private set

    var skips = 0
        private set

    override fun discard() {
        discards++
    }

    override fun skip() {
        skips++
    }

    override fun acknowledgeUpdatedTo() {
        updatedTo.value = null
    }
}
