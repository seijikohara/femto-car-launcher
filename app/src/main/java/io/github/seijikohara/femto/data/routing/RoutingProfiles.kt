package io.github.seijikohara.femto.data.routing

import io.github.seijikohara.femto.data.common.writeAtomically
import java.io.File
import java.io.IOException
import java.io.InputStream

/** The asset directory of the engine's runtime files, both from the BRouter tag the `:brouter` module names. */
internal const val ROUTING_ASSET_DIR = "routing"

/** The tag table every profile and tile is encoded against; BRouter reads it from the profile's directory. */
private const val LOOKUP_TABLE = "lookups.dat"

/** BRouter's car profile, with the kinematic model that estimates travel time from speed limits and curves. */
internal const val CAR_PROFILE = "car-vario.brf"

/**
 * The engine's runtime files as real files: BRouter opens the profile and `lookups.dat` by path, so the assets are
 * copied into [dir]. A copy is rewritten only when it differs from the asset, which an app update can change.
 */
internal class RoutingProfileFiles(
    private val dir: File,
    private val openAsset: (path: String) -> InputStream,
) {
    /** Copy the runtime files where they are missing or stale and return the profile; blocking, throws IOException. */
    fun prepare(): File = File(dir, CAR_PROFILE).also { listOf(LOOKUP_TABLE, CAR_PROFILE).forEach(::copyIfStale) }

    private fun copyIfStale(name: String) {
        val bytes = openAsset("$ROUTING_ASSET_DIR/$name").use(InputStream::readBytes)
        val target = File(dir, name)
        val current = target.takeIf { it.isFile && it.length() == bytes.size.toLong() }?.readBytes()
        if (bytes.contentEquals(current)) return
        if (!writeAtomically(target, bytes)) throw IOException("could not write $target")
    }
}
