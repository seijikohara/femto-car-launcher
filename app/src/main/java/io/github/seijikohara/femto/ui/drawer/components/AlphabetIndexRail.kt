package io.github.seijikohara.femto.ui.drawer.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.ui.drawer.letterIndexForOffset
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark

// Narrow by necessity: a 26+ letter A-Z strip could not otherwise fit beside
// the app grid at any reasonable width. The rail compensates for its
// necessarily sub-floor per-letter target by resolving a tap or drag
// proportionally across the whole rail height (see letterIndexForOffset)
// rather than requiring the finger to land inside one specific letter row —
// the standard launcher fast-scroll interaction, and the same kind of narrow,
// low-precision control the map zoom / follow pill is sanctioned as
// (AGENTS.md#automotive-overrides). Internal (not private): the drawer screen
// insets the app grid/list and the Recent row by this same width so nothing
// scrolls under the rail.
internal val IndexRailWidth = 28.dp

/**
 * Vertical A-Z fast-scroll rail. A tap or drag over it resolves the touch
 * position to one of [letters] (proportionally, via [letterIndexForOffset])
 * and invokes [onSelectLetter] — continuously while dragging, so a single
 * top-to-bottom drag scrubs through the whole alphabet. [letters] holds only
 * the buckets the current list actually has (never a blank A-Z scaffold).
 * [onActiveLetterChange] reports the letter under the finger while pressed
 * and `null` on release, driving a caller-owned floating letter indicator
 * (see [FloatingLetterIndicator]) — the rail itself is too narrow to show the
 * letter at a legible size.
 */
@Composable
internal fun AlphabetIndexRail(
    letters: List<String>,
    onSelectLetter: (String) -> Unit,
    modifier: Modifier = Modifier,
    onActiveLetterChange: (String?) -> Unit = {},
) {
    var heightPx by remember { mutableFloatStateOf(0f) }
    Column(
        modifier =
            modifier
                .width(IndexRailWidth)
                .fillMaxHeight()
                .onSizeChanged { heightPx = it.height.toFloat() }
                .pointerInput(letters) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        selectLetterAt(down.position.y, heightPx, letters, onSelectLetter, onActiveLetterChange)
                        var pressed = true
                        while (pressed) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                pressed = false
                            } else {
                                change.consume()
                                selectLetterAt(
                                    change.position.y,
                                    heightPx,
                                    letters,
                                    onSelectLetter,
                                    onActiveLetterChange,
                                )
                                pressed = change.pressed
                            }
                        }
                        onActiveLetterChange(null)
                    }
                },
        verticalArrangement = Arrangement.Center,
    ) {
        letters.forEach { letter ->
            Text(
                text = letter,
                // titleSmall (18sp) keeps the rail's letters on the automotive body-text
                // floor (AGENTS.md#automotive-overrides) — this drawer is not one of the
                // sanctioned relaxation surfaces. The letters distribute via weight(1f).
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
}

private fun selectLetterAt(
    offsetY: Float,
    heightPx: Float,
    letters: List<String>,
    onSelectLetter: (String) -> Unit,
    onActiveLetterChange: (String?) -> Unit,
) {
    if (letters.isEmpty()) return
    val letter = letters[letterIndexForOffset(offsetY, heightPx, letters.size)]
    onSelectLetter(letter)
    onActiveLetterChange(letter)
}

/**
 * Floating "where am I" bubble shown over the app list while [AlphabetIndexRail]
 * is being dragged — standard fast-scroll launcher feedback, and necessary here
 * since the rail itself (narrow by necessity, see [IndexRailWidth]) has no room
 * to show the current letter at a legible size.
 */
@Composable
internal fun FloatingLetterIndicator(
    letter: String,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier.size(FemtoDimens.IndexBubbleSize),
    shape = CircleShape,
    color = MaterialTheme.colorScheme.primaryContainer,
) {
    Box(contentAlignment = Alignment.Center) {
        Text(
            text = letter,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@PreviewLightDark
@Composable
private fun AlphabetIndexRailPreview() {
    FemtoTheme {
        AlphabetIndexRail(
            letters = listOf("A", "B", "C", "D", "M", "S", "Z", "#"),
            onSelectLetter = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun FloatingLetterIndicatorPreview() {
    FemtoTheme {
        FloatingLetterIndicator(letter = "M")
    }
}
