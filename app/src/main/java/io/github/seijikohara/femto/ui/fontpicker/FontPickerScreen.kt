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
import io.github.seijikohara.femto.data.fonts.FontSlot
import io.github.seijikohara.femto.data.fonts.FontSource
import io.github.seijikohara.femto.data.fonts.GoogleFontFamily
import io.github.seijikohara.femto.data.fonts.SystemFontFamily
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark

private const val SYSTEM_KEY = "__system__"
private const val INSTALLED_HEADER_KEY = "__installed_header__"
private const val CATALOG_HEADER_KEY = "__catalog_header__"
private const val STATUS_KEY = "__status__"

/**
 * Font picker for one slot: the system default, this device's own installed
 * fonts, and the full Google Fonts catalog. A search field filters both the
 * installed list and the catalog (CJK-capable entries only for the fallback
 * slot). Selecting an installed font resolves instantly straight from disk —
 * no spinner, no retry hint, unlike a Google family, which downloads in the
 * background (the row shows a spinner until the cache is ready, a check once
 * it backs the live theme, and a retry hint when the download failed).
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
                    selected = uiState.selectedSource == FontSource.SystemDefault,
                    onClick = { onAction(FontPickerAction.Choose(FontSource.SystemDefault)) },
                )
            }
            if (uiState.systemFonts.isNotEmpty()) {
                item(key = INSTALLED_HEADER_KEY) {
                    SectionLabel(stringResource(R.string.font_picker_installed_header))
                }
                items(uiState.systemFonts, key = { "installed:${it.familyName}" }) { installed ->
                    InstalledFontRow(
                        family = installed,
                        selected = uiState.selectedSource == FontSource.SystemFont(installed.familyName),
                        onClick = { onAction(FontPickerAction.Choose(FontSource.SystemFont(installed.familyName))) },
                    )
                }
            }
            item(key = CATALOG_HEADER_KEY) {
                // The catalog is served most-popular-first; name the order so it
                // does not read as arbitrary. Static label — sorting is not
                // user-switchable.
                SectionLabel(stringResource(R.string.font_picker_sort_popular))
            }
            items(uiState.families, key = { it.family }) { family ->
                FontRow(
                    family = family,
                    selected = uiState.selectedSource == FontSource.GoogleFonts(family.family),
                    downloading = family.family in uiState.downloading,
                    failed = family.family in uiState.downloadFailed,
                    onClick = { onAction(FontPickerAction.Choose(FontSource.GoogleFonts(family.family))) },
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
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) = Text(
    text = text,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.padding(horizontal = 4.dp, vertical = 4.dp),
)

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
        FemtoIcon(
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
        FemtoIcon(imageVector = Lucide.Search, contentDescription = null, modifier = Modifier.size(20.dp))
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
                FemtoIcon(
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
    failed = false,
    onClick = onClick,
    modifier = modifier,
)

@Composable
private fun FontRow(
    family: GoogleFontFamily,
    selected: Boolean,
    downloading: Boolean,
    failed: Boolean,
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
    failed = failed,
    onClick = onClick,
    modifier = modifier,
)

// An installed font resolves straight from disk: no download, so no spinner
// and no failure state — unlike FontRow's Google Fonts row.
@Composable
private fun InstalledFontRow(
    family: SystemFontFamily,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = PickerRow(
    title = family.familyName,
    subtitle =
        if (family.supportsCjk) {
            stringResource(R.string.font_picker_installed_subtitle_cjk)
        } else {
            stringResource(R.string.font_picker_installed_subtitle)
        },
    selected = selected,
    downloading = false,
    failed = false,
    onClick = onClick,
    modifier = modifier,
)

// Shared row: a >= MinTouchTarget tap target with the family name, a category /
// CJK subtitle, and a trailing slot that shows the download spinner then the
// selected check. A failed download replaces the subtitle with a retry hint:
// tapping re-chooses the family, which routes through the repository's retry.
@Composable
private fun PickerRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    downloading: Boolean,
    failed: Boolean,
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
            text = if (failed) stringResource(R.string.font_download_failed) else subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
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

        // A failed family may still be the persisted selection, but its face is
        // not live — suppress the check so the row does not claim a font the
        // theme is not rendering.
        selected && !failed -> {
            FemtoIcon(
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
                    selectedSource = FontSource.GoogleFonts("Inter"),
                    families =
                        listOf(
                            GoogleFontFamily("Roboto", "Sans Serif", listOf("latin")),
                            GoogleFontFamily("Inter", "Sans Serif", listOf("latin", "latin-ext")),
                            GoogleFontFamily("Noto Sans JP", "Sans Serif", listOf("latin", "japanese")),
                        ),
                    systemFonts =
                        listOf(
                            SystemFontFamily(
                                familyName = "Roboto Condensed",
                                files = emptyList(),
                                supportsLatin = true,
                                supportsCjk = false,
                            ),
                            SystemFontFamily(
                                familyName = "Noto Sans CJK",
                                files = emptyList(),
                                supportsLatin = true,
                                supportsCjk = true,
                            ),
                        ),
                    downloading = setOf("Roboto"),
                    downloadFailed = setOf("Noto Sans JP"),
                    status = PickerStatus.READY,
                ),
            onAction = {},
            onBack = {},
        )
    }
}
