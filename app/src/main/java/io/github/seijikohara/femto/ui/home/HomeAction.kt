package io.github.seijikohara.femto.ui.home

import android.content.ComponentName
import io.github.seijikohara.femto.data.music.MusicCommand
import io.github.seijikohara.femto.ui.home.components.AppsBarShortcut

internal sealed interface HomeAction {
    data class LaunchApp(
        val componentName: ComponentName,
    ) : HomeAction

    data object OpenAppDrawer : HomeAction

    data object OpenMaps : HomeAction

    data object ConnectMusicPlayer : HomeAction

    /** Open the app behind the current media session (the music card's source icon). */
    data class LaunchMusicSource(
        val packageName: String,
    ) : HomeAction

    data class Shortcut(
        val target: AppsBarShortcut,
    ) : HomeAction

    data class Music(
        val command: MusicCommand,
    ) : HomeAction

    data object OpenBrowser : HomeAction

    data object OpenSettings : HomeAction

    data object OpenAssistant : HomeAction

    data object ResetTrip : HomeAction
}
