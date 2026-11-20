package io.github.seijikohara.femto.ui.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarColors
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.X
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Render the Material 3 search pattern every searchable list shares: a collapsed
 * [SearchBar] holding the query field, and the [ExpandedFullScreenSearchBar] it
 * opens, which shows [content] as the results under the same field.
 *
 * The full-screen variant, not the docked one: the docked view caps its height at
 * two thirds of the window and anchors to the collapsed bar's width, which on a
 * 512 dp landscape head unit leaves room for about four result rows (fewer with
 * the keyboard up), while the full-screen view takes the whole window on both the
 * head unit and a portrait phone.
 *
 * Material 3 only accepts typing inside the expanded view (the collapsed bar
 * blocks the soft keyboard and focusing the field expands it), so the results
 * live in [content], not in the caller's own layout. Collapsing — the back
 * gesture, the field's back button, or the caller calling
 * [SearchBarState.animateToCollapsed] — clears [textFieldState], so the next
 * search starts empty.
 *
 * The expanded view is its own dialog window: [fullscreen] re-applies the
 * launcher's fullscreen choice to it (see [ImmersiveSheetEffect]). Every control
 * meets [FemtoDimens.MinTouchTarget]; Material's 56 dp field height does not, so
 * the field is floored here, and the expanded view takes the collapsed bar's
 * height as its own field height.
 */
@Composable
internal fun ExpandedSearch(
    searchBarState: SearchBarState,
    textFieldState: TextFieldState,
    placeholder: String,
    inputFieldTestTag: String,
    fullscreen: Boolean,
    modifier: Modifier = Modifier,
    collapsedColors: SearchBarColors = SearchBarDefaults.colors(),
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val onCollapse: () -> Unit = { scope.launch { searchBarState.animateToCollapsed() } }
    SearchBar(
        state = searchBarState,
        inputField = {
            SearchInputField(
                searchBarState = searchBarState,
                textFieldState = textFieldState,
                placeholder = placeholder,
                textStyle = textStyle,
                expandedCopy = false,
                onCollapse = onCollapse,
            )
        },
        modifier = modifier,
        colors = collapsedColors,
    )
    ExpandedFullScreenSearchBar(
        state = searchBarState,
        inputField = {
            SearchInputField(
                searchBarState = searchBarState,
                textFieldState = textFieldState,
                placeholder = placeholder,
                textStyle = textStyle,
                expandedCopy = true,
                onCollapse = onCollapse,
                modifier = Modifier.testTag(inputFieldTestTag),
            )
        },
    ) {
        ImmersiveSheetEffect(fullscreen)
        content()
    }
    // React to a collapse that starts, not to the initial collapsed value: the
    // results fade out with the view instead of jumping after it has gone.
    LaunchedEffect(searchBarState, textFieldState) {
        snapshotFlow { searchBarState.targetValue }
            .drop(1)
            .filter { it == SearchBarValue.Collapsed }
            .collect { textFieldState.clearText() }
    }
}

// The query field, used once by each search bar slot. Only the expanded copy
// carries the back and clear buttons: the collapsed copy sits behind the modal
// search view, so buttons there would only be unreachable duplicates.
@Composable
private fun SearchInputField(
    searchBarState: SearchBarState,
    textFieldState: TextFieldState,
    placeholder: String,
    textStyle: TextStyle,
    expandedCopy: Boolean,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    SearchBarDefaults.InputField(
        textFieldState = textFieldState,
        searchBarState = searchBarState,
        // The results filter as the user types; the keyboard's search key only
        // gets the keyboard out of the way of the results.
        onSearch = { keyboard?.hide() },
        modifier = modifier.heightIn(min = FemtoDimens.MinTouchTarget),
        textStyle = textStyle,
        placeholder = { Text(text = placeholder, style = textStyle) },
        leadingIcon = {
            if (expandedCopy) {
                SearchIconButton(
                    icon = Lucide.ArrowLeft,
                    description = stringResource(R.string.search_close),
                    onClick = onCollapse,
                )
            } else {
                FemtoIcon(imageVector = Lucide.Search, contentDescription = null)
            }
        },
        trailingIcon =
            if (expandedCopy && textFieldState.text.isNotEmpty()) {
                {
                    SearchIconButton(
                        icon = Lucide.X,
                        description = stringResource(R.string.search_clear),
                        onClick = { textFieldState.clearText() },
                    )
                }
            } else {
                null
            },
    )
}

@Composable
private fun SearchIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = IconButton(
    onClick = onClick,
    shapes = IconButtonDefaults.shapes(),
    modifier = modifier.size(FemtoDimens.MinTouchTarget),
) {
    FemtoIcon(imageVector = icon, contentDescription = description, modifier = Modifier.size(SearchIconSize))
}

// Matches the settings back arrow and the panel bar icons.
private val SearchIconSize = 28.dp
