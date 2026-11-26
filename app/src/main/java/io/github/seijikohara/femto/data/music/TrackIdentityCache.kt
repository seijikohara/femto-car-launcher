package io.github.seijikohara.femto.data.music

/**
 * The track identity each session published, read once and kept until the watch learns the metadata may have changed,
 * so a pass of the session watch does not read the metadata of every session through a binder call. Keyed by [K], the
 * session token on a device. A null identity is kept too: it records that the session published no metadata.
 *
 * Not thread-safe: the session watch fills and invalidates it on the main thread, like the rest of the watch.
 */
internal class TrackIdentityCache<K : Any> {
    private val identities = HashMap<K, TrackIdentity?>()

    /** Return the identity kept for [key], reading it with [read] and keeping the result when there is none yet. */
    fun identityOf(
        key: K,
        read: () -> TrackIdentity?,
    ): TrackIdentity? = if (key in identities) identities[key] else read().also { identities[key] = it }

    /** Mark every entry stale: the next [identityOf] of each key reads again. */
    fun invalidate() = identities.clear()
}
