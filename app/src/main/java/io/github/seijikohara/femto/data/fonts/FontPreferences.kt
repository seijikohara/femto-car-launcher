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
 * DataStore-backed [FontSelectionStore].
 *
 * Each slot stores a [FontSource] encoded via [FontSource.toPersisted]; an
 * absent key means the system font (the default), so a fresh install and a
 * reset both fall back to the platform typeface with no download. Values
 * written before the system-font source existed carry no prefix — read back
 * as a legacy [FontSource.GoogleFonts] by [FontSource.fromPersisted], so an
 * existing selection survives the upgrade unchanged.
 */
internal class FontPreferences(
    private val context: Context,
) : FontSelectionStore {
    override val selection: Flow<FontSelection> =
        context.fontDataStore.data
            .catchIoAsDefaults(TAG)
            .map { prefs ->
                FontSelection(
                    latin = FontSource.fromPersisted(prefs[LATIN_KEY]),
                    cjk = FontSource.fromPersisted(prefs[CJK_KEY]),
                )
            }

    /** Persist [source] for [slot]; [FontSource.SystemDefault] clears the slot. */
    override suspend fun setSource(
        slot: FontSlot,
        source: FontSource,
    ) {
        val key = keyFor(slot)
        val persisted = source.toPersisted()
        context.fontDataStore.editOrLog(TAG) { prefs ->
            if (persisted == null) prefs.remove(key) else prefs[key] = persisted
        }
    }

    // Clearing the keys makes the read path fall back to the system font,
    // mirroring DisplayPreferences.resetToDefaults.
    override suspend fun resetToDefaults() {
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
