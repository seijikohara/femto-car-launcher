// JNI surface for the native Vulkan trip flyover. Mirrors the external funcs on
// the Kotlin object io.github.seijikohara.femto.ui.home.components.TripFlyoverNative.
// The handle is the FlyoverRenderer* boxed as a jlong; 0 means "no renderer",
// so every call tolerates it and the Kotlin side falls back to 2D.

#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>

#include "flyover_renderer.h"

namespace {
FlyoverRenderer *asRenderer(jlong handle) {
    return reinterpret_cast<FlyoverRenderer *>(handle);
}
}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_io_github_seijikohara_femto_ui_home_components_TripFlyoverNative_nativeCreate(
    JNIEnv *, jobject) {
    return reinterpret_cast<jlong>(flyover_create());
}

JNIEXPORT jboolean JNICALL
Java_io_github_seijikohara_femto_ui_home_components_TripFlyoverNative_nativeStart(
    JNIEnv *env, jobject, jlong handle, jobject surface, jint width, jint height) {
    if (handle == 0 || surface == nullptr) return JNI_FALSE;
    ANativeWindow *window = ANativeWindow_fromSurface(env, surface);
    if (window == nullptr) return JNI_FALSE;
    // flyover_start takes ownership of the window ref (released in flyover_stop).
    bool ok = flyover_start(asRenderer(handle), window, width, height);
    if (!ok) {
        ANativeWindow_release(window);
    }
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_io_github_seijikohara_femto_ui_home_components_TripFlyoverNative_nativeSetTrack(
    JNIEnv *env, jobject, jlong handle, jfloatArray data, jint count) {
    if (handle == 0 || data == nullptr || count <= 0) return;
    jsize len = env->GetArrayLength(data);
    if (count > len) count = len;
    jfloat *elems = env->GetFloatArrayElements(data, nullptr);
    if (elems == nullptr) return;
    flyover_set_track(asRenderer(handle), elems, count);
    env->ReleaseFloatArrayElements(data, elems, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_io_github_seijikohara_femto_ui_home_components_TripFlyoverNative_nativeSetProgress(
    JNIEnv *, jobject, jlong handle, jfloat progress) {
    if (handle != 0) flyover_set_progress(asRenderer(handle), progress);
}

JNIEXPORT void JNICALL
Java_io_github_seijikohara_femto_ui_home_components_TripFlyoverNative_nativeSetTheme(
    JNIEnv *, jobject, jlong handle, jfloat bgR, jfloat bgG, jfloat bgB, jfloat headR,
    jfloat headG, jfloat headB, jboolean isDark) {
    if (handle != 0) {
        flyover_set_theme(asRenderer(handle), bgR, bgG, bgB, headR, headG, headB,
                          isDark == JNI_TRUE);
    }
}

JNIEXPORT jboolean JNICALL
Java_io_github_seijikohara_femto_ui_home_components_TripFlyoverNative_nativeIsRunning(
    JNIEnv *, jobject, jlong handle) {
    return (handle != 0 && flyover_is_running(asRenderer(handle))) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_io_github_seijikohara_femto_ui_home_components_TripFlyoverNative_nativeResize(
    JNIEnv *, jobject, jlong handle, jint width, jint height) {
    if (handle != 0) flyover_resize(asRenderer(handle), width, height);
}

JNIEXPORT void JNICALL
Java_io_github_seijikohara_femto_ui_home_components_TripFlyoverNative_nativeStop(
    JNIEnv *, jobject, jlong handle) {
    if (handle != 0) flyover_stop(asRenderer(handle));
}

JNIEXPORT void JNICALL
Java_io_github_seijikohara_femto_ui_home_components_TripFlyoverNative_nativeDestroy(
    JNIEnv *, jobject, jlong handle) {
    if (handle != 0) flyover_destroy(asRenderer(handle));
}

}  // extern "C"
