package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.update.InstallConfirmation

/** Counts how often the platform's confirmation was put on screen. */
internal class FakeInstallConfirmation : InstallConfirmation {
    var shows = 0
        private set

    override fun show() {
        shows++
    }
}
