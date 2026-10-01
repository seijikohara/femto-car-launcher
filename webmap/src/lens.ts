// Google's stand-in for camera padding: a projection adapter that measures the
// perspective of a tilted Google vector map and corrects the follow camera for
// a chevron that sits off the viewport centre.
//
// MapLibre's camera padding moves the perspective's vanishing point to the
// padded centre, so the road ahead runs straight up through a chevron placed
// beside the cards. The Maps JS API has no padding: its perspective always
// converges on the viewport centre, and a ground line through an off-centre
// chevron that runs along the camera's forward axis leans toward that centre.
// Two corrections restore the OSM picture at the OSM spot (markerSpot):
//
//   - a yaw bias δ on the map heading, so that the direction of travel — not
//     the camera's forward axis — projects vertically through the chevron;
//   - the ground offset of the fix from the camera target, solved through
//     the perspective, so the fix lands exactly under the chevron (the flat
//     Web Mercator offset misses it by tens of px at a typical tilt).
//
// Both need the camera's focal length and distance, which Google does not
// document. They are MEASURED, never assumed: the page projects two ground
// points ahead of and behind the camera target through Google's own
// MapCanvasProjection (calibrateLens) and solves the pinhole model below for
// them. No field of view is hard-coded anywhere.
//
// Pinhole model, in flat map px at the current zoom (the units of
// camera-glide.ts's screen offsets on an untilted map): the camera looks at
// the target from distance L, tilted θ from straight down, with focal length
// f in screen px. A ground point (x right, y forward of the target) lands at
//
//     sx = f·x / (L + y·sinθ),    sy = −f·y·cosθ / (L + y·sinθ)
//
// screen px from the viewport centre (sy down). At θ = 0 with f = L that is
// the flat map, which is why a raster or untilted map needs no lens.
//
// δ is a projection correction, not a heading rule: followOrientation in
// camera.ts stays the one source of the follow heading, and the backend adds
// δ only where it talks to the map (moveCamera, the read-back, the compass).

export interface LensCalibration {
    // The focal length f, in screen px.
    focalPx: number;
    // The camera's distance L from its target, in flat map px.
    distancePx: number;
}

// One measurement through the map's projection: ground points
// [probeDistancePx] flat px ahead of and behind the camera target along the
// camera's forward axis, and how far above / below the target's own pixel
// they land on screen.
export interface LensProbe {
    tiltDeg: number;
    probeDistancePx: number;
    aheadUpPx: number;
    behindDownPx: number;
    viewportHeightPx: number;
}

// The implied vertical field of view a calibration must fall inside to be
// believed: wide enough for any map renderer we know of, narrow enough to
// reject a projection that ignores the tilt (an infinite focal length) or a
// garbage reading. Outside it the page falls back to the uncalibrated
// placement rather than trusting the numbers.
export const LENS_MIN_FOVY_DEG = 10;
export const LENS_MAX_FOVY_DEG = 60;

const DEG = Math.PI / 180;

// How far from the target to probe: a quarter of the viewport height keeps
// both probes well in front of the camera at any plausible tilt and field of
// view, while spanning enough px for the measurement to be precise.
export function lensProbeDistancePx(viewportHeightPx: number): number {
    return viewportHeightPx / 4;
}

// Solve the pinhole model for one probe pair, or null when the measurement
// cannot be trusted. With v₊ = aheadUpPx and v₋ = behindDownPx at probe
// distance d:  1/v₊ − 1/v₋ = 2·tanθ / f  and  1/v₊ + 1/v₋ = 2L / (f·d·cosθ).
export function calibrateLens(probe: LensProbe): LensCalibration | null {
    const { tiltDeg, probeDistancePx: d, aheadUpPx: up, behindDownPx: down } = probe;
    // At tilt 0 the two probes are symmetric and f/L is all they carry.
    if (!(tiltDeg > 0) || !(up > 0) || !(down > 0) || !(d > 0)) return null;
    const theta = tiltDeg * DEG;
    const asymmetry = 1 / up - 1 / down;
    if (!(asymmetry > 0)) return null;
    const focalPx = (2 * Math.tan(theta)) / asymmetry;
    const distancePx = ((focalPx * d * Math.cos(theta)) / 2) * (1 / up + 1 / down);
    const fovyDeg = (2 * Math.atan(probe.viewportHeightPx / 2 / focalPx)) / DEG;
    if (!Number.isFinite(focalPx) || !(distancePx > 0) || !Number.isFinite(distancePx)) {
        return null;
    }
    if (!(fovyDeg >= LENS_MIN_FOVY_DEG && fovyDeg <= LENS_MAX_FOVY_DEG)) return null;
    return { focalPx, distancePx };
}

// The flat-px offset (x right, y down, camera-aligned) of the ground point
// that a chevron at screen offset ([offsetXPx], [offsetYPx]) from the viewport
// centre sits on: what camera-glide.ts's centre math takes as the chevron's
// offset. The screen offset itself while uncalibrated, on a flat map, and for
// a spot at or above the horizon (no ground point under it).
export function lensGroundOffset(
    lens: LensCalibration | null,
    offsetXPx: number,
    offsetYPx: number,
    tiltDeg: number,
): { x: number; y: number } {
    if (lens === null || !(tiltDeg > 0)) return { x: offsetXPx, y: offsetYPx };
    const theta = tiltDeg * DEG;
    const sin = Math.sin(theta);
    const denominator = lens.focalPx * Math.cos(theta) + offsetYPx * sin;
    if (!(denominator > 0)) return { x: offsetXPx, y: offsetYPx };
    // The ground point's forward distance from the target (negative: below).
    const forward = (-offsetYPx * lens.distancePx) / denominator;
    return {
        x: (offsetXPx * (lens.distancePx + forward * sin)) / lens.focalPx,
        y: -forward,
    };
}

// The yaw bias δ, in degrees, with the sign of [offsetXPx]: the map turns to
// the travel heading minus δ, which points the direction of travel δ
// clockwise of the camera's forward axis — the one direction whose vanishing
// point sits straight above a chevron [offsetXPx] px off the centre line
// (tan δ = Δx·sinθ / f). Independent of the camera distance and of the
// chevron's drop. 0 while uncalibrated and on a flat map.
export function lensYawDeg(
    lens: LensCalibration | null,
    offsetXPx: number,
    tiltDeg: number,
): number {
    if (lens === null || !(tiltDeg > 0) || offsetXPx === 0) return 0;
    return Math.atan((offsetXPx * Math.sin(tiltDeg * DEG)) / lens.focalPx) / DEG;
}
