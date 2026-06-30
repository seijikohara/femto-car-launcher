package io.github.seijikohara.femto.data.display

// Map rendering backend. All are WebView pages built from webmap/.
// OSM = MapLibre GL JS + OpenFreeMap (free). MAPBOX = Mapbox GL JS (requires a
// user-supplied Mapbox access token). GOOGLEMAPS = Google Maps API (requires a
// user-supplied Google Maps API key).
internal enum class MapBackend { OSM, MAPBOX, GOOGLEMAPS }
