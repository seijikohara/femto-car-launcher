package io.github.seijikohara.femto.data.fonts

import java.io.File

/**
 * A font family already installed on the device, grouped from the files
 * [installedFontFamilies] enumerates. Android exposes no family-name catalog
 * for `android.graphics.fonts.SystemFonts`, so [familyName] is a best-effort
 * label (an OpenType 'name' table entry, or a cleaned filename) rather than a
 * platform-guaranteed identifier — see [OpenTypeFontName].
 *
 * [supportsLatin] / [supportsCjk] are precomputed once at catalog-build time
 * (probing the family's representative file via [GlyphCoverageChecker]) so
 * the picker can filter per slot on every keystroke without touching
 * `Typeface` / `Paint` again.
 */
internal data class SystemFontFamily(
    val familyName: String,
    val files: List<File>,
    val supportsLatin: Boolean,
    val supportsCjk: Boolean,
) {
    /** True when this family can fill the [slot] the user is choosing for. */
    fun fits(slot: FontSlot): Boolean =
        when (slot) {
            FontSlot.LATIN -> supportsLatin
            FontSlot.CJK -> supportsCjk
        }
}
