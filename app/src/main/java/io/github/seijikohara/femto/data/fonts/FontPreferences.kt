package io.github.seijikohara.femto.data.fonts

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.seijikohara.femto.data.common.catchIoAsDefaults
import io.github.seijikohara.femto.data.common.editOrLog
import io.github.seijikohara.femto.data.display.DisplayPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.fontDataStore: DataStore<Preferences> by preferencesDataStore(name = "font_preferences")

private const val TAG = "FontPreferences"

/**
 * DataStore-backed accessor for the user's [FontSelection].
 *
 * Each slot stores a Google Fonts family name; an absent key means the system
 * font (the default), so a fresh install and a reset both fall back to the
 * platform typeface with no download.
 */
internal class FontPreferences(
    private val context: Context,
) {
    val selection: Flow<FontSelection> =
        context.fontDataStore.data
            .catchIoAsDefaults(TAG)
            .map { prefs ->
                FontSelection(latinFamily = prefs[LATIN_KEY], cjkFamily = prefs[CJK_KEY])
            }

    /** Persist [family] for [slot]; a null family clears the slot to system. */
    suspend fun setFamily(
        slot: FontSlot,
        family: String?,
    ) {
        val key = keyFor(slot)
        context.fontDataStore.editOrLog(TAG) { prefs ->
            if (family == null) prefs.remove(key) else prefs[key] = family
        }
    }

    // Clearing the keys makes the read path fall back to the system font,
    // mirroring DisplayPreferences.resetToDefaults.
    suspend fun resetToDefaults() {
        context.fontDataStore.editOrLog(TAG) { it.clear() }
    }

    private fun keyFor(slot: FontSlot): Preferences.Key<String> =
        when (slot) {
            FontSlot.LATIN -> LATIN_KEY
            FontSlot.CJK -> CJK_KEY
        }

    private companion object {
        val LATIN_KEY = stringPreferencesKey("latin_family")
        val CJK_KEY = stringPreferencesKey("cjk_family")
    }
}
