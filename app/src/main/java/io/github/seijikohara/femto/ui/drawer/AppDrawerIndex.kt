package io.github.seijikohara.femto.ui.drawer

/** Bucket for a label with no leading letter (digits, symbols, emoji). */
internal const val NON_LETTER_SECTION_KEY = "#"

/**
 * The alphabetical bucket [label] belongs in: its uppercased first letter, or
 * [NON_LETTER_SECTION_KEY]. Mirrors the standard Android launcher fast-scroll
 * index (bucket by leading letter; everything else shares one bucket).
 */
internal fun sectionKeyOf(label: String): String =
    label
        .trim()
        .firstOrNull()
        ?.takeIf { it.isLetter() }
        ?.uppercaseChar()
        ?.toString() ?: NON_LETTER_SECTION_KEY

/**
 * The flat-list index of the first item in each alphabetical bucket present in
 * [items] (assumed pre-sorted by [labelOf]), keyed by [sectionKeyOf] and
 * ordered by first appearance. The app grid/list flows continuously with no
 * section-header items breaking it up (a header-per-letter forced a new row
 * at every letter, leaving a car launcher's typically 1-2-app-per-letter list
 * mostly empty space), so a rail letter jumps straight to its first app's own
 * index rather than to a header above it. This one map feeds both the A-Z
 * rail's letter set ([Map.keys]) and its `animateScrollToItem` target
 * ([Map.values]).
 */
internal fun <T> sectionStartIndices(
    items: List<T>,
    labelOf: (T) -> String,
): Map<String, Int> {
    val indices = LinkedHashMap<String, Int>()
    items.forEachIndexed { index, item ->
        indices.getOrPut(sectionKeyOf(labelOf(item))) { index }
    }
    return indices
}

// A drag that has not yet moved past the rail's bottom edge should still
// resolve to the last letter, not roll over past it.
private const val ALMOST_ONE = 0.999999f

/**
 * Map a touch/drag offset along the fast-scroll rail to a letter index: the
 * rail scrubs proportionally across its full height rather than requiring the
 * finger to land inside one specific (necessarily small, per
 * CLAUDE.md#automotive-overrides) letter row — the standard launcher
 * fast-scroll interaction. Degrades to index 0 for a not-yet-measured
 * (zero-height) rail or an empty letter set.
 */
internal fun letterIndexForOffset(
    offsetY: Float,
    heightPx: Float,
    letterCount: Int,
): Int {
    if (letterCount <= 0 || heightPx <= 0f) return 0
    val fraction = (offsetY / heightPx).coerceIn(0f, ALMOST_ONE)
    return (fraction * letterCount).toInt().coerceIn(0, letterCount - 1)
}
