package io.github.seijikohara.femto.ui.home.components

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Owns one native Vulkan renderer handle across a [SurfaceView]'s lifecycle. The
 * instance is created eagerly ([vulkanUsable]) so the caller can decide between
 * this and the 2D fallback before laying out a surface; the swapchain is built
 * when the surface appears. A start failure flips [vulkanUsable] false so the
 * caller can fall back mid-flight. Never throws — a Vulkan-less device just
 * reports unusable.
 */
internal class TripFlyoverController {
    private var handle: Long = 0L
    private var started = false

    var vulkanUsable: Boolean = false
        private set

    /** Create the Vulkan instance once; returns whether it is usable. */
    fun ensureCreated(): Boolean {
        if (handle == 0L && TripFlyoverNative.libraryAvailable) {
            handle = runCatching { TripFlyoverNative.nativeCreate() }.getOrDefault(0L)
        }
        vulkanUsable = handle != 0L
        return vulkanUsable
    }

    fun holderCallback(onUnavailable: () -> Unit): SurfaceHolder.Callback =
        object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) = Unit

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int,
            ) {
                if (handle == 0L) return
                if (!started) {
                    started =
                        runCatching {
                            TripFlyoverNative.nativeStart(handle, holder.surface, width, height)
                        }.getOrDefault(false)
                    if (!started) {
                        vulkanUsable = false
                        onUnavailable()
                    }
                } else {
                    runCatching { TripFlyoverNative.nativeResize(handle, width, height) }
                }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                if (handle != 0L && started) {
                    runCatching { TripFlyoverNative.nativeStop(handle) }
                    started = false
                }
            }
        }

    fun setTrack(wireframe: FloatArray) {
        if (handle != 0L && wireframe.isNotEmpty()) {
            runCatching { TripFlyoverNative.nativeSetTrack(handle, wireframe, wireframe.size) }
        }
    }

    fun setProgress(progress: Float) {
        if (handle != 0L) runCatching { TripFlyoverNative.nativeSetProgress(handle, progress) }
    }

    fun release() {
        if (handle != 0L) {
            runCatching { TripFlyoverNative.nativeDestroy(handle) }
            handle = 0L
            started = false
            vulkanUsable = false
        }
    }
}

/**
 * The native Vulkan flyover as a full-bleed [SurfaceView] (media overlay so the
 * Compose HUD layers on top). [onUnavailable] fires if the swapchain fails to
 * start, letting the parent swap to the 2D fallback. [wireframe] and [progress]
 * are pushed to the native renderer as they change; the camera orbit is
 * self-driven natively.
 */
@Composable
internal fun TripFlyoverSurface(
    controller: TripFlyoverController,
    wireframe: FloatArray,
    progress: Float,
    onUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(controller) { onDispose { controller.release() } }
    AndroidView(
        modifier = modifier,
        factory = { context: Context ->
            SurfaceView(context).apply {
                // Above the app window's own surface so the Compose HUD drawn
                // after this view still composites on top of the render.
                setZOrderMediaOverlay(true)
                holder.addCallback(controller.holderCallback(onUnavailable))
            }
        },
    )
    LaunchedEffect(wireframe) { controller.setTrack(wireframe) }
    LaunchedEffect(progress) { controller.setProgress(progress) }
}
