package io.github.seijikohara.femto.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.seijikohara.femto.data.AppsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the launcher home state. Queries [AppsRepository] for the
 * installed-app list once on creation; the screen emits actions
 * back through [onAction]. The ViewModel takes [Application] only
 * because [android.content.pm.LauncherApps] requires a `Context`;
 * once dependency injection is introduced this should switch to
 * constructor injection of the repository.
 */
class HomeViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = AppsRepository(application)

    private val _uiState = MutableStateFlow(HomeUiState.Initial)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.Refresh -> refresh()
            is HomeAction.LaunchApp -> repository.launch(action.componentName)
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val apps = repository.queryApps()
            _uiState.update { it.copy(isLoading = false, apps = apps) }
        }
    }
}
