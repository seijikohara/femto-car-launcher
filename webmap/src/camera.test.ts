import { describe, expect, it } from "vite-plus/test";
import {
    appliedBearing,
    BEARING_SNAP_DELTA_DEG,
    cubicBezier,
    DETACHED_ZOOM_STEP_MOTION,
    defaultEase,
    easeDurationMs,
    followMotion,
    followOrientation,
    isPaddingOnlyReflow,
    isRealPosition,
    linearEase,
    MAX_EASE_MS,
    MAX_LATITUDE_DEG,
    MAX_LONGITUDE_DEG,
    MIN_EASE_MS,
    ORIENTATION_FLIP_MOTION,
    REFLOW_MOTION,
    REFOLLOW_MOTION,
    type ReflowFix,
    shortestBearingDelta,
    smoothedBearing,
    spotMotion,
} from "./camera";

describe("easeDurationMs", () => {
    it("matches the measured inter-fix interval", () => {
        expect(easeDurationMs(250)).toBe(250);
        expect(easeDurationMs(1000)).toBe(1000);
    });

    it("clamps a burst of near-simultaneous fixes to the floor", () => {
        expect(easeDurationMs(0)).toBe(MIN_EASE_MS);
        expect(easeDurationMs(40)).toBe(MIN_EASE_MS);
    });

    it("clamps a slow provider to the ceiling", () => {
        expect(easeDurationMs(5_000)).toBe(MAX_EASE_MS);
    });
});

describe("linearEase", () => {
    it("is the identity over the animation progress", () => {
        expect(linearEase(0)).toBe(0);
        expect(linearEase(0.25)).toBe(0.25);
        expect(linearEase(1)).toBe(1);
    });
});

describe("cubicBezier", () => {
    it("is the identity for the straight curve, including where the solver bisects", () => {
        // cubic-bezier(0, 0, 1, 1) is flat at both ends in x(t), which sends
        // the solver from Newton's method to its bisection fallback there.
        const straight = cubicBezier(0, 0, 1, 1);
        for (const x of [1e-7, 1e-3, 0.1, 0.5, 0.9, 1 - 1e-3, 1 - 1e-7]) {
            expect(Math.abs(straight(x) - x)).toBeLessThan(1e-5);
        }
    });

    it("holds the endpoints outside [0, 1]", () => {
        const curve = cubicBezier(0.42, 0, 0.58, 1);
        expect(curve(-0.5)).toBe(0);
        expect(curve(0)).toBe(0);
        expect(curve(1)).toBe(1);
        expect(curve(1.5)).toBe(1);
    });
});

describe("defaultEase", () => {
    it("matches MapLibre's default easeTo curve, cubic-bezier(0.25, 0.1, 0.25, 1)", () => {
        // Reference values from maplibre-gl 6.10.0's own bezier(.25, .1, .25, 1),
        // the curve the OSM map's one-shot moves have always taken.
        expect(Math.abs(defaultEase(0.25) - 0.408510593016)).toBeLessThan(1e-6);
        expect(Math.abs(defaultEase(0.5) - 0.802403387695)).toBeLessThan(1e-6);
        expect(Math.abs(defaultEase(0.75) - 0.960459072953)).toBeLessThan(1e-6);
    });

    it("starts fast and settles gently, rising monotonically from 0 to 1", () => {
        const samples = Array.from({ length: 101 }, (_, i) => defaultEase(i / 100));
        expect(samples[0]).toBe(0);
        expect(samples[100]).toBe(1);
        expect(samples.every((v, i) => i === 0 || v >= samples[i - 1])).toBe(true);
        expect(defaultEase(0.25)).toBeGreaterThan(0.25);
    });

    it("is the curve of every one-shot camera move, for both backends", () => {
        for (const motion of [
            REFOLLOW_MOTION,
            ORIENTATION_FLIP_MOTION,
            DETACHED_ZOOM_STEP_MOTION,
        ]) {
            expect(motion.easing).toBe(defaultEase);
        }
    });
});

describe("shortestBearingDelta", () => {
    it("returns the signed shortest rotation", () => {
        expect(shortestBearingDelta(10, 30)).toBe(20);
        expect(shortestBearingDelta(30, 10)).toBe(-20);
    });

    it("crosses the 0/360 seam the short way", () => {
        expect(shortestBearingDelta(350, 10)).toBe(20);
        expect(shortestBearingDelta(10, 350)).toBe(-20);
    });

    it("treats the antipode as -180 (range [-180, 180))", () => {
        expect(shortestBearingDelta(0, 180)).toBe(-180);
    });
});

describe("smoothedBearing", () => {
    it("adopts the raw bearing when there is no previous one", () => {
        expect(smoothedBearing(null, 123)).toBe(123);
    });

    it("low-passes jitter-scale deltas toward the raw bearing", () => {
        // alpha 0.5: halfway between previous and raw.
        expect(smoothedBearing(100, 110, 0.5)).toBe(105);
    });

    it("smooths across the 0/360 seam without spinning the long way", () => {
        expect(smoothedBearing(358, 6, 0.5)).toBe(2);
        expect(smoothedBearing(6, 358, 0.5)).toBe(2);
    });

    it("passes a turn-scale delta straight through", () => {
        expect(smoothedBearing(0, BEARING_SNAP_DELTA_DEG + 10)).toBe(BEARING_SNAP_DELTA_DEG + 10);
    });

    it("normalizes the result into [0, 360)", () => {
        expect(smoothedBearing(null, 370)).toBe(10);
        expect(smoothedBearing(2, 354, 0.5)).toBe(358);
    });
});

describe("appliedBearing", () => {
    it("pins the camera to north when north-up is on", () => {
        expect(appliedBearing(true, 137)).toBe(0);
    });

    it("follows the travel heading when north-up is off", () => {
        expect(appliedBearing(false, 137)).toBe(137);
    });
});

describe("followOrientation", () => {
    it("turns a rotating map to every smoothed bearing, however small the change", () => {
        // Bearing jitter on a straight road, every step under the 4° the
        // removed Google dead band held back: the map lands on each smoothed
        // bearing and the chevron points straight up, with no residual.
        const headings = [90, 90.8, 91.3, 90.9].reduce<number[]>(
            (smoothed, raw) => [
                ...smoothed,
                smoothedBearing(smoothed.length > 0 ? smoothed[smoothed.length - 1] : null, raw),
            ],
            [],
        );
        [90, 90.4, 90.85, 90.875].forEach((expected, i) => {
            expect(headings[i]).toBeCloseTo(expected, 9);
        });
        for (const heading of headings) {
            expect(followOrientation(false, heading, true)).toEqual({
                mapBearing: heading,
                chevronTurn: 0,
            });
        }
    });

    it("pins a rotating map to north in north-up and turns the chevron instead", () => {
        expect(followOrientation(true, 137, true)).toEqual({ mapBearing: 0, chevronTurn: 137 });
    });

    it("keeps a map that cannot rotate north-up, the chevron carrying the heading", () => {
        expect(followOrientation(false, 137, false)).toEqual({ mapBearing: 0, chevronTurn: 137 });
        expect(followOrientation(true, 137, false)).toEqual({ mapBearing: 0, chevronTurn: 137 });
    });
});

describe("followMotion", () => {
    const steady = { firstCamera: false, signalGap: false, reflow: false, sinceLastFixMs: 1_000 };

    it("snaps the first camera placement", () => {
        expect(followMotion({ ...steady, firstCamera: true })).toBeNull();
    });

    it("snaps across a signal gap, even one pushed as a reflow", () => {
        expect(followMotion({ ...steady, signalGap: true, reflow: true })).toBeNull();
    });

    it("glides a layout reflow in lockstep with the marker", () => {
        expect(followMotion({ ...steady, reflow: true })).toBe(REFLOW_MOTION);
    });

    it("glides a fix linearly over the measured inter-fix interval", () => {
        const motion = followMotion({ ...steady, sinceLastFixMs: 250 });
        expect(motion?.durationMs).toBe(easeDurationMs(250));
        expect(motion?.easing).toBe(linearEase);
    });
});

describe("spotMotion", () => {
    const offset = { x: -0.214, y: 0.224 };
    const centred = { x: 0, y: 0.224 };
    const fix = followMotion({
        firstCamera: false,
        signalGap: false,
        reflow: false,
        sinceLastFixMs: 1_000,
    });

    it("places a chevron that is not on screen yet without a glide of its own", () => {
        expect(spotMotion(null, centred, null)).toEqual({ snapAt: null, motion: null });
        expect(spotMotion(null, centred, REFOLLOW_MOTION)).toEqual({
            snapAt: null,
            motion: REFOLLOW_MOTION,
        });
    });

    it("keeps the push's motion while the chevron stays where it is", () => {
        expect(spotMotion(centred, { ...centred }, fix)).toEqual({ snapAt: null, motion: fix });
        expect(spotMotion(centred, { ...centred }, null)).toEqual({ snapAt: null, motion: null });
    });

    it("glides a chevron that moves with the reflow motion, the camera in lockstep", () => {
        expect(spotMotion(offset, centred, fix)).toEqual({ snapAt: null, motion: REFLOW_MOTION });
        expect(spotMotion(centred, offset, ORIENTATION_FLIP_MOTION)).toEqual({
            snapAt: null,
            motion: REFLOW_MOTION,
        });
    });

    it("lands a snap under the chevron where it is, then glides the chevron to its new spot", () => {
        // A snap (a signal gap, the rendering-mode resolve) cannot carry the
        // chevron: it would jump. The camera snaps with the fix under the
        // chevron's current spot and the two then glide together.
        expect(spotMotion(offset, centred, null)).toEqual({
            snapAt: offset,
            motion: REFLOW_MOTION,
        });
    });
});

describe("isPaddingOnlyReflow", () => {
    const fix: ReflowFix = {
        lon: 139.767,
        lat: 35.681,
        markerPos: 70,
        bottomSafe: 0.1,
        rightSafe: 0.4,
        leftSafe: 0,
    };

    it("is false with no previous push", () => {
        expect(isPaddingOnlyReflow(null, fix)).toBe(false);
    });

    it("is false when neither the center nor the padding changed", () => {
        expect(isPaddingOnlyReflow(fix, { ...fix })).toBe(false);
    });

    it("is true when the center holds but a safe fraction changed (a layout change)", () => {
        expect(isPaddingOnlyReflow(fix, { ...fix, rightSafe: 0, leftSafe: 0 })).toBe(true);
    });

    it("is true when only markerPos changed", () => {
        expect(isPaddingOnlyReflow(fix, { ...fix, markerPos: 40 })).toBe(true);
    });

    it("is false when the center moved (a genuine GPS fix), even alongside a padding change", () => {
        expect(
            isPaddingOnlyReflow(fix, {
                ...fix,
                lon: fix.lon + 0.001,
                rightSafe: 0,
                leftSafe: 0,
            }),
        ).toBe(false);
    });

    it("tolerates float round-trip noise in the center without flagging a fix", () => {
        expect(
            isPaddingOnlyReflow(fix, {
                ...fix,
                lat: fix.lat + 1e-9,
                bottomSafe: 0,
            }),
        ).toBe(true);
    });
});

describe("isRealPosition", () => {
    it("accepts a coordinate on the globe", () => {
        expect(isRealPosition(35.658, 139.7016)).toBe(true);
        expect(isRealPosition(0, 0)).toBe(true);
        expect(isRealPosition(-MAX_LATITUDE_DEG, MAX_LONGITUDE_DEG)).toBe(true);
    });

    it("rejects a non-finite coordinate", () => {
        expect(isRealPosition(Number.NaN, 139.7016)).toBe(false);
        expect(isRealPosition(35.658, Number.POSITIVE_INFINITY)).toBe(false);
    });

    it("rejects a coordinate off the globe", () => {
        expect(isRealPosition(MAX_LATITUDE_DEG + 1, 0)).toBe(false);
        expect(isRealPosition(0, -MAX_LONGITUDE_DEG - 1)).toBe(false);
    });
});
