package io.github.seijikohara.femto.ui.home

import android.content.ComponentName
import androidx.compose.runtime.Immutable
import io.github.seijikohara.femto.data.AppEntry

@Immutable
data class HomeUiState(
    val isLoading: Boolean,
    val apps: List<AppEntry>,
) {
    companion object {
        val Initial: HomeUiState =
            HomeUiState(
                isLoading = true,
                apps = emptyList(),
            )
    }
}

sealed interface HomeAction {
    data object Refresh : HomeAction

    data class LaunchApp(
        val componentName: ComponentName,
    ) : HomeAction
}
