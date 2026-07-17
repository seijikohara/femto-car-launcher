package io.github.seijikohara.femto.ui.home.components

import android.util.Log
import android.view.Surface

/**
 * JNI surface for the native Vulkan trip flyover (app/src/main/cpp). The library
 * is loaded opportunistically: an ABI without `libtripflyover.so`, or a device
 * without a Vulkan driver, simply reports unavailable and the UI renders the 2D
 * Compose fallback instead. Nothing here may crash the HOME launcher.
 *
 * The handle is the native renderer pointer boxed as a Long; 0 means "none", and
 * every native call tolerates it.
 */
internal object TripFlyoverNative {
    private const val TAG = "TripFlyoverNative"

    /** Whether libtripflyover.so loaded for this ABI. */
    val libraryAvailable: Boolean =
        runCatching { System.loadLibrary("tripflyover") }
            .onFailure { Log.i(TAG, "native flyover library unavailable; using 2D fallback") }
            .isSuccess

    external fun nativeCreate(): Long

    external fun nativeStart(
        handle: Long,
        surface: Surface,
        width: Int,
        height: Int,
    ): Boolean

    external fun nativeSetTrack(
        handle: Long,
        data: FloatArray,
        count: Int,
    )

    external fun nativeSetProgress(
        handle: Long,
        progress: Float,
    )

    external fun nativeSetTheme(
        handle: Long,
        backgroundR: Float,
        backgroundG: Float,
        backgroundB: Float,
        headR: Float,
        headG: Float,
        headB: Float,
        isDark: Boolean,
    )

    external fun nativeIsRunning(handle: Long): Boolean

    external fun nativeResize(
        handle: Long,
        width: Int,
        height: Int,
    )

    external fun nativeStop(handle: Long)

    external fun nativeDestroy(handle: Long)
}
