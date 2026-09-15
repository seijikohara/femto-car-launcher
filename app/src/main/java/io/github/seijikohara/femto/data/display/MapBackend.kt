package io.github.seijikohara.femto.data.display

// Map rendering backend. Both are WebView pages built from webmap/.
// OSM = MapLibre GL JS + OpenFreeMap (free, keyless). GOOGLEMAPS = Google Maps
// JavaScript API (requires a user-supplied API key). A persisted name that no
// longer exists — the retired MAPBOX backend — reads back as OSM (see
// RetiredMapboxKeysMigration).
internal enum class MapBackend { OSM, GOOGLEMAPS }
