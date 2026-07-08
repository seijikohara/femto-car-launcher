import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { LAYOUT_REFLOW_MS } from "./camera";
import { createMarkerTransition } from "./marker-motion";

// A plain style-bearing stub stands in for the marker element: the helper
// only ever reads/writes el.style.transition, so a real HTMLElement (and the
// jsdom environment it would require) adds nothing here.
function markerStub(): HTMLElement {
	return { style: { transition: "" } } as unknown as HTMLElement;
}

describe("createMarkerTransition", () => {
	beforeEach(() => {
		vi.useFakeTimers();
	});

	afterEach(() => {
		vi.useRealTimers();
	});

	it("arms the left/top transition on setActive(true) and self-clears once the duration elapses", () => {
		const marker = markerStub();
		const transition = createMarkerTransition(marker, LAYOUT_REFLOW_MS);

		transition.setActive(true);
		expect(marker.style.transition).toBe(
			`left ${LAYOUT_REFLOW_MS}ms linear, top ${LAYOUT_REFLOW_MS}ms linear`,
		);

		vi.advanceTimersByTime(LAYOUT_REFLOW_MS);
		expect(marker.style.transition).toBe("");
	});

	it("clears the transition synchronously on setActive(false), with no lag for the next fix", () => {
		const marker = markerStub();
		const transition = createMarkerTransition(marker, LAYOUT_REFLOW_MS);

		transition.setActive(true);
		expect(marker.style.transition).not.toBe("");

		transition.setActive(false);
		expect(marker.style.transition).toBe("");
	});

	it("ignores a stale timeout from an interrupted arming and lets the later one clear at its own deadline", () => {
		const marker = markerStub();
		const transition = createMarkerTransition(marker, LAYOUT_REFLOW_MS);

		transition.setActive(true); // first arming, generation N
		vi.advanceTimersByTime(LAYOUT_REFLOW_MS / 2);
		transition.setActive(true); // second arming (rapid layout toggle), generation N+1

		// The first arming's timeout fires here; its generation check must fail
		// and leave the second, still-active transition untouched.
		vi.advanceTimersByTime(LAYOUT_REFLOW_MS / 2);
		expect(marker.style.transition).not.toBe("");

		// The second arming's own timeout fires here and clears it.
		vi.advanceTimersByTime(LAYOUT_REFLOW_MS / 2);
		expect(marker.style.transition).toBe("");
	});

	it("treats a setActive(false) before the timeout as final; the stale timeout later fires as a no-op", () => {
		const marker = markerStub();
		const transition = createMarkerTransition(marker, LAYOUT_REFLOW_MS);

		transition.setActive(true);
		transition.setActive(false);
		expect(marker.style.transition).toBe("");

		vi.advanceTimersByTime(LAYOUT_REFLOW_MS);
		expect(marker.style.transition).toBe("");
	});
});
