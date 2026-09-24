package io.github.seijikohara.femto.data.update

import android.content.pm.PackageInstaller
import io.github.seijikohara.femto.BuildConfig
import io.github.seijikohara.femto.testfixtures.FakeApkBody
import io.github.seijikohara.femto.testfixtures.FakeInstallSessions
import io.github.seijikohara.femto.testfixtures.FakeInstallSessions.Companion.FIRST_SESSION_ID
import io.github.seijikohara.femto.testfixtures.WrittenSession
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PackageInstallerApkInstallerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val sessions = FakeInstallSessions()

    @Test
    fun `a session names this app, the file's size and the OTHER package source`() =
        runTest {
            val apk = apk()

            installer().stage(apk)

            assertEquals(
                listOf(
                    InstallSessionParams(
                        mode = PackageInstaller.SessionParams.MODE_FULL_INSTALL,
                        appPackageName = BuildConfig.APPLICATION_ID,
                        sizeBytes = FakeApkBody.size.toLong(),
                        packageSource = PackageInstaller.PACKAGE_SOURCE_OTHER,
                    ),
                ),
                sessions.created,
            )
        }

    @Test
    fun `stage writes the file into the session it opened`() =
        runTest {
            val apk = apk()

            val sessionId = installer().stage(apk)

            assertEquals(FIRST_SESSION_ID, sessionId)
            assertEquals(FIRST_SESSION_ID, sessions.written.single().sessionId)
            assertEquals(apk, sessions.written.single().source)
        }

    @Test
    fun `stage first abandons the sessions an earlier attempt left open`() =
        runTest {
            val leftovers = FakeInstallSessions(leftovers = listOf(7, 8))

            val sessionId = installer(leftovers).stage(apk())

            // Only the leftovers: the session just opened stays.
            assertEquals(listOf(7, 8), leftovers.abandoned.sorted())
            assertTrue(leftovers.exists(checkNotNull(sessionId)))
        }

    @Test
    fun `stage abandons a session it could not write`() =
        runTest {
            sessions.writeFails = true

            val sessionId = installer().stage(apk())

            assertNull(sessionId)
            assertEquals(listOf(FIRST_SESSION_ID), sessions.abandoned)
        }

    @Test
    fun `stage reports no session when the platform opens none`() =
        runTest {
            sessions.createFails = true

            val sessionId = installer().stage(apk())

            assertNull(sessionId)
            assertEquals(emptyList<WrittenSession>(), sessions.written)
        }

    @Test
    fun `commit hands the session to the platform`() =
        runTest {
            val installer = installer()
            val sessionId = checkNotNull(installer.stage(apk()))

            assertTrue(installer.commit(sessionId))
            assertEquals(listOf(sessionId), sessions.committed)
            assertEquals(emptyList(), sessions.abandoned)
        }

    @Test
    fun `commit abandons a session the platform refused`() =
        runTest {
            val installer = installer()
            val sessionId = checkNotNull(installer.stage(apk()))
            sessions.commitFails = true

            assertFalse(installer.commit(sessionId))
            assertEquals(listOf(sessionId), sessions.abandoned)
        }

    @Test
    fun `isPending follows whether the platform still holds the session`() =
        runTest {
            val installer = installer()
            val sessionId = checkNotNull(installer.stage(apk()))
            assertTrue(installer.isPending(sessionId))

            sessions.abandon(sessionId)

            assertFalse(installer.isPending(sessionId))
        }

    @Test
    fun `isPending reads a session the platform will not look up as gone`() =
        runTest {
            val installer = installer()
            val sessionId = checkNotNull(installer.stage(apk()))
            sessions.lookupFails = true

            assertFalse(installer.isPending(sessionId))
        }

    private fun TestScope.installer(sessions: InstallSessions = this@PackageInstallerApkInstallerTest.sessions) =
        PackageInstallerApkInstaller(sessions, ioDispatcher = StandardTestDispatcher(testScheduler))

    private fun apk(): File = tempFolder.newFile("update.apk").apply { writeBytes(FakeApkBody) }
}
