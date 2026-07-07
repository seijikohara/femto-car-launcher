package io.github.seijikohara.femto.data.fonts

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FontSourceTest {
    @Test
    fun `a null persisted value is the system default`() {
        assertEquals(FontSource.SystemDefault, FontSource.fromPersisted(null))
    }

    @Test
    fun `the system default persists as null`() {
        assertNull(FontSource.SystemDefault.toPersisted())
    }

    @Test
    fun `a Google Fonts source round-trips through its prefixed encoding`() {
        val source = FontSource.GoogleFonts("Noto Sans JP")
        val persisted = source.toPersisted()
        assertEquals("google:Noto Sans JP", persisted)
        assertEquals(source, FontSource.fromPersisted(persisted))
    }

    @Test
    fun `a system font source round-trips through its prefixed encoding`() {
        val source = FontSource.SystemFont("Roboto Condensed")
        val persisted = source.toPersisted()
        assertEquals("system:Roboto Condensed", persisted)
        assertEquals(source, FontSource.fromPersisted(persisted))
    }

    @Test
    fun `an unprefixed legacy value is read as a Google Fonts family`() {
        // Every value FontPreferences wrote before this feature was a bare Google
        // Fonts family name (no colon prefix) — an existing user's selection must
        // keep resolving to the same family after the upgrade.
        assertEquals(FontSource.GoogleFonts("Roboto"), FontSource.fromPersisted("Roboto"))
        assertEquals(FontSource.GoogleFonts("M PLUS 2"), FontSource.fromPersisted("M PLUS 2"))
    }

    @Test
    fun `displayNameOrNull surfaces the right label per source`() {
        assertNull(FontSource.SystemDefault.displayNameOrNull)
        assertEquals("Inter", FontSource.GoogleFonts("Inter").displayNameOrNull)
        assertEquals("Roboto Condensed", FontSource.SystemFont("Roboto Condensed").displayNameOrNull)
    }
}
