package io.github.seijikohara.femto.ui.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Play
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.cardCta
import io.github.seijikohara.femto.ui.theme.cardCtaHint

@Composable
internal fun MusicConnectState(onConnect: () -> Unit) =
    Surface(
        onClick = onConnect,
        modifier = Modifier.fillMaxWidth(),
        // Transparent so the card's glass (the outer MusicCard's glassChrome) shows
        // through in every state — the same translucency the Playing state has. An
        // opaque colour here would paint over the blurred map behind the card.
        color = Color.Transparent,
    ) {
        Column(
            // Fill the width but wrap the height so the card sizes to its content,
            // matching the Playing state (MusicCard.kt): the card is a content-height
            // child of the floating column, so filling the height would stretch this
            // CTA over the whole column and starve the calendar / weather row. Compact
            // padding matches the Playing state's inset so the card's edge does not
            // visibly jump when playback starts / stops.
            modifier = Modifier.fillMaxWidth().padding(FemtoDimens.CardPaddingCompact),
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
                // matching the equally actionable NoActiveSession Play hint below.
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

/**
 * The "nothing is playing" empty state, with a Play affordance: tapping it
 * dispatches [onPlay] (`HomeAction.PlayDefaultMusic`), which best-effort
 * resumes the last session via a synthetic media key and falls back to
 * launching the user's default music app, so the tap always visibly responds
 * even though the transport command bus itself no-ops with nothing playing
 * (`selectPrimaryController` needs a playing/paused session).
 */
@Composable
internal fun MusicEmptyState(onPlay: () -> Unit) =
    Surface(
        onClick = onPlay,
        modifier = Modifier.fillMaxWidth(),
        // Transparent so the card stays translucent glass when nothing is playing —
        // this state was a plain Column (no surface) before it became tappable, and
        // an opaque colour regressed it to a solid box over the map.
        color = Color.Transparent,
    ) {
        Column(
            // Fill the width but wrap the height so the card sizes to its content,
            // matching the Playing state (MusicCard.kt): the card is a content-height
            // child of the floating column, so filling the height would stretch this
            // affordance over the whole column and starve the calendar / weather row.
            // Compact padding matches the Playing state's inset so the card's edge
            // does not visibly jump when playback starts / stops. The icon / title /
            // hint cluster stacks from the top — the mockup's space-between seating
            // needed a filled height, which the content-wrapping card no longer has.
            modifier = Modifier.fillMaxWidth().padding(FemtoDimens.CardPaddingCompact),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FemtoIcon(
                imageVector = Lucide.Play,
                contentDescription = null,
                // Primary tint (mirroring MusicConnectState's icon), not the old
                // muted 60%-alpha Music glyph: the state is now clickable, so the
                // glyph itself should read as the actionable Play affordance.
                tint = MaterialTheme.colorScheme.primary,
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
                // Actionable copy the user must read to unlock the affordance:
                // clear the head-unit glance floor (CLAUDE.md#automotive-overrides),
                // matching MusicConnectState's hint now that this state is tappable.
                text = stringResource(R.string.music_nothing_hint),
                style = MaterialTheme.typography.cardCtaHint(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 280.dp),
            )
        }
    }
