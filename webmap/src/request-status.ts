// The HTTP status behind a MapLibre map error, which load-outcome.ts
// classifies. A module of its own so it can be tested without a map, and
// imported only by osm.ts: load-outcome.ts sits in the entry chunk, and
// importing MapLibre there would load it into the Google page too.
import { AJAXError } from "maplibre-gl";

// The status of a failed request: MapLibre's AJAXError carries the response's
// status, or 0 when fetch failed without one (offline, DNS, CORS). Any other
// error — a style that does not parse, a worker exception — has none.
export function requestStatusOf(error: unknown): number | null {
    return error instanceof AJAXError ? error.status : null;
}
