package io.github.seijikohara.femto.data.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import androidx.core.content.getSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// A GL_RENDERER string needs a current GL context; none exists off-screen, so
// probe once with a throwaway 1x1 pbuffer context and cache — the GPU cannot
// change within a process lifetime.
private object GpuInfoProbe {
    @Volatile private var cached: GpuInfo? = null
    private val lock = Any()

    data class GpuInfo(
        val renderer: String?,
        val vendor: String?,
        val version: String?,
    )

    fun probe(): GpuInfo =
        cached ?: synchronized(lock) {
            cached ?: runProbe().also { cached = it }
        }

    private fun runProbe(): GpuInfo =
        runCatching {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(display != EGL14.EGL_NO_DISPLAY)
            check(EGL14.eglInitialize(display, IntArray(1), 0, IntArray(1), 0))
            try {
                val configs = arrayOfNulls<EGLConfig>(1)
                val numConfigs = IntArray(1)
                val configAttrs =
                    intArrayOf(
                        EGL14.EGL_RENDERABLE_TYPE,
                        EGL14.EGL_OPENGL_ES2_BIT,
                        EGL14.EGL_SURFACE_TYPE,
                        EGL14.EGL_PBUFFER_BIT,
                        EGL14.EGL_NONE,
                    )
                check(EGL14.eglChooseConfig(display, configAttrs, 0, configs, 0, 1, numConfigs, 0) && numConfigs[0] > 0)
                val context =
                    EGL14.eglCreateContext(
                        display,
                        configs[0],
                        EGL14.EGL_NO_CONTEXT,
                        intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
                        0,
                    )
                check(context != EGL14.EGL_NO_CONTEXT)
                val surface =
                    EGL14.eglCreatePbufferSurface(
                        display,
                        configs[0],
                        intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE),
                        0,
                    )
                check(EGL14.eglMakeCurrent(display, surface, surface, context))
                val info =
                    GpuInfo(
                        renderer = GLES20.glGetString(GLES20.GL_RENDERER),
                        vendor = GLES20.glGetString(GLES20.GL_VENDOR),
                        version = GLES20.glGetString(GLES20.GL_VERSION),
                    )
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(display, surface)
                EGL14.eglDestroyContext(display, context)
                info
            } finally {
                EGL14.eglTerminate(display)
            }
        }.getOrElse { GpuInfo(null, null, null) }
}

private fun vulkanSupportLabel(packageManager: PackageManager): String {
    if (!packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)) {
        return "not supported"
    }
    val level =
        packageManager.systemAvailableFeatures
            .firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL }
            ?.version
            ?: 0
    return "supported (level $level)"
}

private fun gpuFact(gpuInfo: GpuInfoProbe.GpuInfo): DiagnosticFact =
    gpuInfo.renderer?.let { renderer ->
        DiagnosticFact("GPU", FactValue.Text("$renderer (${gpuInfo.vendor})"))
    } ?: DiagnosticFact("GPU", FactValue.Status("unavailable", FactHealth.WARNING))

/** Collects the GRAPHICS diagnostics section. */
internal class GraphicsFactsCollector(
    private val context: Context,
) {
    suspend fun graphicsFacts(): SectionPayload.Facts =
        withContext(Dispatchers.IO) {
            val glEsVersion = context.getSystemService<ActivityManager>()?.deviceConfigurationInfo?.glEsVersion
            val gpuInfo = GpuInfoProbe.probe()
            SectionPayload.Facts(
                buildList {
                    add(DiagnosticFact("OpenGL ES", FactValue.Text(glEsVersion ?: "unavailable")))
                    add(DiagnosticFact("Vulkan", FactValue.Text(vulkanSupportLabel(context.packageManager))))
                    add(gpuFact(gpuInfo))
                    add(DiagnosticFact("GL version", FactValue.Text(gpuInfo.version ?: "unavailable")))
                },
            )
        }
}
