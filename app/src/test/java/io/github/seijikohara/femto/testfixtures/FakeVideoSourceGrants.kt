package io.github.seijikohara.femto.testfixtures

import io.github.seijikohara.femto.data.video.VideoSourceGrants

/**
 * In-memory [VideoSourceGrants]: [held] is the set of URIs whose read grant
 * the app keeps. [grantable] lists the URIs [take] may persist; a URI outside
 * it models a provider that refuses a persistable grant. [names] backs
 * [displayNameOrNull].
 */
internal class FakeVideoSourceGrants(
    held: Set<String> = emptySet(),
    private val grantable: Set<String>? = null,
    private val names: Map<String, String> = emptyMap(),
) : VideoSourceGrants {
    val held: MutableSet<String> = held.toMutableSet()

    override fun take(uri: String): Boolean = (grantable?.contains(uri) ?: true).also { if (it) held += uri }

    override fun release(uri: String) {
        held -= uri
    }

    override fun holds(uri: String): Boolean = uri in held

    override fun displayNameOrNull(uri: String): String? = names[uri]?.takeIf { uri in held }
}
