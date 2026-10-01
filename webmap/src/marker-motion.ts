// Screen-pinned self-marker CSS-transition control, used by both backends'
// follow machines (which position the #self-marker element via left/top
// percentages — see style.ts for the percentage math).
//
// A layout reflow (see camera.ts isPaddingOnlyReflow) — and, on the Google
// Maps page, any other move of the chevron's spot (camera.ts spotMotion) —
// needs the marker to glide left/top over the SAME fixed duration the camera
// eases over, instead of the marker's normal instant (screen-pinned) jump; a
// genuine GPS fix needs that instant jump back, so the transition is armed
// only for the span of one such glide. A fix that arrives mid-glide leaves it
// armed and moves the camera over the time it has left (remainingMs; see
// camera.ts followMotion and markerTransitionStep), so the chevron and the
// camera land together; the fix writes the same left/top for an unchanged
// layout, which does not restart the running transition.
import type { MarkerTransitionStep } from "./camera";

export interface MarkerTransition {
    // Arms (or clears) the CSS transition; call before writing the new
    // left/top so the write itself is what animates (or jumps).
    setActive(active: boolean): void;
    // Applies one camera move's step (camera.ts markerTransitionStep):
    // arm, keep whatever is in flight, or clear.
    apply(step: MarkerTransitionStep): void;
    // How long the armed transition has left, in ms; 0 when none is armed.
    remainingMs(): number;
}

export function createMarkerTransition(el: HTMLElement, durationMs: number): MarkerTransition {
    // Mutable state in one const holder (let/var are banned — see the
    // vite.config.ts lint block and no-let.js). The generation guards the
    // self-clearing timeout below against a stale clear: two reflows in quick
    // succession (a rapid layout toggle) must not have the first reflow's
    // timeout wipe out the second reflow's still-running transition.
    // endsAtMs is the wall-clock end of the armed transition (0: none).
    const marker = { generation: 0, endsAtMs: 0 };
    function setActive(active: boolean): void {
        marker.generation += 1;
        const thisGeneration = marker.generation;
        if (!active) {
            marker.endsAtMs = 0;
            el.style.transition = "";
            return;
        }
        marker.endsAtMs = Date.now() + durationMs;
        el.style.transition = `left ${durationMs}ms linear, top ${durationMs}ms linear`;
        // Self-clearing: a reflow with no follow-up push (GPS momentarily
        // idle) must not leave the transition armed forever, where it would
        // silently animate some unrelated later left/top write.
        setTimeout(() => {
            if (thisGeneration !== marker.generation) return;
            marker.endsAtMs = 0;
            el.style.transition = "";
        }, durationMs);
    }
    return {
        setActive,
        apply(step: MarkerTransitionStep): void {
            if (step !== "keep") setActive(step === "arm");
        },
        remainingMs(): number {
            return Math.max(0, marker.endsAtMs - Date.now());
        },
    };
}
