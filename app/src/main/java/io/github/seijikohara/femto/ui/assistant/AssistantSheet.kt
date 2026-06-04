package io.github.seijikohara.femto.ui.assistant

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Sparkles
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark

/**
 * Voice-assistant launch options that map one-to-one to the system intents the
 * host fires (`ACTION_ASSIST` / `ACTION_VOICE_COMMAND` / `ACTION_WEB_SEARCH`).
 * The system assistant opens as a separate activity — it cannot be embedded in
 * the launcher — so the sheet's sole job is to pick which intent to fire.
 */
internal enum class AssistantOption {
    ASSISTANT,
    VOICE_COMMAND,
    VOICE_SEARCH,
}

/**
 * Voice-assistant options presented as a Material 3 [ModalBottomSheet] over the
 * dashboard, mirroring [io.github.seijikohara.femto.ui.drawer.AppDrawerSheet].
 *
 * The launcher keeps the dashboard composed underneath, so the sheet reads as an
 * overlay (slide-up + scrim) the user dismisses by swiping down or tapping the
 * scrim. The sheet height keys off the viewport via
 * [FemtoDimens.DrawerSheetHeightFraction], never a specific device, so the
 * dashboard stays visible behind it. Rendering is delegated to
 * [AssistantSheetContent], which carries the @PreviewLightDark coverage.
 *
 * No standalone preview: a [ModalBottomSheet] renders in a popup window that
 * Compose previews do not capture, so [AssistantSheetContent]'s own
 * @PreviewLightDark covers the sheet content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AssistantSheet(
    onLaunchOption: (AssistantOption) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetHeight = (LocalConfiguration.current.screenHeightDp * FemtoDimens.DrawerSheetHeightFraction).dp
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(sheetHeight),
        ) {
            AssistantSheetContent(onLaunchOption = onLaunchOption)
        }
    }
}

@Composable
internal fun AssistantSheetContent(
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
private fun AssistantSheetContentPreview() {
    FemtoTheme {
        AssistantSheetContent(onLaunchOption = {})
    }
}
