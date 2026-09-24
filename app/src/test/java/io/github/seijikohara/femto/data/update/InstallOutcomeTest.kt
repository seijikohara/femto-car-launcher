package io.github.seijikohara.femto.data.update

import android.content.pm.PackageInstaller
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals

/**
 * Mapping table for [installOutcomeOf]: every status [PackageInstaller]
 * documents, plus one a later platform may add, which must still land on a
 * failure the UI can phrase.
 */
@RunWith(Parameterized::class)
internal class InstallOutcomeTest(
    private val status: Int,
    private val expected: InstallOutcome,
) {
    @Test
    fun `maps the platform status to its outcome`() {
        assertEquals(expected, installOutcomeOf(status))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "status {0} -> {1}")
        fun cases(): List<Array<Any>> =
            listOf(
                arrayOf(PackageInstaller.STATUS_PENDING_USER_ACTION, InstallOutcome.NeedsConfirmation),
                arrayOf(PackageInstaller.STATUS_SUCCESS, InstallOutcome.Installed),
                arrayOf(PackageInstaller.STATUS_FAILURE_ABORTED, InstallOutcome.Declined),
                arrayOf(
                    PackageInstaller.STATUS_FAILURE_CONFLICT,
                    InstallOutcome.Refused(UpdateFailure.INSTALL_CONFLICT),
                ),
                arrayOf(PackageInstaller.STATUS_FAILURE_BLOCKED, InstallOutcome.Refused(UpdateFailure.INSTALL_BLOCKED)),
                arrayOf(PackageInstaller.STATUS_FAILURE_STORAGE, InstallOutcome.Refused(UpdateFailure.STORAGE)),
                arrayOf(PackageInstaller.STATUS_FAILURE, InstallOutcome.Refused(UpdateFailure.OTHER)),
                arrayOf(PackageInstaller.STATUS_FAILURE_INVALID, InstallOutcome.Refused(UpdateFailure.OTHER)),
                arrayOf(PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, InstallOutcome.Refused(UpdateFailure.OTHER)),
                arrayOf(PackageInstaller.STATUS_FAILURE_TIMEOUT, InstallOutcome.Refused(UpdateFailure.OTHER)),
                arrayOf(Int.MAX_VALUE, InstallOutcome.Refused(UpdateFailure.OTHER)),
            )
    }
}
