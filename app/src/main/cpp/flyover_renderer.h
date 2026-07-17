#pragma once

#include <android/native_window.h>

// Opaque C-style handle for the native Vulkan trip-flyover renderer. The Kotlin
// side (via jni_bridge) drives the whole lifecycle; every entry point is
// failure-tolerant so a Vulkan-less device degrades to the 2D fallback rather
// than crashing the HOME launcher.
struct FlyoverRenderer;

// Create the Vulkan instance and confirm a usable device exists. Returns null
// when Vulkan is unavailable (no loader, no device, missing extension).
FlyoverRenderer *flyover_create();

// Bind an ANativeWindow (from the SurfaceView's Surface), build the swapchain +
// pipeline, and start the render thread. Returns false on any failure — the
// caller then destroys the renderer and shows the fallback.
bool flyover_start(FlyoverRenderer *r, ANativeWindow *window, int width, int height);

// Replace the rendered geometry: interleaved GL_LINES vertices, 7 floats each
// (x, y, z, r, g, b, distanceFraction). Thread-safe; uploaded on the render
// thread at the next frame.
void flyover_set_track(FlyoverRenderer *r, const float *data, int floatCount);

// Draw-on playhead in [0, 1] (used while the user scrubs / when paused).
void flyover_set_progress(FlyoverRenderer *r, float progress);

// Surface resized (rotation / layout). The render thread recreates the swapchain.
void flyover_resize(FlyoverRenderer *r, int width, int height);

// Stop the render thread and tear down the swapchain, keeping the device so a
// later flyover_start is cheap. Safe to call repeatedly.
void flyover_stop(FlyoverRenderer *r);

// Destroy everything and free the handle.
void flyover_destroy(FlyoverRenderer *r);
