package io.github.seijikohara.femto.data.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import androidx.core.content.getSystemService
import io.github.seijikohara.femto.BuildConfig
import io.github.seijikohara.femto.data.update.UpdateChannel
import io.github.seijikohara.femto.data.update.UpdatePreferences
import io.github.seijikohara.femto.data.update.UpdateRepository
import io.github.seijikohara.femto.data.update.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

// How many historical exit records to surface: enough to span a debugging
// session's worth of restarts without the section scrolling forever.
private const val MAX_EXIT_HISTORY = 8

private val FACT_TIMESTAMP_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault())

// Internal so every diagnostics timestamp shares one format (the SETTINGS dump
// prints the updater's last attempt with it).
internal fun formatEpochMillis(epochMillis: Long): String =
    FACT_TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(epochMillis))

// The reasons that indicate the process actually misbehaved, as opposed to a
// normal lifecycle exit (user swipe-away, OS package churn, and the like).
private val UNHEALTHY_EXIT_REASONS =
    setOf(
        ApplicationExitInfo.REASON_CRASH,
        ApplicationExitInfo.REASON_CRASH_NATIVE,
        ApplicationExitInfo.REASON_ANR,
        ApplicationExitInfo.REASON_LOW_MEMORY,
    )

/**
 * Maps every [ApplicationExitInfo] `REASON_*` constant to its bare suffix
 * name; an int with no known constant renders as `REASON_<n>` rather than
 * silently dropping the record — a firmware fork can define reasons ahead of
 * the SDK this app compiles against.
 */
internal fun exitReasonName(reason: Int): String =
    when (reason) {
        ApplicationExitInfo.REASON_UNKNOWN -> "UNKNOWN"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        ApplicationExitInfo.REASON_FREEZER -> "FREEZER"
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "PACKAGE_STATE_CHANGE"
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "PACKAGE_UPDATED"
        else -> "REASON_$reason"
    }

/** The release channel a build of [flavor] follows; a flavor without a feed of its own says so. */
internal fun channelFact(flavor: String): DiagnosticFact =
    DiagnosticFact(
        "Channel",
        FactValue.Text(UpdateChannel.fromFlavorOrNull(flavor)?.id ?: "$flavor (no update feed)"),
    )

/**
 * Where the updater stands, with its last check attempt ([lastAttemptAtMs],
 * failed attempts included). Only a user's own action ends in a failure (an
 * automatic check that fails stays quiet), so a failure is a WARNING; every
 * other state, an available update included, is plain information.
 */
internal fun updateCheckFact(
    state: UpdateState,
    lastAttemptAtMs: Long?,
): DiagnosticFact {
    // A build that never checks has no attempt to report.
    val value =
        if (state == UpdateState.Disabled) {
            updateOutcome(state)
        } else {
            "${updateOutcome(state)} (last attempt ${lastAttemptAtMs?.let(::formatEpochMillis) ?: "never"})"
        }
    return DiagnosticFact(
        "Update check",
        if (state is UpdateState.Failed) FactValue.Status(value, FactHealth.WARNING) else FactValue.Text(value),
    )
}

private fun updateOutcome(state: UpdateState): String =
    when (state) {
        UpdateState.Disabled -> "disabled for this build"
        is UpdateState.Idle -> "no result yet"
        UpdateState.Checking -> "checking"
        UpdateState.UpToDate -> "up to date"
        is UpdateState.Available -> "${state.manifest.versionName} available"
        is UpdateState.Downloading -> "downloading ${state.manifest.versionName}, ${state.percent()}%"
        is UpdateState.Ready -> "${state.manifest.versionName} ready to install"
        is UpdateState.Installing -> "installing ${state.manifest.versionName}"
        is UpdateState.Failed -> "failed: ${state.reason.name}"
    }

private fun UpdateState.Downloading.percent(): Int = (fraction * 100).roundToInt()

/** Collects the APP and CRASH_HISTORY diagnostics sections. */
internal class AppFactsCollector(
    private val context: Context,
) {
    suspend fun appFacts(): SectionPayload.Facts =
        withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            val packageInfo = packageManager.getPackageInfo(context.packageName, 0)
            SectionPayload.Facts(
                buildList {
                    add(
                        DiagnosticFact(
                            "App",
                            FactValue.Text(
                                "${BuildConfig.VERSION_NAME} (${if (BuildConfig.DEBUG) "debug" else "release"})",
                            ),
                        ),
                    )
                    add(DiagnosticFact("Version code", FactValue.Text(packageInfo.longVersionCode.toString())))
                    add(
                        DiagnosticFact(
                            "Installed",
                            FactValue.Text(
                                "${formatEpochMillis(packageInfo.firstInstallTime)} " +
                                    "(updated ${formatEpochMillis(packageInfo.lastUpdateTime)})",
                            ),
                        ),
                    )
                    add(
                        DiagnosticFact(
                            "Installer",
                            FactValue.Text(
                                packageManager.getInstallSourceInfo(context.packageName).installingPackageName
                                    ?: "sideload/unknown",
                            ),
                        ),
                    )
                    add(channelFact(BuildConfig.FLAVOR))
                    add(
                        updateCheckFact(
                            state = UpdateRepository.get(context).state.value,
                            lastAttemptAtMs = UpdatePreferences(context).settings.first().lastCheckAttemptAt,
                        ),
                    )
                },
            )
        }

    suspend fun crashHistory(): SectionPayload.Facts =
        withContext(Dispatchers.IO) {
            val exitInfos =
                context
                    .getSystemService<ActivityManager>()
                    ?.getHistoricalProcessExitReasons(null, 0, MAX_EXIT_HISTORY)
                    .orEmpty()
            SectionPayload.Facts(
                if (exitInfos.isEmpty()) {
                    listOf(DiagnosticFact("Exit history", FactValue.Text("none recorded")))
                } else {
                    exitInfos.map { exitInfo ->
                        val label = formatEpochMillis(exitInfo.timestamp)
                        val value = "${exitReasonName(
                            exitInfo.reason,
                        )} (status ${exitInfo.status}): ${exitInfo.description ?: "-"}"
                        DiagnosticFact(
                            label,
                            if (exitInfo.reason in UNHEALTHY_EXIT_REASONS) {
                                FactValue.Status(value, FactHealth.WARNING)
                            } else {
                                FactValue.Text(value)
                            },
                        )
                    }
                },
            )
        }
}
