package io.github.seijikohara.femto.data.update

import java.io.File

/**
 * Hands a verified APK to the platform's package installer. The platform
 * decides asynchronously — it asks the user to confirm, and a successful
 * install replaces (and first kills) this process — so [install] reports only
 * whether the hand-off worked. A refusal or a dismissed confirmation reaches
 * [UpdateRepository.onInstallFailed] / [UpdateRepository.onInstallCancelled];
 * a success is only visible to the next process start.
 */
internal fun interface ApkInstaller {
    /** Commit [file], already verified against [manifest]; false when the platform could not take it. */
    suspend fun install(
        file: File,
        manifest: UpdateManifest,
    ): Boolean
}
