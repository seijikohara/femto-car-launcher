package io.github.seijikohara.femto.data.places

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri

/**
 * Return the implicit intent that hands [target] to whichever app handles
 * `geo:` ([geoHandoffUri]). The launch ([geoLaunchIntentOrNull]) and
 * [hasDestinationHandler] share it, so the check asks with the action and
 * scheme the launch sends.
 */
internal fun destinationIntent(
    target: PlaceTarget,
    label: String = "",
): Intent = geoViewIntent(geoHandoffUri(target, label))

/** Return the implicit intent that views the `geo:` URI [uri]. */
internal fun geoViewIntent(uri: String): Intent = Intent(Intent.ACTION_VIEW, uri.toUri())

/** An activity by its package and class name: the hand-off rule's view of a component, free of Android types. */
internal data class AppActivity(
    val packageName: String,
    val className: String,
)

/**
 * The package manager's two answers the hand-off rule needs about one `geo:`
 * intent, both for default-category activities as `startActivity` matches them.
 */
internal interface GeoHandlerLookup {
    /** Every activity that takes the intent. */
    fun handlers(): List<AppActivity>

    /** What the intent resolves to: the user's default, the system's chooser, or null. */
    fun resolvedOrNull(): AppActivity?
}

/** How the launcher starts a `geo:` hand-off; see [geoHandoffChoice]. */
internal sealed interface GeoHandoffChoice {
    /** No app but the launcher itself takes it. */
    data object NoHandler : GeoHandoffChoice

    /** Start [activity] explicitly. */
    data class Direct(
        val activity: AppActivity,
    ) : GeoHandoffChoice

    /** Let the user pick, through a chooser that leaves out [excluded] (the launcher's own entrance). */
    data class Chooser(
        val excluded: List<AppActivity>,
    ) : GeoHandoffChoice
}

/**
 * Decide how the launcher starts a `geo:` hand-off now that it takes `geo:`
 * itself (the destination entrance), so the hand-off never comes back to it.
 *
 * 1. The handlers, minus every activity of [ownPackage]. None: [GeoHandoffChoice.NoHandler].
 * 2. The resolved activity is the user's default only if it is among those
 *    handlers; that rules out the system's resolver and the launcher itself.
 *    A default is started explicitly, as an implicit start would reach it.
 * 3. Otherwise one handler is started explicitly, and several go through a
 *    chooser without the launcher. The chooser offers no "Always", which the
 *    system's resolver did; a default set in the system settings still
 *    applies through step 2.
 */
internal fun GeoHandlerLookup.geoHandoffChoice(ownPackage: String): GeoHandoffChoice {
    val (own, others) = handlers().partition { it.packageName == ownPackage }
    val resolved = resolvedOrNull()
    return when {
        others.isEmpty() -> GeoHandoffChoice.NoHandler
        resolved != null && resolved in others -> GeoHandoffChoice.Direct(resolved)
        others.size == 1 -> GeoHandoffChoice.Direct(others.single())
        else -> GeoHandoffChoice.Chooser(excluded = own)
    }
}

/**
 * Return the intent that starts the `geo:` hand-off [view] (an implicit
 * `ACTION_VIEW`, as [destinationIntent] or [geoViewIntent] builds it) by
 * [geoHandoffChoice], ready for a new task, or null when no other app takes it.
 * Every `geo:` start the launcher makes goes through here.
 */
internal fun Context.geoLaunchIntentOrNull(view: Intent): Intent? =
    when (val choice = PackageManagerGeoLookup(packageManager, view).geoHandoffChoice(packageName)) {
        GeoHandoffChoice.NoHandler -> {
            null
        }

        is GeoHandoffChoice.Direct -> {
            Intent(view).setComponent(choice.activity.toComponentName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        is GeoHandoffChoice.Chooser -> {
            Intent
                .createChooser(view, null)
                .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, choice.excluded.map { it.toComponentName() }.toTypedArray())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

/**
 * Return whether an app other than the launcher takes a destination hand-off,
 * by the rule the launch follows ([geoHandoffChoice]).
 *
 * The manifest's `<queries>` entry for the `geo` scheme keeps such an app
 * visible on Android 11+ even when it has no launcher icon. A failed lookup
 * answers true: a wrong "no app" would block a hand-off that works, while a
 * wrong "yes" only leaves the silent launch the panel had before this check.
 */
internal fun Context.hasDestinationHandler(): Boolean =
    runCatching {
        PackageManagerGeoLookup(packageManager, destinationIntent(PlaceTarget.Query("")))
            .geoHandoffChoice(packageName) != GeoHandoffChoice.NoHandler
    }.getOrDefault(true)

private class PackageManagerGeoLookup(
    private val packageManager: PackageManager,
    private val intent: Intent,
) : GeoHandlerLookup {
    private val defaultOnly = PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())

    override fun handlers(): List<AppActivity> =
        packageManager
            .queryIntentActivities(intent, defaultOnly)
            .map { AppActivity(it.activityInfo.packageName, it.activityInfo.name) }

    override fun resolvedOrNull(): AppActivity? =
        packageManager
            .resolveActivity(intent, defaultOnly)
            ?.activityInfo
            ?.let { AppActivity(it.packageName, it.name) }
}

private fun AppActivity.toComponentName(): ComponentName = ComponentName(packageName, className)
