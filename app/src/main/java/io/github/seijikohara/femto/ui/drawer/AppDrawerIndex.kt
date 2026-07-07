package io.github.seijikohara.femto.ui.drawer

/**
 * One entry in the drawer's alphabetically-sectioned app list: either a
 * full-span section [Header] (its bucket key, e.g. "A", or [NON_LETTER_SECTION_KEY]
 * for a label with no leading letter) or a launchable [App]. Shared by the
 * grid and list layouts so both render the same header + fast-scroll
 * structure over the same item list.
 */
internal sealed interface DrawerListEntry<out T> {
    data class Header(
        val key: String,
    ) : DrawerListEntry<Nothing>

    data class App<T>(
        val item: T,
    ) : DrawerListEntry<T>
}

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
 * Interleave alphabetical section headers into [items] (assumed pre-sorted by
 * [labelOf]): one [DrawerListEntry.Header] per run of a shared [sectionKeyOf]
 * bucket, immediately before that bucket's first item. A bucket with no items
 * never appears — the index only ever shows letters the list actually has.
 */
internal fun <T> withSectionHeaders(
    items: List<T>,
    labelOf: (T) -> String,
): List<DrawerListEntry<T>> {
    val entries = mutableListOf<DrawerListEntry<T>>()
    var currentKey: String? = null
    for (item in items) {
        val key = sectionKeyOf(labelOf(item))
        if (key != currentKey) {
            entries += DrawerListEntry.Header(key)
            currentKey = key
        }
        entries += DrawerListEntry.App(item)
    }
    return entries
}

/**
 * The section keys present in [items], in the order [withSectionHeaders]
 * would emit their headers — the letters the fast-scroll rail renders.
 */
internal fun <T> availableSectionKeys(
    items: List<T>,
    labelOf: (T) -> String,
): List<String> = items.map { sectionKeyOf(labelOf(it)) }.distinct()

/**
 * The flattened-list index of [targetKey]'s header, or null when that section
 * is not present. Feeds the fast-scroll rail's `LazyGridState` /
 * `LazyListState` `.animateScrollToItem(...)` target.
 */
internal fun <T> headerIndexOf(
    entries: List<DrawerListEntry<T>>,
    targetKey: String,
): Int? = entries.indexOfFirst { it is DrawerListEntry.Header && it.key == targetKey }.takeIf { it >= 0 }

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
