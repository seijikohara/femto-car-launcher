package io.github.seijikohara.femto.ui.video

import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState

/**
 * The player's picture, drawn into a [TextureView] that [host] attaches while
 * this is composed and the launcher is on screen (started). The launcher
 * leaving the screen releases the surface; the player keeps playing, so the
 * audio goes on. The picture keeps its own aspect ratio, letterboxed in the
 * scrim colour.
 */
@Composable
internal fun VideoSurface(
    host: VideoPlayer,
    modifier: Modifier = Modifier,
) {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val aspectRatio by host.videoAspectRatio.collectAsStateWithLifecycle()
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.scrim),
        contentAlignment = Alignment.Center,
    ) {
        if (lifecycleState.isAtLeast(Lifecycle.State.STARTED)) {
            AndroidView(
                factory = { context -> TextureView(context).also(host::attach) },
                onRelease = host::detach,
                modifier = aspectRatio?.let { Modifier.aspectRatio(it) } ?: Modifier.fillMaxSize(),
            )
        }
    }
}
