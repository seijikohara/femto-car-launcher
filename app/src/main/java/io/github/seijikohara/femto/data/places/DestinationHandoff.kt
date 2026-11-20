package io.github.seijikohara.femto.data.places

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri

/**
 * Return the implicit intent that hands [target] to whichever app handles
 * `geo:` ([geoHandoffUri]). The launch and [hasDestinationHandler] share it, so
 * the check asks with the action and scheme the launch sends.
 */
internal fun destinationIntent(
    target: PlaceTarget,
    label: String = "",
): Intent = Intent(Intent.ACTION_VIEW, geoHandoffUri(target, label).toUri())

/**
 * Return whether an installed app takes a destination hand-off.
 *
 * The lookup matches default-category activities only, as `startActivity`
 * does for an implicit intent. The manifest's `<queries>` entry for the `geo`
 * scheme keeps such an app visible on Android 11+ even when it has no
 * launcher icon. A failed lookup answers true: a wrong "no app" would block a
 * hand-off that works, while a wrong "yes" only leaves the silent launch the
 * panel had before this check.
 */
internal fun Context.hasDestinationHandler(): Boolean =
    runCatching {
        packageManager
            .queryIntentActivities(
                destinationIntent(PlaceTarget.Query("")),
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            ).isNotEmpty()
    }.getOrDefault(true)
