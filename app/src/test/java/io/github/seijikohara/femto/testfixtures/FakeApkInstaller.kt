package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.update.ApkInstaller
import java.io.File

/**
 * In-memory [ApkInstaller]. [stage] records the file and opens the next
 * session, counting up from [FIRST_SESSION_ID], unless [stages] is false.
 * [commit] records the session and answers [commits].
 *
 * [pending] holds the sessions the "platform" still keeps: every committed
 * one, until a test removes it the way the platform drops a session it has
 * finished or abandoned. [onCommit] runs inside [commit], so a test can
 * observe what was persisted at the moment the platform takes a session, or
 * deliver a verdict before [commit] returns.
 */
internal class FakeApkInstaller(
    var stages: Boolean = true,
    var commits: Boolean = true,
    var canRequest: Boolean = true,
    private val onCommit: (sessionId: Int) -> Unit = {},
) : ApkInstaller {
    val staged = mutableListOf<File>()
    val committed = mutableListOf<Int>()
    val pending = mutableSetOf<Int>()
    private var nextSessionId = FIRST_SESSION_ID

    override fun canRequestInstalls(): Boolean = canRequest

    override suspend fun stage(file: File): Int? =
        if (stages) {
            staged += file
            nextSessionId++
        } else {
            null
        }

    override suspend fun commit(sessionId: Int): Boolean {
        committed += sessionId
        if (commits) pending += sessionId
        onCommit(sessionId)
        return commits
    }

    override suspend fun isPending(sessionId: Int): Boolean = sessionId in pending

    companion object {
        /** The id of the first session [stage] opens. */
        const val FIRST_SESSION_ID = 1
    }
}
