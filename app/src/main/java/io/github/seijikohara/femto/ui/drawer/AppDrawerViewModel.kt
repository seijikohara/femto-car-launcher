package io.github.seijikohara.femto.ui.drawer

import android.content.ComponentName
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.seijikohara.femto.data.apps.AppEntry
import io.github.seijikohara.femto.data.apps.AppsRepository
import io.github.seijikohara.femto.data.apps.RecentAppsPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "AppDrawerViewModel"

/**
 * Owns the drawer's app-query state per `.claude/rules/compose.md`.
 *
 * [queryApps] is injected as a plain suspend seam so JVM tests exercise the
 * Loading/Content/Error transitions without Android types; production wires
 * [AppsRepository.queryApps] via [AppDrawerViewModelFactory].
 *
 * [recentComponents] is the launch-history store's ordered component list
 * (most-recent-first). Unlike [queryApps] it is collected continuously, not
 * just on [AppDrawerAction.Refresh]: a launch from the very panel currently
 * open should bump the Recent row immediately, without waiting for the next
 * full app re-query.
 *
 * [launchApp] and [recordLaunch] are the launch side effect: injected as plain
 * seams (production wires [AppsRepository.launch] and
 * [RecentAppsPreferences.recordLaunch]) so the panel is self-contained and JVM
 * tests can assert the resolved-launch-only recents rule without Android types.
 * [openAppInfo] and [requestUninstall] follow the same idiom for the
 * long-press management actions.
 *
 * [packageChanges] mirrors [AppsRepository.packageChanges]: every emission
 * re-queries in place (no Loading flash) so an uninstall completing — or an
 * install landing — while the panel is open updates the grid without blanking
 * it or resetting scroll.
 */
internal class AppDrawerViewModel(
    private val queryApps: suspend () -> List<AppEntry>,
    recentComponents: Flow<List<String>> = emptyFlow(),
    private val launchApp: (ComponentName) -> Boolean = { false },
    private val recordLaunch: suspend (String) -> Unit = {},
    private val openAppInfo: (ComponentName) -> Unit = {},
    private val requestUninstall: (ComponentName) -> Unit = {},
    packageChanges: Flow<Unit> = emptyFlow(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<AppDrawerUiState>(AppDrawerUiState.Loading)
    val uiState: StateFlow<AppDrawerUiState> = _uiState.asStateFlow()

    // Snapshot of the latest launch-history read: resolved into Content the
    // next time refresh() succeeds, and re-resolved in place below whenever
    // it changes while Content is already showing.
    private var latestRecentComponents: List<String> = emptyList()

    init {
        viewModelScope.launch {
            recentComponents.collect { recent ->
                latestRecentComponents = recent
                _uiState.update { state ->
                    when (state) {
                        is AppDrawerUiState.Content -> {
                            state.copy(
                                recentApps = resolveByOrder(state.apps, recent) { it.componentName.flattenToString() },
                            )
                        }

                        else -> {
                            state
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            packageChanges.collect { refresh(silent = true) }
        }
    }

    fun onAction(action: AppDrawerAction) =
        when (action) {
            AppDrawerAction.Refresh -> refresh()
            is AppDrawerAction.Launch -> launchAndRecord(action.componentName)
            is AppDrawerAction.OpenAppInfo -> openAppInfo(action.componentName)
            is AppDrawerAction.RequestUninstall -> requestUninstall(action.componentName)
        }

    // Only a resolved launch feeds the Recent row — a stale tile (its app
    // uninstalled since it was listed) never opened anything worth surfacing
    // again. viewModelScope outlives the panel's collapse-on-launch, so the
    // DataStore write always completes even as the launched app foregrounds.
    private fun launchAndRecord(componentName: ComponentName) {
        if (launchApp(componentName)) {
            viewModelScope.launch { recordLaunch(componentName.flattenToString()) }
        }
    }

    // Flip back to Loading first so a retry shows progress rather than a stale
    // error, mirroring the pre-ViewModel sheet behavior. A silent refresh (a
    // package-change while the panel is open) skips that flip and, on failure,
    // keeps the current grid — the stale list beats blanking an open panel.
    private fun refresh(silent: Boolean = false) {
        if (!silent) _uiState.value = AppDrawerUiState.Loading
        viewModelScope.launch {
            runCatching { queryApps() }
                .onSuccess { apps ->
                    _uiState.value =
                        AppDrawerUiState.Content(
                            apps = apps,
                            recentApps =
                                resolveByOrder(apps, latestRecentComponents) { it.componentName.flattenToString() },
                        )
                }.onFailure {
                    // runCatching also traps cancellation; rethrow so it never
                    // renders as the Error state.
                    if (it is CancellationException) throw it
                    Log.e(TAG, "app query failed", it)
                    if (!silent) _uiState.value = AppDrawerUiState.Error
                }
        }
    }
}

/** Wires the production [AppsRepository] and [RecentAppsPreferences] without an UNCHECKED_CAST factory. */
internal val AppDrawerViewModelFactory: ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            val application = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
            // One repository / store instance backs both the query and the launch
            // side effect so a launch records against the same recents store the
            // Recent row reads.
            val appsRepository = AppsRepository(application)
            val recentApps = RecentAppsPreferences(application)
            AppDrawerViewModel(
                queryApps = appsRepository::queryApps,
                recentComponents = recentApps.recentComponents,
                launchApp = appsRepository::launch,
                recordLaunch = recentApps::recordLaunch,
                openAppInfo = appsRepository::openAppDetails,
                requestUninstall = appsRepository::requestUninstall,
                packageChanges = appsRepository.packageChanges,
            )
        }
    }
