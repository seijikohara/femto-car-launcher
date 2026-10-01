package io.github.seijikohara.femto.testfixtures

import androidx.media3.common.Player
import java.lang.reflect.Proxy

/**
 * A media3 [Player] that records every call made on it, by method name and
 * arguments, and answers each query with its type's zero value except
 * [playbackState]. Built as a dynamic proxy because the [Player] interface has
 * far more members than the video player wrapper touches; this keeps the
 * wrapper's tests free of a real decoder pipeline.
 */
internal class RecordingPlayer(
    var playbackState: Int = Player.STATE_IDLE,
) {
    val calls: MutableList<Pair<String, List<Any?>>> = mutableListOf()

    /** The names of the recorded calls, in order. */
    val callNames: List<String> get() = calls.map { it.first }

    val player: Player =
        Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            calls += method.name to args.orEmpty().toList()
            when {
                method.name == "getPlaybackState" -> playbackState
                method.returnType == Boolean::class.javaPrimitiveType -> false
                method.returnType == Int::class.javaPrimitiveType -> 0
                method.returnType == Long::class.javaPrimitiveType -> 0L
                method.returnType == Float::class.javaPrimitiveType -> 0f
                else -> null
            }
        } as Player
}
