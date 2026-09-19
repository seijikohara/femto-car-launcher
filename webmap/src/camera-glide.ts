// A per-frame camera interpolator for a map whose camera API is immediate:
// Google Maps' moveCamera sets the camera "without animation", and the API has
// no easeTo, so a fix applied straight to it lands as a jump — one per GPS
// interval. Google's own vector-map guidance animates the camera by calling
// moveCamera from a requestAnimationFrame loop, which is what this does: each
// glide reads the camera the map is showing now, then re-applies an
// interpolated pose every frame until the target lands. The MapLibre backend
// needs none of this (maplibregl.Map#easeTo is the same loop, built in).
//
// Pure interpolation (lerpPose) is separate from the frame driver so the math
// is unit-testable, and the driver takes its frame source and clock as
// dependencies so the tests can step it deterministically.
import { type CameraMotion, normalizeBearing, shortestBearingDelta } from "./camera";

// The camera the glide moves: the Google Maps CameraOptions surface with the
// centre split into its two numbers. heading/tilt ride along for a raster
// map too (the backend drops them before moveCamera).
export interface CameraPose {
    lat: number;
    lng: number;
    zoom: number;
    heading: number;
    tilt: number;
}

const POSE_KEYS = ["lat", "lng", "zoom", "heading", "tilt"] as const;

// Below this delta a field is applied as the target rather than interpolated.
// A start pose read back from the map can differ from what was last set by a
// rounding hair; interpolating that hair would re-set the field every frame —
// and each heading set re-lays-out a vector map's labels.
const HOLD_EPSILON = 1e-9;

// The pose [t] of the way from [from] to the fields [to] names; the heading
// turns the short way round and folds into [0, 360). Only the named fields
// are returned, so a partial target (a zoom-only step) leaves the rest of the
// camera alone.
export function lerpPose(
    from: CameraPose,
    to: Partial<CameraPose>,
    t: number,
): Partial<CameraPose> {
    const out: Partial<CameraPose> = {};
    for (const key of POSE_KEYS) {
        const target = to[key];
        if (target === undefined) continue;
        const delta =
            key === "heading" ? shortestBearingDelta(from.heading, target) : target - from[key];
        if (Math.abs(delta) < HOLD_EPSILON) {
            out[key] = target;
        } else {
            const value = from[key] + delta * t;
            out[key] = key === "heading" ? normalizeBearing(value) : value;
        }
    }
    return out;
}

export interface CameraGlideDeps {
    // The camera the map shows right now: the start of a glide for every
    // field the glide does not own (see CameraGlide.release), so a re-follow
    // starts from wherever the user panned.
    current(): CameraPose;
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
            const from = { ...deps.current(), ...state.owned };
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
