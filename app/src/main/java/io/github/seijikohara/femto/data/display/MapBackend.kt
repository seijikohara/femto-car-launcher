package io.github.seijikohara.femto.data.display

// Map rendering backend. Both are WebGL LIVE WebView pages built from webmap/.
// OSM = MapLibre GL JS + OpenFreeMap (free). MAPBOX = Mapbox GL JS (requires a
// user-supplied Mapbox access token).
internal enum class MapBackend { OSM, MAPBOX }
