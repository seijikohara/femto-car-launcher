package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.update.ApkInstaller
import io.github.seijikohara.femto.data.update.UpdateManifest
import java.io.File

/** One [FakeApkInstaller.install] call. */
internal data class HandedOffApk(
    val file: File,
    val manifest: UpdateManifest,
)

/**
 * Records every hand-off; [accepts] decides whether the platform takes the
 * file. [onInstall] runs inside the call, so a test can observe what was
 * already persisted at the moment the platform would commit.
 */
internal class FakeApkInstaller(
    var accepts: Boolean = true,
    private val onInstall: () -> Unit = {},
) : ApkInstaller {
    val installs = mutableListOf<HandedOffApk>()

    override suspend fun install(
        file: File,
        manifest: UpdateManifest,
    ): Boolean {
        installs += HandedOffApk(file, manifest)
        onInstall()
        return accepts
    }
}
