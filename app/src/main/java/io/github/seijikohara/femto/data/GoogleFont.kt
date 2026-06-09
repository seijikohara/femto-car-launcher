package io.github.seijikohara.femto.data

import kotlinx.serialization.Serializable

/**
 * One typeface slot the user configures independently. The Latin face renders
 * alphanumerics and Western text; the CJK face is the multibyte fallback that
 * supplies glyphs the Latin face lacks (Japanese / Korean / Chinese).
 */
internal enum class FontSlot {
    LATIN,
    CJK,
}

/**
 * The user's font choice for both slots. A null family means "use the system
 * font" (the default) — no download, no cache entry.
 */
internal data class FontSelection(
    val latinFamily: String? = null,
    val cjkFamily: String? = null,
) {
    fun familyFor(slot: FontSlot): String? =
        when (slot) {
            FontSlot.LATIN -> latinFamily
            FontSlot.CJK -> cjkFamily
        }

    fun with(
        slot: FontSlot,
        family: String?,
    ): FontSelection =
        when (slot) {
            FontSlot.LATIN -> copy(latinFamily = family)
            FontSlot.CJK -> copy(cjkFamily = family)
        }

    /** Every distinct family the selection references, for cache retention. */
    val families: Set<String>
        get() = setOfNotNull(latinFamily, cjkFamily)

    companion object {
        val System = FontSelection()
    }
}

/**
 * A Google Fonts family as listed by the public catalog metadata. Only the
 * fields the picker needs are decoded; the catalog carries far more.
 *
 * [subsets] drives the CJK-capable filter: a family advertising the `japanese`,
 * `korean`, or a `chinese-*` subset can serve as a multibyte fallback. The
 * catalog sorts by [popularity] (rank 1 = most popular) so the picker can lead
 * with the faces users actually reach for.
 */
@Serializable
internal data class GoogleFontFamily(
    val family: String,
    val category: String = "",
    val subsets: List<String> = emptyList(),
    val popularity: Int = Int.MAX_VALUE,
) {
    val supportsJapanese: Boolean get() = "japanese" in subsets

    val supportsCjk: Boolean
        get() =
            subsets.any { subset ->
                subset == "japanese" || subset == "korean" || subset.startsWith("chinese")
            }

    /** True when this family can fill the [slot] the user is choosing for. */
    fun fits(slot: FontSlot): Boolean =
        when (slot) {
            FontSlot.LATIN -> true
            FontSlot.CJK -> supportsCjk
        }
}
