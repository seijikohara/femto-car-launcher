package io.github.seijikohara.femto.ui.assistant

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Sparkles
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.VoiceState
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark

/**
 * Voice-assistant launch options that map one-to-one to the system intents the
 * host fires (`ACTION_ASSIST` / `ACTION_VOICE_COMMAND` / `ACTION_WEB_SEARCH`).
 * These are the fallback path: the system assistant opens as a separate
 * activity, used when in-launcher recognition is unavailable or the user
 * prefers it.
 */
internal enum class AssistantOption {
    ASSISTANT,
    VOICE_COMMAND,
    VOICE_SEARCH,
}

/**
 * Assistant sheet content. When the device has a speech recognizer the launcher
 * captures voice in-process — a mic button, a live partial transcript, and a
 * recognized-text result the user dispatches as a search without leaving the
 * launcher. The system-intent delegation rows sit below as a secondary path,
 * and become the whole sheet when no recognizer is available.
 */
@Composable
internal fun AssistantScreen(
    uiState: AssistantUiState,
    onMicTap: () -> Unit,
    onReset: () -> Unit,
    onSubmitQuery: (String) -> Unit,
    onLaunchOption: (AssistantOption) -> Unit,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.background,
) {
    Column(
        modifier = Modifier.padding(FemtoDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(FemtoDimens.GridGutter),
    ) {
        Text(
            text = stringResource(R.string.assistant_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (uiState.voice != VoiceState.Unavailable) {
            VoiceSurface(
                voice = uiState.voice,
                onMicTap = onMicTap,
                onReset = onReset,
                onSubmitQuery = onSubmitQuery,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = stringResource(R.string.assistant_delegate_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DelegationRows(onLaunchOption = onLaunchOption)
    }
}

@Composable
private fun VoiceSurface(
    voice: VoiceState,
    onMicTap: () -> Unit,
    onReset: () -> Unit,
    onSubmitQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    val listening = voice is VoiceState.Listening
    MicButton(listening = listening, onClick = onMicTap)

    val prompt =
        when (voice) {
            is VoiceState.Listening -> voice.partial.ifBlank { stringResource(R.string.assistant_voice_listening) }
            is VoiceState.Result -> voice.text
            is VoiceState.Failed -> stringResource(voice.messageRes)
            else -> stringResource(R.string.assistant_voice_idle)
        }
    Text(
        text = prompt,
        style = MaterialTheme.typography.bodyLarge,
        color =
            if (voice is VoiceState.Failed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onBackground
            },
        textAlign = TextAlign.Center,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )

    if (voice is VoiceState.Result) {
        PrimaryAction(
            icon = Lucide.Search,
            label = stringResource(R.string.assistant_voice_search),
            onClick = { onSubmitQuery(voice.text) },
        )
        Text(
            text = stringResource(R.string.assistant_voice_again),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onReset)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun MicButton(
    listening: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A gentle pulse while listening signals the mic is live without text.
    val transition = rememberInfiniteTransition(label = "mic-pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (listening) 1.12f else 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "mic-scale",
    )
    Box(
        modifier =
            modifier
                .size(FemtoDimens.MinTouchTarget + 24.dp)
                .scale(if (listening) pulse else 1f)
                .clip(CircleShape)
                .background(
                    if (listening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                ).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Lucide.Mic,
            contentDescription = stringResource(R.string.assistant_voice_mic),
            tint = if (listening) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(32.dp),
        )
    }
}

@Composable
private fun PrimaryAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier =
        modifier
            .defaultMinSize(minHeight = FemtoDimens.MinTouchTarget)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(FemtoDimens.InlineIconSize),
    )
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onPrimary,
    )
}

@Composable
private fun DelegationRows(
    onLaunchOption: (AssistantOption) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(FemtoDimens.GridGutter),
) {
    AssistantOptionRow(
        option = AssistantOption.ASSISTANT,
        icon = Lucide.Sparkles,
        label = stringResource(R.string.assistant_option_assistant),
        onLaunchOption = onLaunchOption,
    )
    AssistantOptionRow(
        option = AssistantOption.VOICE_COMMAND,
        icon = Lucide.Mic,
        label = stringResource(R.string.assistant_option_voice_command),
        onLaunchOption = onLaunchOption,
    )
    AssistantOptionRow(
        option = AssistantOption.VOICE_SEARCH,
        icon = Lucide.Search,
        label = stringResource(R.string.assistant_option_voice_search),
        onLaunchOption = onLaunchOption,
    )
}

@Composable
private fun AssistantOptionRow(
    option: AssistantOption,
    icon: ImageVector,
    label: String,
    onLaunchOption: (AssistantOption) -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier =
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = FemtoDimens.MinTouchTarget)
            .clickable { onLaunchOption(option) }
            .padding(horizontal = FemtoDimens.GridGutter),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(FemtoDimens.GridGutter),
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.size(FemtoDimens.InlineIconSize),
    )
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@PreviewLightDark
@Composable
private fun AssistantScreenIdlePreview() {
    FemtoTheme {
        AssistantScreen(
            uiState = AssistantUiState(voice = VoiceState.Idle),
            onMicTap = {},
            onReset = {},
            onSubmitQuery = {},
            onLaunchOption = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun AssistantScreenResultPreview() {
    FemtoTheme {
        AssistantScreen(
            uiState = AssistantUiState(voice = VoiceState.Result("nearest coffee shop")),
            onMicTap = {},
            onReset = {},
            onSubmitQuery = {},
            onLaunchOption = {},
        )
    }
}
