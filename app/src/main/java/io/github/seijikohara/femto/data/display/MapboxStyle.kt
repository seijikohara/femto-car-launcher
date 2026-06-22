package io.github.seijikohara.femto.data.display

// Mapbox base style for the MAPBOX backend. STANDARD is the 3D fragment style
// (lightPreset day/night); SATELLITE is satellite-streets; STREETS is the vector
// parity style. Traffic is a separate toggle layered on top (mapboxTraffic).
internal enum class MapboxStyle { STANDARD, SATELLITE, STREETS }
