package io.github.seijikohara.femto.ui.home

import android.content.ComponentName
import io.github.seijikohara.femto.ui.home.components.AppsBarShortcut
import io.github.seijikohara.femto.ui.home.components.MusicCommand

internal sealed interface HomeAction {
    data class LaunchApp(
        val componentName: ComponentName,
    ) : HomeAction

    data object OpenAppDrawer : HomeAction

    data object OpenMaps : HomeAction

    data object ConnectMusicPlayer : HomeAction

    data class Shortcut(
        val target: AppsBarShortcut,
    ) : HomeAction

    data class Music(
        val command: MusicCommand,
    ) : HomeAction
}
