package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.data.NowPlaying
import io.github.seijikohara.femto.ui.theme.FemtoDimens

internal sealed interface MusicCommand {
    data object PlayPause : MusicCommand

    data object SkipNext : MusicCommand

    data object SkipPrevious : MusicCommand
}

@Composable
internal fun MusicPanel(
    nowPlaying: NowPlaying?,
    onCommand: (MusicCommand) -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation = FemtoDimens.CardElevation,
) {
    Column(modifier = Modifier.padding(FemtoDimens.GridGutter)) {
        Text(
            text = "NOW PLAYING",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (nowPlaying == null) {
            Text(
                text = "Connect a player",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .clickable(onClick = onConnect),
            )
        } else {
            TrackInfo(nowPlaying)
            TransportControls(
                isPlaying = nowPlaying.isPlaying,
                onCommand = onCommand,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun TrackInfo(nowPlaying: NowPlaying) =
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = nowPlaying.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        nowPlaying.artist?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

@Composable
private fun TransportControls(
    isPlaying: Boolean,
    onCommand: (MusicCommand) -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
    verticalAlignment = Alignment.CenterVertically,
) {
    TransportButton(label = "◀◀", description = "Skip previous") { onCommand(MusicCommand.SkipPrevious) }
    TransportButton(
        label = if (isPlaying) "❚❚" else "▶",
        description = "Play / pause",
        primary = true,
    ) { onCommand(MusicCommand.PlayPause) }
    TransportButton(label = "▶▶", description = "Skip next") { onCommand(MusicCommand.SkipNext) }
}

@Composable
private fun TransportButton(
    label: String,
    description: String,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val container =
        if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val content =
        if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        modifier =
            Modifier
                .size(FemtoDimens.MinTouchTarget)
                .semantics { contentDescription = description },
        shape = CircleShape,
        color = container,
    ) {
        IconButton(onClick = onClick) {
            Text(text = label, style = MaterialTheme.typography.titleLarge, color = content)
        }
    }
}
