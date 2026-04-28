package io.github.seijikohara.femto.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.seijikohara.femto.ui.theme.FontTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.fontDataStore: DataStore<Preferences> by preferencesDataStore(name = "font_preferences")

/**
 * DataStore-backed accessor for the user's selected [FontTheme].
 *
 * MVP exposes no UI to mutate the value, so the read path always emits the
 * default. The setter exists so future settings code can flip the choice
 * without introducing the persistence layer separately.
 */
class FontPreferences(private val context: Context) {

    val fontTheme: Flow<FontTheme> = context.fontDataStore.data.map { prefs ->
        prefs[KEY]?.let { runCatching { FontTheme.valueOf(it) }.getOrNull() } ?: FontTheme.GEIST
    }

    suspend fun setFontTheme(theme: FontTheme) {
        context.fontDataStore.edit { prefs ->
            prefs[KEY] = theme.name
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("font_theme")
    }
}
