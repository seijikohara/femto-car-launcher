package io.github.seijikohara.femto.data.fonts

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

// Robolectric hands each test method a fresh Application, but the
// fontDataStore delegate is a process-wide singleton bound to whichever
// Application first touched it — so every test below resets the store first
// to avoid disagreeing with whatever an earlier test method left behind
// (mirrors DisplayPreferencesTest). The unprefixed-legacy-value migration
// itself is covered at the pure-function level by FontSourceTest
// (FontPreferences.selection does nothing more than pass the raw stored
// String through FontSource.fromPersisted).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FontPreferencesTest {
    @Test
    fun `an empty store reads the system default for both slots`() =
        runTest {
            val store = FontPreferences(ApplicationProvider.getApplicationContext())
            store.resetToDefaults()

            assertEquals(FontSelection.System, store.selection.first())
        }

    @Test
    fun `setSource round-trips a Google Fonts and a system font selection per slot`() =
        runTest {
            val store = FontPreferences(ApplicationProvider.getApplicationContext())
            store.resetToDefaults()

            store.setSource(FontSlot.LATIN, FontSource.GoogleFonts("Inter"))
            store.setSource(FontSlot.CJK, FontSource.SystemFont("Noto Sans CJK"))

            val selection = store.selection.first()
            assertEquals(FontSource.GoogleFonts("Inter"), selection.latin)
            assertEquals(FontSource.SystemFont("Noto Sans CJK"), selection.cjk)
        }

    @Test
    fun `setSource with SystemDefault clears a previously chosen slot`() =
        runTest {
            val store = FontPreferences(ApplicationProvider.getApplicationContext())
            store.resetToDefaults()
            store.setSource(FontSlot.LATIN, FontSource.GoogleFonts("Inter"))

            store.setSource(FontSlot.LATIN, FontSource.SystemDefault)

            assertEquals(FontSource.SystemDefault, store.selection.first().latin)
        }

    @Test
    fun `resetToDefaults clears both slots back to the system default`() =
        runTest {
            val store = FontPreferences(ApplicationProvider.getApplicationContext())
            store.setSource(FontSlot.LATIN, FontSource.GoogleFonts("Inter"))
            store.setSource(FontSlot.CJK, FontSource.SystemFont("Noto Sans CJK"))

            store.resetToDefaults()

            assertEquals(FontSelection.System, store.selection.first())
        }
}
