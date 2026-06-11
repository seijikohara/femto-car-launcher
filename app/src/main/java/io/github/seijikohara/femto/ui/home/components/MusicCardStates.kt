package io.github.seijikohara.femto.ui.home.components

import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Disc
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.SkipBack
import com.composables.icons.lucide.SkipForward
import com.composables.icons.lucide.User
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.music.MusicCardState
import io.github.seijikohara.femto.data.music.MusicCommand
import io.github.seijikohara.femto.data.music.NowPlaying
import io.github.seijikohara.femto.data.music.sourceLabel
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.TabularFigures
import io.github.seijikohara.femto.ui.theme.cardCta
import io.github.seijikohara.femto.ui.theme.cardCtaHint
import io.github.seijikohara.femto.ui.theme.cardMeta
import io.github.seijikohara.femto.ui.theme.cardTitle
import io.github.seijikohara.femto.ui.theme.sectionLabel
import kotlinx.coroutines.delay

@Composable
internal fun MusicConnectState(onConnect: () -> Unit) =
    Surface(
        onClick = onConnect,
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(FemtoDimens.CardPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Lucide.Music,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
            Box(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.music_connect_cta),
                style = MaterialTheme.typography.cardCta(),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Box(modifier = Modifier.height(4.dp))
            Text(
                // Actionable copy the user must read to unlock the card: clear
                // the head-unit glance floor (CLAUDE.md#automotive-overrides),
                // unlike the sanctioned 13sp NoActiveSession MusicEmptyState hint.
                text = stringResource(R.string.music_connect_hint),
                style = MaterialTheme.typography.cardCtaHint(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 280.dp),
            )
        }
    }

@Composable
internal fun MusicEmptyState() =
    Column(
        modifier = Modifier.fillMaxSize().padding(FemtoDimens.CardPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Mockup `.music-card.empty` = `grid-template-rows: 1fr auto auto 1fr`
        // with `align-content: space-between` and `.empty-icon { align-self:
        // end }`. The two flexible tracks seat the icon/title/description
        // cluster at/slightly above the vertical centre, and the icon hugs the
        // title (its row ends flush against the title row). The taller top
        // weight nudges the cluster just above centre.
        Box(modifier = Modifier.weight(1.1f))
        Icon(
            imageVector = Lucide.Music,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(36.dp),
        )
        Box(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.music_nothing_playing),
            style = MaterialTheme.typography.cardCta(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Box(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.music_nothing_hint),
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontSize = FemtoDimens.GlanceTextSize,
                    lineHeight = 18.sp,
                ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 280.dp),
        )
        Box(modifier = Modifier.weight(0.9f))
    }
