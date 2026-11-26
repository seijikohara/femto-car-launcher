package io.github.seijikohara.femto.testfixtures

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Binder
import android.provider.Settings
import io.github.seijikohara.femto.data.music.ENABLED_NOTIFICATION_LISTENERS
import io.github.seijikohara.femto.data.music.MusicSessionListenerService
import org.robolectric.Shadows.shadowOf
import java.lang.reflect.Proxy

/**
 * Grant the launcher notification access the way the user does, by enabling its listener in the Secure setting
 * the platform and the launcher both read. Without it every session read is refused.
 */
internal fun grantNotificationListenerAccess(context: Context) {
    Settings.Secure.putString(
        context.contentResolver,
        ENABLED_NOTIFICATION_LISTENERS,
        ComponentName(context, MusicSessionListenerService::class.java).flattenToString(),
    )
}

/** Revoke the launcher's notification access, as switching it off in Settings does. */
internal fun revokeNotificationListenerAccess(context: Context) {
    Settings.Secure.putString(context.contentResolver, ENABLED_NOTIFICATION_LISTENERS, "")
}

/**
 * Register an active session of [packageName] in [state] publishing [metadata] (null for none, as a stream can) with
 * the platform, appended to the priority order [MediaSessionManager.getActiveSessions] hands out.
 */
internal fun MediaSessionManager.addFakeSession(
    context: Context,
    packageName: String,
    state: Int,
    metadata: MediaMetadata? = fakeMediaMetadata(title = "Headlights"),
): MediaController =
    fakeMediaController(context, packageName).also { controller ->
        shadowOf(controller).setPackageName(packageName)
        shadowOf(controller).setPlaybackState(fakePlaybackState(state))
        shadowOf(controller).setMetadata(metadata)
        shadowOf(this).addController(controller)
    }

/**
 * Push [state] from the session's own app, the way a change from outside the launcher arrives: the session's own
 * callback fires and the session set does not change.
 */
internal fun MediaController.pushPlaybackState(state: Int) =
    shadowOf(this).executeOnPlaybackStateChanged(fakePlaybackState(state))

/**
 * Publish [metadata] from the session's own app, the way a track change arrives: the controller reads it from now on
 * and the session's own callback fires; the session set does not change.
 */
internal fun MediaController.pushMetadata(metadata: MediaMetadata) {
    shadowOf(this).setMetadata(metadata)
    shadowOf(this).executeOnMetadataChanged(metadata)
}

/** Push PLAYING from the session's own app (see [pushPlaybackState]). */
internal fun MediaController.startPlaying() = pushPlaybackState(PlaybackState.STATE_PLAYING)

/**
 * Build a platform [MediaController] over a stub session binder. A controller cannot be built here the way the
 * platform builds one — it needs a [MediaSession.Token], and a token needs a session binder — so the binder is a
 * proxy over the hidden `ISessionController` interface. It answers the two calls the session paths make on it:
 * `asBinder`, which the token hashes and compares by (the watch sets are keyed by token), and `getPackageName`, which
 * the media3 token lookup rejects when null.
 */
internal fun fakeMediaController(
    context: Context,
    packageName: String,
): MediaController =
    Class.forName("android.media.session.ISessionController").let { sessionBinderType ->
        val binder = Binder()
        val sessionBinder =
            Proxy.newProxyInstance(sessionBinderType.classLoader, arrayOf(sessionBinderType)) { _, method, _ ->
                when (method.name) {
                    "asBinder" -> binder
                    "getPackageName" -> packageName
                    else -> unanswered(method.returnType)
                }
            }
        MediaController(
            context,
            MediaSession.Token::class.java
                .getDeclaredConstructor(Int::class.javaPrimitiveType, sessionBinderType)
                .newInstance(0, sessionBinder),
        )
    }

// An unanswered binder call still has to return something of the declared
// shape: handing null back for a primitive return type throws on unboxing.
private fun unanswered(returnType: Class<*>): Any? =
    when (returnType) {
        Boolean::class.javaPrimitiveType -> false
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        else -> null
    }
