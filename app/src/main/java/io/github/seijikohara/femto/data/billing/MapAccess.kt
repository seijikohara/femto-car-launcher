package io.github.seijikohara.femto.data.billing

import io.github.seijikohara.femto.data.display.MapBackend

// The rendered backend is gated on entitlement: a stored MAPBOX preference only
// takes effect while the subscription is active. The stored preference is kept as-is
// (sub-project C never rewrites it here), so a lapsed-then-renewed subscription
// restores Mapbox automatically.
internal fun effectiveBackend(stored: MapBackend, mapboxUnlocked: Boolean): MapBackend =
    if (mapboxUnlocked && stored == MapBackend.MAPBOX) MapBackend.MAPBOX else MapBackend.OSM
