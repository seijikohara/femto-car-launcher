package io.github.seijikohara.femto.testfixtures

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.os.Process
import android.os.UserHandle
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/**
 * Build a real [LauncherActivityInfo] for Robolectric tests. The platform class
 * has no public constructor, so this goes through the hidden
 * `LauncherActivityInfoInternal` / `IncrementalStatesInfo` types reflectively,
 * matching the API 33 constructor shapes (tests pin `@Config(sdk = [33])`).
 *
 * Pass `label = null` together with `hasApplicationInfo = false` to produce an
 * entry whose label resolution throws (`ComponentInfo.loadUnsafeLabel`
 * dereferences the missing [ApplicationInfo]); tests use that to exercise the
 * per-app isolation path in `AppsRepository.queryApps`.
 */
internal fun fakeLauncherActivityInfo(
    context: Context,
    packageName: String,
    className: String = "$packageName.MainActivity",
    label: String? = packageName,
    hasApplicationInfo: Boolean = true,
    user: UserHandle = Process.myUserHandle(),
): LauncherActivityInfo {
    val activityInfo =
        ActivityInfo().apply {
            this.packageName = packageName
            name = className
            nonLocalizedLabel = label
            if (hasApplicationInfo) {
                applicationInfo = ApplicationInfo().also { it.packageName = packageName }
            }
        }
    val statesClass = Class.forName("android.content.pm.IncrementalStatesInfo")
    val states: Any =
        ReflectionHelpers.callConstructor(
            statesClass,
            ClassParameter.from(Boolean::class.javaPrimitiveType!!, false),
            ClassParameter.from(Float::class.javaPrimitiveType!!, 1f),
        )
    val internalClass = Class.forName("android.content.pm.LauncherActivityInfoInternal")
    val internal: Any =
        ReflectionHelpers.callConstructor(
            internalClass,
            ClassParameter.from(ActivityInfo::class.java, activityInfo),
            ClassParameter.from(statesClass, states),
        )
    return ReflectionHelpers.callConstructor(
        LauncherActivityInfo::class.java,
        ClassParameter.from(Context::class.java, context),
        ClassParameter.from(UserHandle::class.java, user),
        ClassParameter.from(internalClass, internal),
    )
}
