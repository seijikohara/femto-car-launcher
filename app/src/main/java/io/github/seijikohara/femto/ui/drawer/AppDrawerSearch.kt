package io.github.seijikohara.femto.ui.drawer

import io.github.seijikohara.femto.data.apps.DrawerLayout

/**
 * Filter [items] by [query] against [labelOf], ranking prefix matches before
 * substring matches (both case-insensitive, both keeping input order) so one
 * typed letter surfaces the apps starting with it. A blank query returns
 * [items] unchanged.
 */
internal fun <T> filterAndRank(
    items: List<T>,
    query: String,
    labelOf: (T) -> String,
): List<T> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return items
    val (prefix, substring) =
        items
            .filter { labelOf(it).contains(trimmed, ignoreCase = true) }
            .partition { labelOf(it).startsWith(trimmed, ignoreCase = true) }
    return prefix + substring
}

/**
 * Return the layout the drawer should render: the list layout while a query is
 * active (labels are the primary signal when searching), otherwise the
 * [persisted] preference. Never writes the persisted value.
 */
internal fun effectiveLayout(
    persisted: DrawerLayout,
    query: String,
): DrawerLayout = if (query.isBlank()) persisted else DrawerLayout.LIST

/**
 * Resolve [order] (component/package keys) against [items] via [keyOf],
 * preserving [order]'s sequence and silently dropping a key with no matching
 * item. Shared by the Pinned dock and the Recent row — both can reference an
 * app uninstalled since it was pinned / launched.
 */
internal fun <T> resolveByOrder(
    items: List<T>,
    order: List<String>,
    keyOf: (T) -> String,
): List<T> {
    val byKey = items.associateBy(keyOf)
    return order.mapNotNull { byKey[it] }
}
