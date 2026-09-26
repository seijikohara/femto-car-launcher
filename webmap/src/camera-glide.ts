// A per-frame camera interpolator for a map whose camera API is immediate:
// Google Maps' moveCamera sets the camera "without animation", and the API has
// no easeTo, so a fix applied straight to it lands as a jump — one per GPS
// interval. Google's own vector-map guidance animates the camera by calling
// moveCamera from a requestAnimationFrame loop, which is what this does: each
// glide reads the camera the map is showing now, then re-applies an
// interpolated pose every frame until the target lands. The MapLibre backend
// needs none of this (maplibregl.Map#easeTo is the same loop, built in).
//
// The glide moves what MapLibre's easeTo moves: the location under the
// chevron and the chevron's screen offset (MapLibre's camera padding), not the
// camera centre. The backend derives the centre from each frame
// (cameraCenterFor), so a rotation or a zoom pivots on the chevron, as it does
// on the OSM map; interpolating the centre instead would swing the location
// off the chevron mid-move.
//
// Pure interpolation (lerpPose) and centre math are separate from the frame
// driver so they are unit-testable, and the driver takes its frame source and
// clock as dependencies so the tests can step it deterministically.
import { type CameraMotion, normalizeBearing, shortestBearingDelta } from "./camera";

export interface LatLng {
    lat: number;
    lng: number;
}

// The camera besides its anchor: the zoom, the heading (clockwise from north,
// the direction at the top of the screen), and the chevron's screen offset
// from the viewport centre in px (x right, y down).
export interface CameraView {
    zoom: number;
    heading: number;
    offsetX: number;
    offsetY: number;
}

// The camera the glide moves: the anchor (lat/lng, the location that sits at
// the chevron's spot) plus the view and the tilt. heading/tilt ride along for
// a raster map too (the backend drops them before moveCamera).
export interface CameraPose extends LatLng, CameraView {
    tilt: number;
}

const POSE_KEYS = ["lat", "lng", "zoom", "heading", "tilt", "offsetX", "offsetY"] as const;

// Below this delta a field is applied as the target rather than interpolated.
// After a release() a glide starts from the camera read back from the map,
// which can differ from what was last set by a rounding hair; interpolating
// that hair would re-set the field every frame, and a zoom or tilt set every
// frame keeps that property's gesture window open.
const HOLD_EPSILON = 1e-9;

// Web Mercator world coordinates, the projection of Google's built-in map
// types (and MapLibre's): a WORLD_SIZE-unit square, x east from the
// antimeridian, y south from the top edge; at zoom z one unit spans 2^z px.
const WORLD_SIZE = 256;

function worldX(lng: number): number {
    return (WORLD_SIZE * (lng + 180)) / 360;
}

function worldY(lat: number): number {
    const phi = (lat * Math.PI) / 180;
    return WORLD_SIZE * (0.5 - Math.log(Math.tan(Math.PI / 4 + phi / 2)) / (2 * Math.PI));
}

// [lng] folded into [-180, 180); an in-range value passes through untouched.
function wrapLongitude(lng: number): number {
    return lng >= -180 && lng < 180 ? lng : normalizeBearing(lng + 180) - 180;
}

function lngAt(x: number): number {
    return wrapLongitude((360 * x) / WORLD_SIZE - 180);
}

function latAt(y: number): number {
    return (360 / Math.PI) * Math.atan(Math.exp((0.5 - y / WORLD_SIZE) * 2 * Math.PI)) - 90;
}

// The world-unit displacement of the view's screen offset. The map turns
// clockwise by the heading, so screen right points along heading + 90° and
// screen down along heading + 180°; screen and world axes coincide at
// heading 0.
function worldOffset(view: CameraView): { dx: number; dy: number } {
    const scale = 2 ** view.zoom;
    const th = (view.heading * Math.PI) / 180;
    const cos = Math.cos(th);
    const sin = Math.sin(th);
    return {
        dx: (cos * view.offsetX - sin * view.offsetY) / scale,
        dy: (sin * view.offsetX + cos * view.offsetY) / scale,
    };
}

// The camera centre that shows [anchor] at the view's screen offset. Flat
// math: a tilted map's perspective is not modelled, so there the anchor sits
// at the offset only approximately (exactly on a flat map).
export function cameraCenterFor(anchor: LatLng, view: CameraView): LatLng {
    const d = worldOffset(view);
    return { lat: latAt(worldY(anchor.lat) - d.dy), lng: lngAt(worldX(anchor.lng) - d.dx) };
}

// The location at the view's screen offset when the camera centre is
// [center]: the inverse of cameraCenterFor.
export function anchorAt(center: LatLng, view: CameraView): LatLng {
    const d = worldOffset(view);
    return { lat: latAt(worldY(center.lat) + d.dy), lng: lngAt(worldX(center.lng) + d.dx) };
}

// One field [t] of the way from [from] to [to]. The heading turns and the
// longitude moves the short way round; the latitude moves evenly in Mercator
// y, as MapLibre moves its camera; the rest move linearly.
function lerpField(key: (typeof POSE_KEYS)[number], from: number, to: number, t: number): number {
    const delta = key === "heading" || key === "lng" ? shortestBearingDelta(from, to) : to - from;
    if (Math.abs(delta) < HOLD_EPSILON) return to;
    switch (key) {
        case "heading":
            return normalizeBearing(from + delta * t);
        case "lng":
            return wrapLongitude(from + delta * t);
        case "lat":
            return latAt(worldY(from) + (worldY(to) - worldY(from)) * t);
        default:
            return from + delta * t;
    }
}

// The pose [t] of the way from [from] to the fields [to] names. Only the
// named fields are returned, so a partial target (a zoom-only step) leaves the
// rest of the camera alone.
export function lerpPose(
    from: CameraPose,
    to: Partial<CameraPose>,
    t: number,
): Partial<CameraPose> {
    const out: Partial<CameraPose> = {};
    for (const key of POSE_KEYS) {
        const target = to[key];
        if (target === undefined) continue;
        out[key] = lerpField(key, from[key], target, t);
    }
    return out;
}

export interface CameraGlideDeps {
    // The camera the map shows right now: the start of a glide for every
    // field the glide does not own (see CameraGlide.release), so a re-follow
    // starts from wherever the user panned. [owned] holds the fields the
    // glide starts from instead of the read-back; a field read back through
    // others (the anchor, read from the map's centre at a zoom, heading and
    // offset) must be read at the owned values, or the first frame jumps
    // wherever the map did not take an owned value as given.
    current(owned: Partial<CameraPose>): CameraPose;
    // Set the map's camera to [pose] immediately (moveCamera).
    apply(pose: Partial<CameraPose>): void;
    // Frame source and clock; default to the page's requestAnimationFrame
    // and performance.now (both on the same time origin).
    requestFrame?: (callback: (now: number) => void) => void;
    now?: () => number;
}

export interface CameraGlide {
    // Ease the named fields to [target] with [motion]. Supersedes any glide
    // in flight, continuing from wherever it got to. A duration of zero or
    // less applies the target at once.
    to(target: Partial<CameraPose>, motion: CameraMotion): void;
    // Apply [target] at once, ending any glide in flight.
    jump(target: Partial<CameraPose>): void;
    // Hand the camera back to the user: end any glide in flight where it is
    // and forget the fields applied so far, so the next glide starts from
    // what the map shows. Call on every user gesture — while following, the
    // glide owns each field it has applied and starts from that value rather
    // than the map's read-back, because the map may not have accepted the
    // value as given (Google clamps zoom to the map type's ceiling and a
    // vector map's tilt by zoom): a glide from the clamped read-back toward
    // the unreachable target would re-set the field every frame, on every
    // push, and keep its gesture window open for as long as the car moves.
    release(): void;
}

export function createCameraGlide(deps: CameraGlideDeps): CameraGlide {
    const requestFrame =
        deps.requestFrame ?? ((callback: (now: number) => void) => requestAnimationFrame(callback));
    const now = deps.now ?? (() => performance.now());
    // Mutable state in one const holder (let/var are banned — see the
    // vite.config.ts lint block and no-let.js). Every to() / jump() /
    // release() advances the generation; a frame still queued from an
    // earlier glide sees the mismatch and does nothing. owned holds the
    // fields applied since the last release — see CameraGlide.release.
    const state = { generation: 0, owned: {} as Partial<CameraPose> };
    function cancel(): void {
        state.generation += 1;
    }
    function apply(pose: Partial<CameraPose>): void {
        deps.apply(pose);
        state.owned = { ...state.owned, ...pose };
    }
    return {
        to(target, motion): void {
            cancel();
            if (motion.durationMs <= 0) {
                apply(target);
                return;
            }
            const generation = state.generation;
            const from = { ...deps.current(state.owned), ...state.owned };
            const startMs = now();
            const frame = (): void => {
                if (generation !== state.generation) return;
                const t = (now() - startMs) / motion.durationMs;
                // The final frame applies the target itself rather than the
                // interpolation at t = 1, which floating point need not land
                // exactly on it.
                if (t >= 1) {
                    apply(target);
                    return;
                }
                apply(lerpPose(from, target, motion.easing(t)));
                requestFrame(frame);
            };
            requestFrame(frame);
        },
        jump(target): void {
            cancel();
            apply(target);
        },
        release(): void {
            cancel();
            state.owned = {};
        },
    };
}
