package io.github.seijikohara.femto.ui.fontpicker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.X
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.FontSlot
import io.github.seijikohara.femto.data.GoogleFontFamily
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark

private const val SYSTEM_KEY = "__system__"
private const val STATUS_KEY = "__status__"

/**
 * Google Fonts picker for one slot. A search field filters the full catalog
 * (CJK-capable families only for the fallback slot); the leading "system
 * default" entry needs no download. Selecting a family downloads it in the
 * background — the row shows a spinner until the cache is ready and a check
 * once it backs the live theme.
 */
@Composable
internal fun FontPickerScreen(
    uiState: FontPickerUiState,
    onAction: (FontPickerAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(FemtoDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(slot = uiState.slot, onBack = onBack)
        SearchField(
            query = uiState.query,
            onQueryChange = { onAction(FontPickerAction.Search(it)) },
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = SYSTEM_KEY) {
                SystemRow(
                    selected = uiState.selectedFamily == null,
                    onClick = { onAction(FontPickerAction.Choose(null)) },
                )
            }
            items(uiState.families, key = { it.family }) { family ->
                FontRow(
                    family = family,
                    selected = family.family == uiState.selectedFamily,
                    downloading = family.family in uiState.downloading,
                    onClick = { onAction(FontPickerAction.Choose(family.family)) },
                )
            }
            if (uiState.status != PickerStatus.READY || uiState.families.isEmpty()) {
                item(key = STATUS_KEY) {
                    StatusLine(status = uiState.status, hasQuery = uiState.query.isNotBlank())
                }
            }
        }
    }
}

@Composable
private fun Header(
    slot: FontSlot,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    Box(
        modifier =
            Modifier
                .size(FemtoDimens.MinTouchTarget)
                .clip(RoundedCornerShape(percent = 50))
                .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Lucide.ArrowLeft,
            contentDescription = stringResource(R.string.settings_back),
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(28.dp),
        )
    }
    Text(
        text = titleFor(slot),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) = TextField(
    value = query,
    onValueChange = onQueryChange,
    modifier = modifier.fillMaxWidth(),
    singleLine = true,
    leadingIcon = {
        Icon(imageVector = Lucide.Search, contentDescription = null, modifier = Modifier.size(20.dp))
    },
    trailingIcon = {
        if (query.isNotEmpty()) {
            Box(
                modifier =
                    Modifier
                        .size(FemtoDimens.MinTouchTarget)
                        .clip(RoundedCornerShape(percent = 50))
                        .clickable { onQueryChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Lucide.X,
                    contentDescription = stringResource(R.string.font_picker_clear),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    },
    placeholder = { Text(text = stringResource(R.string.font_picker_search_hint)) },
    shape = MaterialTheme.shapes.large,
    colors =
        TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
)

@Composable
private fun SystemRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = PickerRow(
    title = stringResource(R.string.font_picker_system),
    subtitle = stringResource(R.string.font_picker_system_desc),
    selected = selected,
    downloading = false,
    onClick = onClick,
    modifier = modifier,
)

@Composable
private fun FontRow(
    family: GoogleFontFamily,
    selected: Boolean,
    downloading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = PickerRow(
    title = family.family,
    subtitle =
        if (family.supportsCjk) {
            stringResource(R.string.font_picker_subtitle_cjk, family.category)
        } else {
            family.category
        },
    selected = selected,
    downloading = downloading,
    onClick = onClick,
    modifier = modifier,
)

// Shared row: a >= MinTouchTarget tap target with the family name, a category /
// CJK subtitle, and a trailing slot that shows the download spinner then the
// selected check.
@Composable
private fun PickerRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    downloading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier =
        modifier
            .fillMaxWidth()
            .heightIn(min = FemtoDimens.MinTouchTarget)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
) {
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    when {
        downloading -> {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        selected -> {
            Icon(
                imageVector = Lucide.Check,
                contentDescription = stringResource(R.string.font_picker_selected),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(FemtoDimens.InlineIconSize),
            )
        }
    }
}

@Composable
private fun StatusLine(
    status: PickerStatus,
    hasQuery: Boolean,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier.fillMaxWidth().padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    when (status) {
        PickerStatus.LOADING -> {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(R.string.font_picker_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        PickerStatus.ERROR -> {
            Text(
                text = stringResource(R.string.font_picker_error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        PickerStatus.READY -> {
            Text(
                text =
                    if (hasQuery) {
                        stringResource(R.string.font_picker_no_matches)
                    } else {
                        stringResource(R.string.font_picker_empty)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun titleFor(slot: FontSlot): String =
    when (slot) {
        FontSlot.LATIN -> stringResource(R.string.settings_group_font_latin)
        FontSlot.CJK -> stringResource(R.string.settings_group_font_cjk)
    }

@PreviewLightDark
@Preview(name = "Font picker", widthDp = 420, heightDp = 640)
@Composable
private fun FontPickerScreenPreview() {
    FemtoTheme {
        FontPickerScreen(
            uiState =
                FontPickerUiState(
                    slot = FontSlot.LATIN,
                    selectedFamily = "Inter",
                    families =
                        listOf(
                            GoogleFontFamily("Roboto", "Sans Serif", listOf("latin")),
                            GoogleFontFamily("Inter", "Sans Serif", listOf("latin", "latin-ext")),
                            GoogleFontFamily("Noto Sans JP", "Sans Serif", listOf("latin", "japanese")),
                        ),
                    downloading = setOf("Roboto"),
                    status = PickerStatus.READY,
                ),
            onAction = {},
            onBack = {},
        )
    }
}
