package io.github.seijikohara.femto.data.video

import android.content.Context
import android.content.Intent
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.flow.first

private const val TAG = "VideoSource"

/**
 * The read grants the app keeps on picked video documents. A document picked
 * through the system file picker is readable only while its grant lasts: the
 * grant is persisted so the file still plays after a restart, and a grant the
 * user or the provider revokes later reads as not held. [ContentResolverVideoSourceGrants]
 * is the production implementation; tests substitute an in-memory fake.
 *
 * Each call may cross into the document's provider, so callers run them off
 * the main thread.
 */
internal interface VideoSourceGrants {
    /** Persist the read grant on [uri]; false when the provider refuses it. */
    fun take(uri: String): Boolean

    fun release(uri: String)

    /** Whether the app still holds a persisted read grant on [uri]. */
    fun holds(uri: String): Boolean

    /** The document's display name, or null when it cannot be read. */
    fun displayNameOrNull(uri: String): String?
}

/**
 * Keep [uri], a document the user just picked, as the video window's file:
 * its read grant is persisted first, then the record moves to it, and only
 * once the store reads back the new record is the grant on the file it
 * replaces released. A grant that cannot be kept, or a write the store lost
 * (its writes log and swallow failures), leaves the current file and its
 * grant in place and returns false; the grant just taken is then let go, as
 * nothing names its file.
 */
internal suspend fun VideoSettingsStore.adoptSource(
    uri: String,
    grants: VideoSourceGrants,
): Boolean {
    val previous = settings.first().sourceUri
    if (!grants.take(uri)) return false
    setSourceUri(uri)
    val recorded = settings.first().sourceUri == uri
    if (recorded) {
        previous?.takeIf { it != uri }?.let(grants::release)
    } else if (uri != previous) {
        grants.release(uri)
    }
    return recorded
}

/** [VideoSourceGrants] over the app's [android.content.ContentResolver]. */
internal class ContentResolverVideoSourceGrants(
    context: Context,
) : VideoSourceGrants {
    private val resolver = context.applicationContext.contentResolver

    // Only the exception's class is logged: its message can carry the URI,
    // which names the user's file.
    override fun take(uri: String): Boolean =
        runCatching { resolver.takePersistableUriPermission(uri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            .onFailure { Log.w(TAG, "read grant refused: ${it.javaClass.simpleName}") }
            .isSuccess

    override fun release(uri: String) {
        runCatching { resolver.releasePersistableUriPermission(uri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            .onFailure { Log.w(TAG, "read grant release failed: ${it.javaClass.simpleName}") }
    }

    override fun holds(uri: String): Boolean =
        uri.toUri().let { target -> resolver.persistedUriPermissions.any { it.uri == target && it.isReadPermission } }

    override fun displayNameOrNull(uri: String): String? =
        runCatching {
            resolver
                .query(uri.toUri(), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> cursor.takeIf { it.moveToFirst() }?.getString(0) }
        }.onFailure { Log.w(TAG, "display name unreadable: ${it.javaClass.simpleName}") }
            .getOrNull()
}
