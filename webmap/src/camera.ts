// Pure camera-follow math, kept free of MapLibre runtime and DOM state so it is
// unit-testable (see camera.test.ts); main.ts owns the page wiring.

// How long after the last camera push the position counts as lost (a tunnel):
// the chevron greys out, and the next push snaps the camera instead of easing
// across the gap. The host pushes once per location or camera-config update and
// goes quiet on signal loss, so the page ages the last push rather than waiting
// for a null. Mirrors LOCATION_STALE_THRESHOLD_MS in the Kotlin data layer
// (LocationFreshness.kt) and the SNAPSHOT chevron.
export const LOCATION_STALE_THRESHOLD_MS = 10_000;

// Bounds for the cadence-matched ease duration. The floor keeps a burst of
// near-simultaneous fixes from degenerating into zero-length jumps; the ceiling
// keeps a slow provider (or a dropped fix) from easing glacially long after the
// vehicle has moved on.
export const MIN_EASE_MS = 100;
export const MAX_EASE_MS = 2_000;

// Ease duration matched to the measured inter-fix interval, so each ease
// finishes right as the next fix lands and the camera never stops between
// fixes. The previous fixed 1000 ms duration fought the fix cadence (250 ms
// default): every fix interrupted the running ease and restarted its
// accelerate-decelerate curve, which read as stop-start jank.
export function easeDurationMs(dtMs: number): number {
	return Math.max(MIN_EASE_MS, Math.min(MAX_EASE_MS, dtMs));
}

// Constant-velocity easing for chained per-fix eases. The default cubic curve
// accelerates and decelerates inside every segment, so a chain of them pulses;
// back-to-back linear segments compose into one continuous glide (the GPS
// speed itself provides the real-world acceleration).
export function linearEase(t: number): number {
	return t;
}

// Bearings above this delta track immediately: a genuine turn must not lag
// behind a low-pass filter. Below it, the EMA damps the few-degree GNSS
// azimuth jitter that otherwise wobbles the whole heading-up map.
export const BEARING_SNAP_DELTA_DEG = 45;

// EMA weight for bearing smoothing; higher follows the raw azimuth faster.
export const BEARING_SMOOTHING_ALPHA = 0.5;

// The signed shortest rotation from [from] to [to], in [-180, 180).
export function shortestBearingDelta(from: number, to: number): number {
	const raw = (((to - from) % 360) + 540) % 360;
	return raw - 180;
}

// Smooth the raw GNSS bearing against the previously applied one: jitter-scale
// deltas are low-passed, turn-scale deltas pass through, and a null previous
// bearing (first fix, or a fix after a signal gap) adopts the raw value.
export function smoothedBearing(
	previous: number | null,
	next: number,
	alpha: number = BEARING_SMOOTHING_ALPHA,
): number {
	if (previous === null) return normalizeBearing(next);
	const delta = shortestBearingDelta(previous, next);
	if (Math.abs(delta) > BEARING_SNAP_DELTA_DEG) return normalizeBearing(next);
	return normalizeBearing(previous + alpha * delta);
}

function normalizeBearing(bearing: number): number {
	return ((bearing % 360) + 360) % 360;
}

// How long after the user's last gesture the camera re-attaches to the
// location follow on its own. Long enough to read the map after a scroll,
// short enough that a driver who forgets the map is detached gets the
// car-nav-standard automatic recovery.
export const AUTO_REFOLLOW_MS = 15_000;

// The bearing the follow camera applies: north-up pins the map to north and
// leaves orientation to the chevron; heading-up rotates the map itself.
export function appliedBearing(northUp: boolean, heading: number): number {
	return northUp ? 0 : heading;
}
