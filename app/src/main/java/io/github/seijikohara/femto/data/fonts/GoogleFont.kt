package io.github.seijikohara.femto.data.fonts

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
 * The user's font choice for both slots. [FontSource.SystemDefault] means "use
 * the system font" — no download, no cache entry, no on-disk lookup.
 */
internal data class FontSelection(
    val latin: FontSource = FontSource.SystemDefault,
    val cjk: FontSource = FontSource.SystemDefault,
) {
    fun sourceFor(slot: FontSlot): FontSource =
        when (slot) {
            FontSlot.LATIN -> latin
            FontSlot.CJK -> cjk
        }

    fun with(
        slot: FontSlot,
        source: FontSource,
    ): FontSelection =
        when (slot) {
            FontSlot.LATIN -> copy(latin = source)
            FontSlot.CJK -> copy(cjk = source)
        }

    /**
     * Every distinct Google Fonts family the selection references, for cache
     * retention. System-installed families are deliberately excluded — their
     * files live outside `filesDir/google_fonts/` and [FontCache.evictExcept]
     * must never be asked to keep (or evict) them.
     */
    val googleFamilies: Set<String>
        get() =
            setOfNotNull(
                (latin as? FontSource.GoogleFonts)?.family,
                (cjk as? FontSource.GoogleFonts)?.family,
            )

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
