package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.update.InstallConfirmation

/**
 * Counts how often the platform's confirmation was put on screen. [starts]
 * answers each [show]: false plays a device whose package installer cannot be
 * started.
 */
internal class FakeInstallConfirmation(
    var starts: Boolean = true,
) : InstallConfirmation {
    var shows = 0
        private set

    override fun show(): Boolean {
        shows++
        return starts
    }
}
