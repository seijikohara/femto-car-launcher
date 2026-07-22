// Backend selection for the single map entry page: the host loads
// index.html?backend=<name> (see mapPageUrl in WebMapView.kt — the value set
// is a compatibility contract with the MapBackend enum). Pure and
// DOM-independent so it is unit-testable.
export const MAP_BACKENDS = ["osm", "mapbox", "googlemaps"] as const;

export type MapBackendName = (typeof MAP_BACKENDS)[number];

// Resolve the ?backend= query parameter; anything missing or unknown falls
// back to OSM (the keyless default — also what `vp dev` serves with a bare
// URL).
export function resolveBackend(search: string): MapBackendName {
    const raw = new URLSearchParams(search).get("backend") ?? "";
    return (MAP_BACKENDS as readonly string[]).includes(raw) ? (raw as MapBackendName) : "osm";
}
