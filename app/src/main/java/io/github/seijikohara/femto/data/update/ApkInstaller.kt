package io.github.seijikohara.femto.data.update

import java.io.File

/**
 * Hands a verified APK to the platform's package installer in two steps:
 * [stage] writes it into a new install session, and [commit] hands that
 * session to the platform. The platform names the session in every status it
 * sends, and the split lets the repository record the session before the
 * first status can arrive, so a status for an older session never settles
 * the current attempt.
 *
 * The platform decides asynchronously. It asks the user to confirm, and a
 * successful install replaces (and first kills) this process. [commit]
 * therefore reports only whether the hand-off worked. The platform's requests
 * and refusals reach [UpdateRepository] through [InstallStatusReceiver]; a
 * success is only visible to the next process start.
 */
internal interface ApkInstaller {
    /**
     * Whether the user lets this app request installs ("Install unknown
     * apps"). Without that grant the platform stops the install at a dialog
     * of its own.
     */
    fun canRequestInstalls(): Boolean

    /** Write [file], already verified, into a new install session; the session's id, or null when the platform could not take it. */
    suspend fun stage(file: File): Int?

    /** Hand staged session [sessionId] to the platform; false when the platform could not take it. */
    suspend fun commit(sessionId: Int): Boolean

    /** Whether the platform still holds session [sessionId], typically while it waits for the user to confirm. */
    suspend fun isPending(sessionId: Int): Boolean

    /** Give up session [sessionId], so the platform drops it and its copy of the APK. */
    suspend fun abandon(sessionId: Int)
}

/** The platform's request that the user confirm an install. */
internal fun interface InstallConfirmation {
    /** Put the system's confirmation on screen; false when the platform could not start it. */
    fun show(): Boolean
}
