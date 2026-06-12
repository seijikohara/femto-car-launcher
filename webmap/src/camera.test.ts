import { describe, expect, it } from "vitest";
import {
	BEARING_SNAP_DELTA_DEG,
	easeDurationMs,
	linearEase,
	MAX_EASE_MS,
	MIN_EASE_MS,
	shortestBearingDelta,
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
		expect(smoothedBearing(0, BEARING_SNAP_DELTA_DEG + 10)).toBe(
			BEARING_SNAP_DELTA_DEG + 10,
		);
	});

	it("normalizes the result into [0, 360)", () => {
		expect(smoothedBearing(null, 370)).toBe(10);
		expect(smoothedBearing(2, 354, 0.5)).toBe(358);
	});
});
