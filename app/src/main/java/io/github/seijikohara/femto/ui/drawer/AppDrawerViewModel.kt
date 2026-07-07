package io.github.seijikohara.femto.ui.drawer

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
 * just on [AppDrawerAction.Refresh]: a launch from the very sheet currently
 * open should bump the Recent row immediately, without waiting for the next
 * full app re-query.
 */
internal class AppDrawerViewModel(
    private val queryApps: suspend () -> List<AppEntry>,
    recentComponents: Flow<List<String>> = emptyFlow(),
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
    }

    fun onAction(action: AppDrawerAction) =
        when (action) {
            AppDrawerAction.Refresh -> refresh()
        }

    // Flip back to Loading first so a retry shows progress rather than a stale
    // error, mirroring the pre-ViewModel sheet behavior.
    private fun refresh() {
        _uiState.value = AppDrawerUiState.Loading
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
                    _uiState.value = AppDrawerUiState.Error
                }
        }
    }
}

/** Wires the production [AppsRepository] and [RecentAppsPreferences] without an UNCHECKED_CAST factory. */
internal val AppDrawerViewModelFactory: ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            val application = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
            AppDrawerViewModel(
                queryApps = AppsRepository(application)::queryApps,
                recentComponents = RecentAppsPreferences(application).recentComponents,
            )
        }
    }
