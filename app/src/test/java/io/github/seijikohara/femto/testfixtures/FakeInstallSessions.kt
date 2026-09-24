package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.update.InstallSessionParams
import io.github.seijikohara.femto.data.update.InstallSessions
import java.io.File
import java.io.IOException

/** One [FakeInstallSessions.write] call. */
internal data class WrittenSession(
    val sessionId: Int,
    val name: String,
    val source: File,
)

/**
 * In-memory [InstallSessions]. [create] opens the next session, counting up
 * from [FIRST_SESSION_ID]. The "platform" holds every session it opened, plus
 * the [leftovers] an earlier attempt left open, until [abandon] drops one. The
 * `…Fails` flags make the matching call throw the way the platform does.
 */
internal class FakeInstallSessions(
    leftovers: Collection<Int> = emptyList(),
) : InstallSessions {
    val created = mutableListOf<InstallSessionParams>()
    val written = mutableListOf<WrittenSession>()
    val committed = mutableListOf<Int>()
    val abandoned = mutableListOf<Int>()
    var createFails = false
    var writeFails = false
    var commitFails = false
    var lookupFails = false
    private val held = leftovers.toMutableSet()
    private var nextSessionId = FIRST_SESSION_ID

    override fun canRequestInstalls(): Boolean = true

    override fun mine(): List<Int> = held.toList()

    override fun create(params: InstallSessionParams): Int {
        if (createFails) throw IOException("the platform opened no session")
        created += params
        return nextSessionId++.also { held += it }
    }

    override fun write(
        sessionId: Int,
        name: String,
        source: File,
    ) {
        if (writeFails) throw IOException("the session could not be written")
        written += WrittenSession(sessionId, name, source)
    }

    override fun commit(sessionId: Int) {
        if (commitFails) throw SecurityException("the platform refused the commit")
        committed += sessionId
    }

    override fun abandon(sessionId: Int) {
        abandoned += sessionId
        held -= sessionId
    }

    override fun exists(sessionId: Int): Boolean {
        if (lookupFails) throw SecurityException("the platform refused the lookup")
        return sessionId in held
    }

    companion object {
        /** The id of the first session [create] opens; apart from any leftover id a test picks. */
        const val FIRST_SESSION_ID = 100
    }
}
