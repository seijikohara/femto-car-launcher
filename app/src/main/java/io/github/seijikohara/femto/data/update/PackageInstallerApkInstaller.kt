package io.github.seijikohara.femto.data.update

import android.content.Context
import android.content.pm.PackageInstaller
import android.util.Log
import io.github.seijikohara.femto.BuildConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "PackageInstallerApkInstaller"

// The APK's name inside a session. The platform needs it unique within the
// session only.
private const val SESSION_APK_NAME = "base.apk"

/** What a new install session asks of the platform: the fields of a [PackageInstaller.SessionParams]. */
internal data class InstallSessionParams(
    val mode: Int,
    val appPackageName: String,
    val sizeBytes: Long,
    val packageSource: Int,
)

/**
 * The platform's install-session calls that [PackageInstallerApkInstaller]
 * makes; a seam for JVM tests. Each call may throw whatever the platform
 * throws.
 */
internal interface InstallSessions {
    fun canRequestInstalls(): Boolean

    /** The ids of the sessions this app created that the platform still holds. */
    fun mine(): List<Int>

    /** Open a new session; its id. */
    fun create(params: InstallSessionParams): Int

    /** Write [source] into session [sessionId] as [name]; every stream is closed on return. */
    fun write(
        sessionId: Int,
        name: String,
        source: File,
    )

    /** Hand session [sessionId] to the platform, which reports to [InstallStatusReceiver]. */
    fun commit(sessionId: Int)

    fun abandon(sessionId: Int)

    fun exists(sessionId: Int): Boolean
}

/**
 * [ApkInstaller] over the platform's install sessions. Every failure is
 * logged and reported as such, never thrown. A session this installer could
 * not hand over is abandoned, so the platform does not keep its copy of the
 * APK (tens of megabytes) until it expires the session days later.
 */
internal class PackageInstallerApkInstaller(
    private val sessions: InstallSessions,
    // The platform fails an install whose APK names another package: a free
    // guard against the other channel's APK.
    private val appPackageName: String = BuildConfig.APPLICATION_ID,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ApkInstaller {
    override fun canRequestInstalls(): Boolean = sessions.canRequestInstalls()

    override suspend fun stage(file: File): Int? =
        withContext(ioDispatcher) {
            abandonLeftovers()
            runCatching { sessions.create(paramsFor(file)) }
                .onFailure { Log.w(TAG, "opening an install session failed", it) }
                .getOrNull()
                ?.takeIf { sessionId -> writeInto(sessionId, file) }
        }

    override suspend fun commit(sessionId: Int): Boolean =
        withContext(ioDispatcher) { runOrAbandon(sessionId, "committing") { sessions.commit(sessionId) } }

    override suspend fun isPending(sessionId: Int): Boolean =
        withContext(ioDispatcher) {
            runCatching { sessions.exists(sessionId) }
                .onFailure { Log.w(TAG, "looking up session $sessionId failed", it) }
                .getOrDefault(false)
        }

    override suspend fun abandon(sessionId: Int) = withContext(ioDispatcher) { abandonSession(sessionId) }

    // PACKAGE_SOURCE_OTHER, never LOCAL_FILE or DOWNLOADED_FILE: on Android 13
    // either of those re-applies the "restricted settings" guard on every
    // install, updates included, and notification-listener access (the music
    // card's source) is such a setting. The size lets the platform check for
    // space before it copies anything.
    private fun paramsFor(file: File) =
        InstallSessionParams(
            mode = PackageInstaller.SessionParams.MODE_FULL_INSTALL,
            appPackageName = appPackageName,
            sizeBytes = file.length(),
            packageSource = PackageInstaller.PACKAGE_SOURCE_OTHER,
        )

    // A session an earlier attempt left open (its confirmation dismissed and
    // that process gone since) keeps a copy of the APK until the platform
    // expires it, days later. Each attempt clears them first; the statuses they
    // send name their own sessions, which the repository ignores.
    private fun abandonLeftovers() =
        runCatching { sessions.mine() }
            .onFailure { Log.w(TAG, "listing this app's install sessions failed", it) }
            .getOrDefault(emptyList())
            .forEach(::abandonSession)

    private fun writeInto(
        sessionId: Int,
        file: File,
    ): Boolean = runOrAbandon(sessionId, "writing the APK") { sessions.write(sessionId, SESSION_APK_NAME, file) }

    // False when [block] fails, and the session is abandoned then.
    private inline fun runOrAbandon(
        sessionId: Int,
        step: String,
        block: () -> Unit,
    ): Boolean =
        runCatching(block)
            .onFailure {
                Log.w(TAG, "$step failed for session $sessionId", it)
                abandonSession(sessionId)
            }.isSuccess

    private fun abandonSession(sessionId: Int) {
        runCatching { sessions.abandon(sessionId) }
            .onFailure { Log.w(TAG, "abandoning session $sessionId failed", it) }
    }
}

/** Real [InstallSessions], backed by the platform's [PackageInstaller]. */
internal class PlatformInstallSessions(
    private val context: Context,
) : InstallSessions {
    private val installer: PackageInstaller get() = context.packageManager.packageInstaller

    override fun canRequestInstalls(): Boolean = context.packageManager.canRequestPackageInstalls()

    override fun mine(): List<Int> = installer.mySessions.map { it.sessionId }

    override fun create(params: InstallSessionParams): Int =
        installer.createSession(
            PackageInstaller.SessionParams(params.mode).apply {
                setAppPackageName(params.appPackageName)
                setSize(params.sizeBytes)
                setPackageSource(params.packageSource)
            },
        )

    // Every stream is closed before this returns: commit() throws a
    // SecurityException while one is still open.
    override fun write(
        sessionId: Int,
        name: String,
        source: File,
    ) = installer.openSession(sessionId).use { session ->
        source.inputStream().use { input ->
            session.openWrite(name, 0, source.length()).use { output ->
                input.copyTo(output)
                session.fsync(output)
            }
        }
    }

    override fun commit(sessionId: Int) =
        installer.openSession(sessionId).use { session ->
            session.commit(InstallStatusReceiver.pendingIntent(context, sessionId).intentSender)
        }

    override fun abandon(sessionId: Int) = installer.abandonSession(sessionId)

    override fun exists(sessionId: Int): Boolean = installer.getSessionInfo(sessionId) != null
}
