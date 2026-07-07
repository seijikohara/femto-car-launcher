package io.github.seijikohara.femto.ui.drawer.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.seijikohara.femto.ui.drawer.letterIndexForOffset
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark

// Narrow by necessity: a 26+ letter A-Z strip could not otherwise fit beside
// the app grid at any reasonable width. The rail compensates for its
// necessarily sub-floor per-letter target by resolving a tap or drag
// proportionally across the whole rail height (see letterIndexForOffset)
// rather than requiring the finger to land inside one specific letter row —
// the standard launcher fast-scroll interaction, and the same kind of narrow,
// low-precision control the map zoom / follow pill is sanctioned as
// (CLAUDE.md#automotive-overrides).
private val IndexRailWidth = 28.dp

/**
 * Vertical A-Z fast-scroll rail. A tap or drag over it resolves the touch
 * position to one of [letters] (proportionally, via [letterIndexForOffset])
 * and invokes [onSelectLetter] — continuously while dragging, so a single
 * top-to-bottom drag scrubs through the whole alphabet. [letters] holds only
 * the buckets the current list actually has (never a blank A-Z scaffold).
 */
@Composable
internal fun AlphabetIndexRail(
    letters: List<String>,
    onSelectLetter: (String) -> Unit,
    modifier: Modifier = Modifier,
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
                        selectLetterAt(down.position.y, heightPx, letters, onSelectLetter)
                        var pressed = true
                        while (pressed) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                pressed = false
                            } else {
                                change.consume()
                                selectLetterAt(change.position.y, heightPx, letters, onSelectLetter)
                                pressed = change.pressed
                            }
                        }
                    }
                },
        verticalArrangement = Arrangement.Center,
    ) {
        letters.forEach { letter ->
            Text(
                text = letter,
                style = MaterialTheme.typography.labelSmall,
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
) {
    if (letters.isEmpty()) return
    onSelectLetter(letters[letterIndexForOffset(offsetY, heightPx, letters.size)])
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
