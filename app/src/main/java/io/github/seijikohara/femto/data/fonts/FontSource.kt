package io.github.seijikohara.femto.data.fonts

// Persisted-value prefixes for FontSource.toPersisted / fromPersisted. A value
// with neither prefix predates this feature: FontPreferences only ever wrote a
// bare Google Fonts family name (or omitted the key for the system default),
// so an unprefixed string is read as a legacy GoogleFonts selection.
private const val GOOGLE_PREFIX = "google:"
private const val SYSTEM_PREFIX = "system:"

/**
 * Where a font slot's typeface comes from: the head-unit's built-in font, a
 * Google Fonts family downloaded on demand and cached to disk, or a family
 * already installed on the device (enumerated by [installedFontFamilies],
 * resolved straight from its on-disk file — no download, no cache entry).
 */
internal sealed interface FontSource {
    data object SystemDefault : FontSource

    data class GoogleFonts(
        val family: String,
    ) : FontSource

    data class SystemFont(
        val familyName: String,
    ) : FontSource

    /** The name to surface in the picker / Settings row; null for the system default. */
    val displayNameOrNull: String?
        get() =
            when (this) {
                SystemDefault -> null
                is GoogleFonts -> family
                is SystemFont -> familyName
            }

    /** Encode for persistence; null clears the slot (system default). */
    fun toPersisted(): String? =
        when (this) {
            SystemDefault -> null
            is GoogleFonts -> "$GOOGLE_PREFIX$family"
            is SystemFont -> "$SYSTEM_PREFIX$familyName"
        }

    companion object {
        /**
         * Parse a persisted slot value. A null value (absent key) is the system
         * default; `google:` / `system:` prefixes select the matching source; an
         * unprefixed value is the pre-migration shape (a bare Google Fonts family
         * name) and is read as [GoogleFonts] so an existing selection keeps working.
         */
        fun fromPersisted(value: String?): FontSource =
            when {
                value == null -> SystemDefault
                value.startsWith(GOOGLE_PREFIX) -> GoogleFonts(value.removePrefix(GOOGLE_PREFIX))
                value.startsWith(SYSTEM_PREFIX) -> SystemFont(value.removePrefix(SYSTEM_PREFIX))
                else -> GoogleFonts(value)
            }
    }
}
