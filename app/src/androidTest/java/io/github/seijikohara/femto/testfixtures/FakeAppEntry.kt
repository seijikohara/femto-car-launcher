package io.github.seijikohara.femto.testfixtures

import android.content.ComponentName
import android.graphics.Bitmap
import io.github.seijikohara.femto.data.apps.AppEntry

/**
 * Build an [AppEntry] for UI tests with a 1x1 placeholder icon.
 *
 * The icon never renders meaningfully at 1x1 — tests assert on the
 * label and the resolved [ComponentName], not on pixels — so the
 * smallest valid [Bitmap] keeps allocation negligible per entry.
 */
internal fun fakeAppEntry(
    packageName: String = "com.example.app",
    className: String = ".MainActivity",
    label: String = "Example",
): AppEntry =
    AppEntry(
        componentName = ComponentName(packageName, className),
        label = label,
        icon = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
    )
