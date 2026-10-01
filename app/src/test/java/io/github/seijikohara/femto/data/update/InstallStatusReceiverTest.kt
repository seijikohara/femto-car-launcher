package io.github.seijikohara.femto.data.update

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.testfixtures.FakeInstallVerdicts
import io.github.seijikohara.femto.testfixtures.ReceivedVerdict
import io.github.seijikohara.femto.testfixtures.receive
import io.github.seijikohara.femto.testfixtures.receiveAndAwaitFinish
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InstallStatusReceiverTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val verdicts = FakeInstallVerdicts()

    @Test
    fun `a confirmation request passes the platform's intent on, to show in a new task`() {
        val request = Intent(CONFIRM_ACTION).setPackage(INSTALLER_PACKAGE)

        deliver(PackageInstaller.STATUS_PENDING_USER_ACTION) { putExtra(Intent.EXTRA_INTENT, request) }

        val requested = assertIs<ReceivedVerdict.ConfirmationRequested>(verdicts.received.single())
        assertEquals(SESSION, requested.sessionId)
        assertTrue(requested.confirmation.show())
        val started = shadowOf(app).nextStartedActivity
        assertEquals(CONFIRM_ACTION, started.action)
        assertEquals(INSTALLER_PACKAGE, started.`package`)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `a confirmation the platform cannot start reports that it did not`() {
        // A locked-down ROM with its package installer disabled: nothing resolves the request.
        shadowOf(app).checkActivities(true)
        deliver(PackageInstaller.STATUS_PENDING_USER_ACTION) {
            putExtra(Intent.EXTRA_INTENT, Intent(CONFIRM_ACTION).setPackage(INSTALLER_PACKAGE))
        }

        val requested = assertIs<ReceivedVerdict.ConfirmationRequested>(verdicts.received.single())
        assertFalse(requested.confirmation.show())
    }

    @Test
    fun `a confirmation request without the platform's intent fails the install`() {
        deliver(PackageInstaller.STATUS_PENDING_USER_ACTION)

        assertEquals(listOf<ReceivedVerdict>(ReceivedVerdict.Failed(SESSION, UpdateFailure.OTHER)), verdicts.received)
    }

    @Test
    fun `an aborted install is reported as cancelled`() {
        deliver(PackageInstaller.STATUS_FAILURE_ABORTED)

        assertEquals(listOf<ReceivedVerdict>(ReceivedVerdict.Cancelled(SESSION)), verdicts.received)
    }

    @Test
    fun `a refusal is reported with its reason`() {
        deliver(PackageInstaller.STATUS_FAILURE_CONFLICT)

        assertEquals(
            listOf<ReceivedVerdict>(ReceivedVerdict.Failed(SESSION, UpdateFailure.INSTALL_CONFLICT)),
            verdicts.received,
        )
    }

    @Test
    fun `a success reports nothing`() {
        deliver(PackageInstaller.STATUS_SUCCESS)

        assertEquals(emptyList<ReceivedVerdict>(), verdicts.received)
    }

    @Test
    fun `the platform's status message is logged`() {
        deliver(PackageInstaller.STATUS_FAILURE_CONFLICT) {
            putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, STATUS_MESSAGE)
        }

        assertTrue(ShadowLog.getLogsForTag("InstallStatusReceiver").any { STATUS_MESSAGE in it.msg })
    }

    @Test
    fun `each session reports to this receiver through a mutable broadcast of its own`() {
        val pendingIntent = shadowOf(InstallStatusReceiver.pendingIntent(app, SESSION))

        assertTrue(pendingIntent.isBroadcast)
        assertFalse(pendingIntent.isImmutable)
        assertEquals(ComponentName(app, InstallStatusReceiver::class.java), pendingIntent.savedIntent.component)
        assertEquals(SESSION, pendingIntent.requestCode)
    }

    @Test
    fun `the receiver is not exported`() {
        val info =
            app.packageManager.getReceiverInfo(
                ComponentName(app, InstallStatusReceiver::class.java),
                PackageManager.ComponentInfoFlags.of(0),
            )

        assertFalse(info.exported)
    }

    @Test
    fun `delivery holds on until the work the verdict started is done`() =
        runTest {
            // The verdict's records are still being written: the receiver must
            // keep its broadcast until they land.
            val work = Job()
            val delivery =
                launch {
                    deliverInstallStatus(
                        app,
                        statusIntent(PackageInstaller.STATUS_FAILURE_ABORTED),
                        FakeInstallVerdicts(work),
                    )
                }
            runCurrent()
            assertFalse(delivery.isCompleted)

            work.complete()
            runCurrent()

            assertTrue(delivery.isCompleted)
        }

    @Test
    fun `the receiver finishes its broadcast once the status is delivered`() {
        // Returns only once the receiver has called finish(); a receiver that
        // never does fails here.
        InstallStatusReceiver { verdicts }.receiveAndAwaitFinish(
            app,
            statusIntent(PackageInstaller.STATUS_FAILURE_ABORTED),
        )

        assertEquals(listOf<ReceivedVerdict>(ReceivedVerdict.Cancelled(SESSION)), verdicts.received)
    }

    @Test
    fun `the receiver holds its broadcast until the verdict's work is done`() {
        val work = Job()
        val receiver = InstallStatusReceiver { FakeInstallVerdicts(work) }

        val finished = receiver.receive(app, statusIntent(PackageInstaller.STATUS_FAILURE_ABORTED))
        assertFalse(finished.isDone)

        work.complete()
        finished.get(30, TimeUnit.SECONDS)
    }

    @Test
    fun `an abort the developer verification caused fails as such, not as a decline`() {
        assertEquals(
            InstallOutcome.Refused(UpdateFailure.DEVELOPER_VERIFICATION_BLOCKED),
            installOutcomeOf(
                PackageInstaller.STATUS_FAILURE_ABORTED,
                developerVerificationFailure = UpdateFailure.DEVELOPER_VERIFICATION_BLOCKED,
            ),
        )
    }

    @Test
    fun `a verification that needed a connection fails as offline`() {
        assertEquals(
            UpdateFailure.DEVELOPER_VERIFICATION_OFFLINE,
            developerVerificationFailureOf(PackageInstaller.DEVELOPER_VERIFICATION_FAILED_REASON_NETWORK_UNAVAILABLE),
        )
    }

    @Test
    fun `a developer the verifier blocked, or an unknown reason, fails as blocked`() {
        // A reason a later release adds is no connection problem either.
        listOf(
            PackageInstaller.DEVELOPER_VERIFICATION_FAILED_REASON_DEVELOPER_BLOCKED,
            PackageInstaller.DEVELOPER_VERIFICATION_FAILED_REASON_UNKNOWN,
            Int.MAX_VALUE,
        ).forEach { reason ->
            assertEquals(
                UpdateFailure.DEVELOPER_VERIFICATION_BLOCKED,
                developerVerificationFailureOf(reason),
                "reason $reason",
            )
        }
    }

    @Test
    fun `an abort reads as a decline on a release without developer verification`() {
        // Android 13 has no developer verification, so whatever extra rides
        // along, an abort is the user's answer.
        deliver(PackageInstaller.STATUS_FAILURE_ABORTED) {
            putExtra(PackageInstaller.EXTRA_DEVELOPER_VERIFICATION_FAILURE_REASON, DEVELOPER_BLOCKED)
        }

        assertEquals(listOf<ReceivedVerdict>(ReceivedVerdict.Cancelled(SESSION)), verdicts.received)
    }

    private fun deliver(
        status: Int,
        extras: Intent.() -> Unit = {},
    ) = runTest { deliverInstallStatus(app, statusIntent(status, extras), verdicts) }

    private fun statusIntent(
        status: Int,
        extras: Intent.() -> Unit = {},
    ): Intent =
        Intent()
            .putExtra(PackageInstaller.EXTRA_SESSION_ID, SESSION)
            .putExtra(PackageInstaller.EXTRA_STATUS, status)
            .apply(extras)

    private companion object {
        const val SESSION = 42
        const val CONFIRM_ACTION = "android.content.pm.action.CONFIRM_INSTALL"
        const val INSTALLER_PACKAGE = "com.android.packageinstaller"
        const val STATUS_MESSAGE = "INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match"
        const val DEVELOPER_BLOCKED = PackageInstaller.DEVELOPER_VERIFICATION_FAILED_REASON_DEVELOPER_BLOCKED
    }
}
