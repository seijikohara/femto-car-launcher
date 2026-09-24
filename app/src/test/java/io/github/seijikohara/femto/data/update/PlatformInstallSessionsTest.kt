package io.github.seijikohara.femto.data.update

import android.app.Application
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.testfixtures.FakeApkBody
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [PlatformInstallSessions] against Robolectric's package installer, which
 * keeps what a session was opened with and, like the platform, refuses a
 * commit while a stream into the session is still open.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlatformInstallSessionsTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val sessions = PlatformInstallSessions(app)

    @Test
    fun `a session carries the package name and the package source it was opened with`() {
        val sessionId = sessions.create(PARAMS)

        val info = checkNotNull(app.packageManager.packageInstaller.getSessionInfo(sessionId))
        assertEquals(PARAMS.appPackageName, info.appPackageName)
        assertEquals(PARAMS.packageSource, info.packageSource)
    }

    @Test
    fun `a written session can be committed`() {
        val sessionId = sessions.create(PARAMS)
        sessions.write(sessionId, "base.apk", apk())

        // Throws if write() left a stream into the session open.
        sessions.commit(sessionId)
    }

    @Test
    fun `mine lists the sessions this app opened`() {
        val first = sessions.create(PARAMS)
        val second = sessions.create(PARAMS)

        assertEquals(setOf(first, second), sessions.mine().toSet())
    }

    @Test
    fun `an abandoned session no longer exists`() {
        val sessionId = sessions.create(PARAMS)
        assertTrue(sessions.exists(sessionId))

        sessions.abandon(sessionId)

        assertFalse(sessions.exists(sessionId))
    }

    @Test
    fun `the app requests the permission the platform installer requires`() {
        val info =
            app.packageManager.getPackageInfo(
                app.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
            )

        assertTrue("android.permission.REQUEST_INSTALL_PACKAGES" in info.requestedPermissions.orEmpty())
    }

    private fun apk(): File = tempFolder.newFile("update.apk").apply { writeBytes(FakeApkBody) }

    private companion object {
        val PARAMS =
            InstallSessionParams(
                mode = PackageInstaller.SessionParams.MODE_FULL_INSTALL,
                appPackageName = "io.example.app",
                sizeBytes = FakeApkBody.size.toLong(),
                packageSource = PackageInstaller.PACKAGE_SOURCE_OTHER,
            )
    }
}
