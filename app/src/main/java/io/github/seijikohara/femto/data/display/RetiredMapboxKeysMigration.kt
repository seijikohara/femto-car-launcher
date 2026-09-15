package io.github.seijikohara.femto.data.display

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * One-shot cleanup for stores written before the Mapbox backend was removed
 * (2026-09). A persisted backend of MAPBOX would already read back as OSM —
 * the read path decodes an unknown enum name as the default — but rewriting it
 * makes the choice explicit, and the orphaned style / traffic / access-token
 * keys are dropped so the user's token stops riding along in the preferences
 * file and its cloud backup. The key names are spelled out here because their
 * constants no longer exist: these are retired names, not live settings, and
 * they must stay out of [DisplayPreferences.ALL_KEYS].
 */
internal object RetiredMapboxKeysMigration : DataMigration<Preferences> {
    private const val RETIRED_BACKEND = "MAPBOX"

    private val retiredKeys: Set<Preferences.Key<*>> =
        setOf(
            stringPreferencesKey("mapbox_style"),
            booleanPreferencesKey("mapbox_traffic"),
            stringPreferencesKey("mapbox_access_token"),
        )

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[DisplayPreferences.MAP_BACKEND_KEY] == RETIRED_BACKEND ||
            currentData.asMap().keys.any { it in retiredKeys }

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData
            .toMutablePreferences()
            .apply {
                if (this[DisplayPreferences.MAP_BACKEND_KEY] == RETIRED_BACKEND) {
                    this[DisplayPreferences.MAP_BACKEND_KEY] = MapBackend.OSM.name
                }
                retiredKeys.forEach { remove(it) }
            }.toPreferences()

    override suspend fun cleanUp() = Unit
}
