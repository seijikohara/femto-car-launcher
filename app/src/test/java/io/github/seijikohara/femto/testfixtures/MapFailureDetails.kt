package io.github.seijikohara.femto.testfixtures

// Page `fatal` details as the map page sends them (webmap/src/load-outcome.ts
// sets the kinds). The network kinds say the map data could not be reached:
// the OSM page with no tile arrived, a hosted style that never loaded, the
// Google Maps script that never arrived.
internal val NetworkFailureDetails =
    listOf(
        "tile-host-unreachable: AJAXError: Failed to fetch (0): https://tiles.openfreemap.org/planet",
        "style-load-failed: AJAXError: Failed to fetch (0): https://tiles.openfreemap.org/styles/positron",
        "backend-load-failed: The Google Maps JavaScript API could not load.",
    )

// Every other kind: a rejected credential, a server that refused the request
// (a mistyped style URL, a tile host that answers 403), no WebGL, or an
// exception thrown once the map library had loaded.
internal val BoundedFailureDetails =
    listOf(
        "google-maps-auth",
        "no-webgl-context",
        "map-init-exception: mapsLib.Map is not a constructor",
        "style-load-rejected: AJAXError: Not Found (404): https://styles.example.test/basic/style.json",
        "tile-host-rejected: AJAXError: Forbidden (403): https://tiles.example.test/planet",
    )
