// Pure camera-follow math, kept free of MapLibre runtime and DOM state so it is
// unit-testable (see camera.test.ts); main.ts owns the page wiring.

// How long after the last camera push the position counts as lost (a tunnel):
// the chevron greys out, and the next push snaps the camera instead of easing
// across the gap. The host pushes once per location or camera-config update and
// goes quiet on signal loss, so the page ages the last push rather than waiting
// for a null. Mirrors LOCATION_STALE_THRESHOLD_MS in the Kotlin data layer
// (LocationFreshness.kt).
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

// Ease-in-out (cubic) for the one-shot camera moves below, which start and
// end at rest, unlike the chained per-fix segments. MapLibre's easeTo applies
// a comparable curve by default and takes only the duration; the Google Maps
// page interpolates its own camera (camera-glide.ts) and takes both.
export function smoothEase(t: number): number {
    return t < 0.5 ? 4 * t * t * t : 1 - (-2 * t + 2) ** 3 / 2;
}

// How a camera move plays out: an ease of this length and shape.
export interface CameraMotion {
    durationMs: number;
    easing: (t: number) => number;
}

// The one-shot camera moves, shared by both backends' follow machines so the
// two maps move alike. A re-follow eases the camera home in one continuous
// transition; a north-up flip re-orients while following; a pushed zoom step
// while detached (the host's +/- button) applies around the free camera's
// own centre.
export const REFOLLOW_MOTION: CameraMotion = { durationMs: 600, easing: smoothEase };
export const ORIENTATION_FLIP_MOTION: CameraMotion = { durationMs: 400, easing: smoothEase };
export const DETACHED_ZOOM_STEP_MOTION: CameraMotion = { durationMs: 250, easing: smoothEase };

// WGS84 coordinate bounds. A push outside them (or a non-finite one) makes the
// camera target garbage and throws the marker off the viewport until the next
// fix lands. Kotlin filters such fixes before they reach the bridge — see
// isUsableFix in LocationSanity.kt, which is the primary gate; this is defence
// in depth on the one value the page cannot render its way out of.
export const MAX_LATITUDE_DEG = 90;
export const MAX_LONGITUDE_DEG = 180;

export function isRealPosition(lat: number, lon: number): boolean {
    return (
        Number.isFinite(lat) &&
        Number.isFinite(lon) &&
        Math.abs(lat) <= MAX_LATITUDE_DEG &&
        Math.abs(lon) <= MAX_LONGITUDE_DEG
    );
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

// [bearing] folded into [0, 360).
export function normalizeBearing(bearing: number): number {
    return ((bearing % 360) + 360) % 360;
}

// How far the smoothed bearing may drift from the heading the map is rotated
// to before the map rotates again. A rotation is the most expensive camera
// change a vector renderer makes — every label is re-laid-out and re-tested
// for collisions — and the GNSS bearing wanders a degree or two on every fix
// even on a straight road, so without a dead band every fix is a rotation.
// Inside the band the map only translates and the chevron carries the
// residual, so the arrow still shows the true travel direction.
export const HEADING_HYSTERESIS_DEG = 4;

// The heading the map should be rotated to for [target], given the heading it
// is currently rotated to: [applied] while the drift stays inside the dead
// band, [target] once it leaves it or when nothing is applied yet (first fix,
// re-follow, signal gap).
export function settledHeading(
    applied: number | null,
    target: number,
    thresholdDeg: number = HEADING_HYSTERESIS_DEG,
): number {
    if (applied === null) return normalizeBearing(target);
    return Math.abs(shortestBearingDelta(applied, target)) < thresholdDeg
        ? applied
        : normalizeBearing(target);
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

// --- Layout-reflow lockstep --------------------------------------------------
// A dashboard layout change (a dock-position change, a card-visibility
// toggle, or a driver-side flip — see DashboardScaffold.kt) changes which
// overlay reserves screen space, so the host re-derives MapConfig's safe-area
// fractions and re-pushes the SAME GPS fix through updateCamera with only the
// padding changed — a "reflow", as opposed to a genuine fix where the center
// itself moves. The screen-pinned marker is normally repositioned with an
// instant style write while the camera eases toward the new padding over the
// GPS-cadence duration (easeDurationMs); on a reflow that reads as the marker
// snapping ahead of the map sliding underneath it. isPaddingOnlyReflow tells
// the two apart so the caller can glide both together instead.

// Tolerance for "same center", in degrees. Far tighter than any real
// fix-to-fix GPS movement (even a parked vehicle's noise floor is orders of
// magnitude above this) but loose enough to absorb the Kotlin-double ->
// JS-string -> JS-double round-trip through evaluateJavascript.
const REFLOW_CENTER_EPSILON_DEG = 1e-7;

// The camera-relevant fields of one host push. lon/lat name the coordinate
// pair generically — the Google Maps page's lat/lng fix maps onto the same
// shape at its call site.
export interface ReflowFix {
    lon: number;
    lat: number;
    markerPos: number;
    bottomSafe: number;
    rightSafe: number;
    leftSafe: number;
}

// True when [next] holds the same center as [previous] but a different
// safe-area padding (a layout reflow, not a new fix). A null
// [previous] (no push yet) or a moved center (a genuine fix, coincidentally
// alongside a padding change) both return false — see the call sites, which
// additionally gate this on "not the first camera push" and "not a signal
// gap" (those already force the existing snap path regardless).
export function isPaddingOnlyReflow(previous: ReflowFix | null, next: ReflowFix): boolean {
    if (!previous) return false;
    const sameCenter =
        Math.abs(previous.lon - next.lon) < REFLOW_CENTER_EPSILON_DEG &&
        Math.abs(previous.lat - next.lat) < REFLOW_CENTER_EPSILON_DEG;
    if (!sameCenter) return false;
    return (
        previous.markerPos !== next.markerPos ||
        previous.bottomSafe !== next.bottomSafe ||
        previous.rightSafe !== next.rightSafe ||
        previous.leftSafe !== next.leftSafe
    );
}

// Fixed lockstep duration for a padding-only reflow, used by both backends'
// follow machines: the marker's CSS transition and the camera ease both run
// this long, so they land together. A reflow is driven by a dashboard layout
// transition, not a new GPS fix, so it uses a fixed duration matched to the
// app-side layout-transition pace (Motion.kt's STANDARD tween is 220 ms, plus
// margin) rather than easeDurationMs's cadence-matched (and much wider, up to
// MAX_EASE_MS) range. Linear, like the marker's transition, so the two stay
// aligned throughout rather than only at the ends.
export const LAYOUT_REFLOW_MS = 260;
export const REFLOW_MOTION: CameraMotion = { durationMs: LAYOUT_REFLOW_MS, easing: linearEase };

// The one push of a follow camera: what the machines measure about it.
export interface FollowPush {
    // No camera placed yet: the first fix snaps rather than flying in from
    // the map's construction centre.
    firstCamera: boolean;
    // The fix arrived past LOCATION_STALE_THRESHOLD_MS after the previous
    // one; easing across a tunnel would glide through geometry, so snap.
    signalGap: boolean;
    // A padding-only reflow (isPaddingOnlyReflow) rather than a moved centre.
    reflow: boolean;
    // The measured interval since the previous push.
    sinceLastFixMs: number;
}

// How the camera moves for one follow push, or null to snap — the one
// decision both backends' follow machines make. A snap wins over a reflow: a
// signal gap can arrive with a padding change and must still snap. A reflow
// takes the marker-lockstep motion (the caller arms the marker's transition
// on exactly that identity). Everything else is a cadence-matched linear
// segment: back-to-back segments compose into one continuous glide instead
// of fixed-duration cubic eases that restart (accelerate-decelerate) on
// every fix.
export function followMotion(push: FollowPush): CameraMotion | null {
    if (push.firstCamera || push.signalGap) return null;
    if (push.reflow) return REFLOW_MOTION;
    return { durationMs: easeDurationMs(push.sinceLastFixMs), easing: linearEase };
}
