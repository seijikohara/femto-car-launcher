package io.github.seijikohara.femto.ui.home.components

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.seijikohara.femto.data.location.TripScenePalette

/**
 * Owns one native Vulkan renderer handle across a [SurfaceView]'s lifecycle. The
 * instance is created eagerly ([vulkanUsable]) so the caller can decide between
 * this and the 2D fallback before laying out a surface; the swapchain is built
 * when the surface appears. A start failure flips [vulkanUsable] false so the
 * caller can fall back mid-flight. Never throws — a Vulkan-less device just
 * reports unusable.
 *
 * The controller outlives an individual surface: [release] is called when the
 * host leaves composition, while the surface's own callbacks start/stop the
 * swapchain, so the instance survives a transient surface swap (the panel's
 * enter/exit gates the surface, see [TripFlyoverSurface]'s caller).
 */
internal class TripFlyoverController {
    private var handle: Long = 0L
    private var started = false

    // Latest scene theme, applied on every (re)start so a surface that mounts
    // after the theme was pushed still clears/blends with the right palette.
    private var theme: TripScenePalette? = null

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
                    } else {
                        // Re-apply the theme the host already pushed before the
                        // surface existed (the render thread starts on defaults).
                        theme?.let { applyTheme(it) }
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

    fun setTheme(palette: TripScenePalette) {
        theme = palette
        // Apply as soon as the instance exists — flyover_set_theme only writes the
        // shared theme atomics (no Vulkan objects), so it is safe before the render
        // thread starts, and the first frame then clears/blends with the right
        // palette instead of the dark default. If the handle is not ready yet, the
        // surface-start path re-applies the stored theme.
        if (handle != 0L) applyTheme(palette)
    }

    private fun applyTheme(palette: TripScenePalette) {
        runCatching {
            TripFlyoverNative.nativeSetTheme(
                handle,
                palette.background[0],
                palette.background[1],
                palette.background[2],
                palette.head[0],
                palette.head[1],
                palette.head[2],
                palette.isDark,
            )
        }
    }

    /** False once the render thread has started and then died (a runtime Vulkan error). */
    fun renderThreadDied(): Boolean =
        handle != 0L && started && !runCatching { TripFlyoverNative.nativeIsRunning(handle) }.getOrDefault(false)

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
    val onUnavailableState = rememberUpdatedState(onUnavailable)
    AndroidView(
        modifier = modifier,
        factory = { context: Context ->
            SurfaceView(context).apply {
                // Above the app window's own surface so the Compose HUD drawn
                // after this view still composites on top of the render.
                setZOrderMediaOverlay(true)
                holder.addCallback(controller.holderCallback { onUnavailableState.value() })
            }
        },
    )
    LaunchedEffect(wireframe) { controller.setTrack(wireframe) }
    // Reading progress here recomposes only this leaf (not the panel); it re-runs
    // the effect each frame to push the playhead natively.
    LaunchedEffect(progress) {
        controller.setProgress(progress)
        // Cheap per-frame liveness check: if the render thread died at runtime
        // (a Vulkan error after a successful start), fall back to 2D so the panel
        // never freezes on a dead surface.
        if (controller.renderThreadDied()) onUnavailableState.value()
    }
}
