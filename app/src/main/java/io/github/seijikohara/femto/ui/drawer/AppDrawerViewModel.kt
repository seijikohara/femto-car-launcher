package io.github.seijikohara.femto.ui.drawer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.seijikohara.femto.data.apps.AppEntry
import io.github.seijikohara.femto.data.apps.AppsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "AppDrawerViewModel"

/**
 * Owns the drawer's app-query state per CLAUDE.md#compose-architecture.
 *
 * [queryApps] is injected as a plain suspend seam so JVM tests exercise the
 * Loading/Content/Error transitions without Android types; production wires
 * [AppsRepository.queryApps] via [AppDrawerViewModelFactory].
 */
internal class AppDrawerViewModel(
    private val queryApps: suspend () -> List<AppEntry>,
) : ViewModel() {
    private val _uiState = MutableStateFlow<AppDrawerUiState>(AppDrawerUiState.Loading)
    val uiState: StateFlow<AppDrawerUiState> = _uiState.asStateFlow()

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
                .onSuccess { apps -> _uiState.value = AppDrawerUiState.Content(apps) }
                .onFailure {
                    // runCatching also traps cancellation; rethrow so it never
                    // renders as the Error state.
                    if (it is CancellationException) throw it
                    Log.e(TAG, "app query failed", it)
                    _uiState.value = AppDrawerUiState.Error
                }
        }
    }
}

/** Wires the production [AppsRepository] without an UNCHECKED_CAST factory. */
internal val AppDrawerViewModelFactory: ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            val application = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
            AppDrawerViewModel(queryApps = AppsRepository(application)::queryApps)
        }
    }
