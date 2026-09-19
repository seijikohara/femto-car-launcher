import { describe, expect, it } from "vite-plus/test";
import {
    appliedBearing,
    BEARING_SNAP_DELTA_DEG,
    easeDurationMs,
    followMotion,
    HEADING_SETTLE_MIN_DEG,
    HEADING_SETTLE_MS,
    heldHeading,
    isPaddingOnlyReflow,
    isRealPosition,
    linearEase,
    MAX_EASE_MS,
    MAX_LATITUDE_DEG,
    MAX_LONGITUDE_DEG,
    MIN_EASE_MS,
    NO_HEADING_HOLD,
    REFLOW_MOTION,
    type ReflowFix,
    shortestBearingDelta,
    settledHeading,
    smoothEase,
    smoothedBearing,
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

describe("smoothEase", () => {
    it("starts and ends on the endpoints", () => {
        expect(smoothEase(0)).toBe(0);
        expect(smoothEase(1)).toBe(1);
    });

    it("accelerates out of the start and decelerates into the end symmetrically", () => {
        expect(smoothEase(0.25)).toBeLessThan(0.25);
        expect(smoothEase(0.75)).toBeGreaterThan(0.75);
        expect(smoothEase(0.5)).toBe(0.5);
        expect(smoothEase(0.25) + smoothEase(0.75)).toBeCloseTo(1);
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

describe("heldHeading", () => {
    const held90 = { applied: 90, residualSinceMs: null };

    it("adopts the target when nothing is applied", () => {
        expect(heldHeading(NO_HEADING_HOLD, 350, 0)).toEqual({
            applied: 350,
            residualSinceMs: null,
        });
    });

    it("holds inside the band and starts the residual clock", () => {
        expect(heldHeading(held90, 92, 1_000)).toEqual({ applied: 90, residualSinceMs: 1_000 });
    });

    it("rotates at once when the target leaves the band", () => {
        expect(heldHeading(held90, 94, 1_000)).toEqual({ applied: 94, residualSinceMs: null });
    });

    it("settles a residual that has persisted for the settle time", () => {
        const holding = heldHeading(held90, 92, 1_000);
        expect(heldHeading(holding, 92, 1_000 + HEADING_SETTLE_MS - 1).applied).toBe(90);
        expect(heldHeading(holding, 92, 1_000 + HEADING_SETTLE_MS)).toEqual({
            applied: 92,
            residualSinceMs: null,
        });
    });

    it("leaves a residual below the settle minimum alone", () => {
        const target = 90 + HEADING_SETTLE_MIN_DEG / 2;
        const holding = heldHeading(held90, target, 1_000);
        expect(holding).toEqual({ applied: 90, residualSinceMs: null });
        expect(heldHeading(holding, target, 1_000 + 2 * HEADING_SETTLE_MS).applied).toBe(90);
    });

    it("restarts the residual clock when the residual dips below the minimum", () => {
        const a = heldHeading(held90, 92, 0);
        const b = heldHeading(a, 90.5, 1_000);
        expect(b.residualSinceMs).toBeNull();
        const c = heldHeading(b, 92, 2_000);
        expect(c.residualSinceMs).toBe(2_000);
        expect(heldHeading(c, 92, 2_000 + HEADING_SETTLE_MS - 1).applied).toBe(90);
        expect(heldHeading(c, 92, 2_000 + HEADING_SETTLE_MS).applied).toBe(92);
    });

    it("measures the residual across the north seam", () => {
        const holding = heldHeading({ applied: 359, residualSinceMs: null }, 1, 0);
        expect(holding).toEqual({ applied: 359, residualSinceMs: 0 });
        expect(heldHeading(holding, 1, HEADING_SETTLE_MS).applied).toBe(1);
    });
});

describe("settledHeading", () => {
    it("adopts the target when nothing is applied yet", () => {
        expect(settledHeading(null, 350)).toBe(350);
        expect(settledHeading(null, -10)).toBe(350);
    });

    it("holds the applied heading while the drift stays inside the dead band", () => {
        // Jitter on a straight road: the map must not rotate.
        expect(settledHeading(90, 92)).toBe(90);
        expect(settledHeading(90, 87)).toBe(90);
        expect(settledHeading(359, 2)).toBe(359);
    });

    it("follows the target once the drift leaves the dead band", () => {
        expect(settledHeading(90, 94)).toBe(94);
        expect(settledHeading(90, 85)).toBe(85);
        expect(settledHeading(359, 5)).toBe(5);
    });

    it("honours a caller-supplied band", () => {
        expect(settledHeading(90, 99, 10)).toBe(90);
        expect(settledHeading(90, 100, 10)).toBe(100);
    });
});
