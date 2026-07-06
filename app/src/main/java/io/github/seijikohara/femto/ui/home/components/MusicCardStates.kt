package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.cardCta
import io.github.seijikohara.femto.ui.theme.cardCtaHint
import io.github.seijikohara.femto.ui.theme.glanceBody

@Composable
internal fun MusicConnectState(onConnect: () -> Unit) =
    Surface(
        onClick = onConnect,
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            // Compact padding matches the Playing state's inset (MusicCard.kt) so
            // the card's edge does not visibly jump when playback starts / stops.
            modifier = Modifier.fillMaxSize().padding(FemtoDimens.CardPaddingCompact),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FemtoIcon(
                imageVector = Lucide.Music,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(FemtoDimens.HeroIconSize),
            )
            Box(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.music_connect_cta),
                style = MaterialTheme.typography.cardCta(),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
        // Compact padding matches the Playing state's inset (MusicCard.kt) so
        // the card's edge does not visibly jump when playback starts / stops.
        modifier = Modifier.fillMaxSize().padding(FemtoDimens.CardPaddingCompact),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Mockup `.music-card.empty` = `grid-template-rows: 1fr auto auto 1fr`
        // with `align-content: space-between` and `.empty-icon { align-self:
        // end }`. The two flexible tracks seat the icon/title/description
        // cluster at/slightly above the vertical centre, and the icon hugs the
        // title (its row ends flush against the title row). The taller top
        // weight nudges the cluster just above centre.
        Box(modifier = Modifier.weight(1.1f))
        FemtoIcon(
            imageVector = Lucide.Music,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(FemtoDimens.HeroIconSize),
        )
        Box(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.music_nothing_playing),
            style = MaterialTheme.typography.cardCta(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.music_nothing_hint),
            style = MaterialTheme.typography.glanceBody(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 280.dp),
        )
        Box(modifier = Modifier.weight(0.9f))
    }
