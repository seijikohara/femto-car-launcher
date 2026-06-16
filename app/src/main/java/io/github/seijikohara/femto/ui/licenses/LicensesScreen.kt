package io.github.seijikohara.femto.ui.licenses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark

/**
 * Open-source attribution surface: the auto-collected library list (tap a row to
 * read the full license body), plus curated map-data and font credits. The full
 * license text takes the sanctioned GlanceTextSize/monospace relaxation — it is
 * dense reference text in a Settings sub-sheet, not dashboard body copy.
 */
@Composable
internal fun LicensesScreen(
    uiState: LicensesUiState,
    onAction: (LicensesAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = uiState.selected
    if (selected != null) {
        LicenseDetail(
            item = selected,
            onBack = { onAction(LicensesAction.ClearSelection) },
            modifier = modifier,
        )
    } else {
        LicensesList(
            uiState = uiState,
            onSelect = { onAction(LicensesAction.Select(it)) },
            onBack = onBack,
            modifier = modifier,
        )
    }
}

// The library list runs to ~150 rows. A LazyColumn with stable keys (the
// AboutLibraries uniqueId) composes only the visible rows instead of all of
// them up front; the curated map/font credits ride along as fixed items.
@Composable
private fun LicensesList(
    uiState: LicensesUiState,
    onSelect: (LicenseItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) = LazyColumn(
    modifier =
        modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
) {
    item { Header(title = stringResource(R.string.licenses_title), onBack = onBack) }
    item { SectionTitle(stringResource(R.string.licenses_section_libraries)) }
    when {
        uiState.libraries.isNotEmpty() -> {
            items(uiState.libraries, key = { it.id }) { item ->
                LibraryRow(item = item, onClick = { onSelect(item) })
            }
        }

        uiState.isLoading -> {
            item { BodyText(stringResource(R.string.licenses_loading)) }
        }

        else -> {
            item { BodyText(stringResource(R.string.licenses_unavailable)) }
        }
    }
    item {
        Section(title = stringResource(R.string.licenses_section_map_data)) {
            // Reuse the on-map attribution strings (their SSOT) and add only the
            // license-name note here, rather than restating the provider list.
            BodyText(stringResource(R.string.map_attribution))
            BodyText(stringResource(R.string.map_attribution_terrain))
            BodyText(stringResource(R.string.licenses_map_data_note))
        }
    }
    item {
        Section(title = stringResource(R.string.licenses_section_fonts)) {
            BodyText(stringResource(R.string.licenses_fonts_note))
        }
    }
}

@Composable
private fun LicenseDetail(
    item: LicenseItem,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier =
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
) {
    Header(title = item.name, onBack = onBack)
    item.licenseName?.let { name ->
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    item.url?.let { url ->
        Text(
            text = url,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        text = item.licenseText ?: stringResource(R.string.licenses_unavailable),
        // Dense legal reference text in a sub-sheet: the same GlanceTextSize +
        // monospace relaxation the diagnostics log tail uses.
        style =
            MaterialTheme.typography.bodySmall.copy(
                fontSize = FemtoDimens.GlanceTextSize,
                fontFamily = FontFamily.Monospace,
            ),
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun Header(
    title: String,
    onBack: () -> Unit,
) = Row(verticalAlignment = Alignment.CenterVertically) {
    IconButton(onClick = onBack, modifier = Modifier.size(FemtoDimens.MinTouchTarget)) {
        Icon(
            imageVector = Lucide.ArrowLeft,
            contentDescription = stringResource(R.string.settings_back),
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(28.dp),
        )
    }
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) = Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    SectionTitle(title)
    content()
}

@Composable
private fun SectionTitle(title: String) =
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )

@Composable
private fun LibraryRow(
    item: LicenseItem,
    onClick: () -> Unit,
) = Row(
    modifier =
        Modifier
            .fillMaxWidth()
            .heightIn(min = FemtoDimens.MinTouchTarget)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        item.licenseName?.let { name ->
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Icon(
        imageVector = Lucide.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(FemtoDimens.InlineIconSize),
    )
}

@Composable
private fun BodyText(text: String) =
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

@PreviewLightDark
@Composable
private fun LicensesScreenPreview() {
    FemtoTheme {
        LicensesScreen(
            uiState =
                LicensesUiState(
                    isLoading = false,
                    libraries =
                        listOf(
                            LicenseItem(
                                id = "androidx.compose",
                                name = "Jetpack Compose",
                                licenseName = "Apache-2.0",
                                licenseText = "Apache License, Version 2.0…",
                                url = "https://developer.android.com/jetpack/compose",
                            ),
                            LicenseItem(
                                id = "org.maplibre:maplibre-gl-js",
                                name = "MapLibre GL JS",
                                licenseName = "BSD-3-Clause",
                                licenseText = "BSD 3-Clause License…",
                                url = "https://maplibre.org/",
                            ),
                        ),
                ),
            onAction = {},
            onBack = {},
        )
    }
}
