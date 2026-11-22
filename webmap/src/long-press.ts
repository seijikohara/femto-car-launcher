// The long press on the OSM map (#389 phase 3): a touch held still picks a
// point as a destination. MapLibre has no long-press event, so this module
// recognises one from the raw touch events. The logic is pure: the backend
// feeds it the touches, and the timer is injected, so the tests drive time.
//
// A press fires only while exactly one finger stays within the slop of where
// it went down for the whole hold. A second finger (a pinch or a two-finger
// rotate), a move beyond the slop, a lifted finger, or a map gesture the
// backend reports (a drag, zoom, rotate or pitch) cancels it, so a long press
// never fires during a drag or a pinch, and a drag never follows a fired
// press with a second report.

// How long a touch must be held. Android's own long press is 400 to 500 ms;
// a little longer here keeps a slow tap that rests on the map from picking a
// point by accident.
export const LONG_PRESS_MS = 600;

// How far, in CSS pixels, a held touch may wander and still count as still. A
// finger rests unsteadily on a screen in a moving car; MapLibre starts a drag
// after a few pixels anyway, and that drag cancels the press through
// `cancel()`, so this slop only absorbs the jitter of a finger that holds.
export const LONG_PRESS_SLOP_PX = 10;

export interface TouchPoint {
    readonly id: number;
    readonly x: number;
    readonly y: number;
}

export interface LongPressTimer {
    schedule: (callback: () => void, delayMs: number) => void;
}

export interface LongPressRecognizer {
    // touchstart: every touch now on the screen.
    start: (touches: readonly TouchPoint[]) => void;
    // touchmove: every touch now on the screen.
    move: (touches: readonly TouchPoint[]) => void;
    // touchend / touchcancel: a finger left the screen.
    end: () => void;
    // A map gesture began (drag, zoom, rotate, pitch).
    cancel: () => void;
}

const defaultTimer: LongPressTimer = {
    schedule: (callback, delayMs) => {
        setTimeout(callback, delayMs);
    },
};

export function createLongPressRecognizer(
    onLongPress: (x: number, y: number) => void,
    timer: LongPressTimer = defaultTimer,
): LongPressRecognizer {
    // Mutable state in a const holder (let/var are banned — see the lint block
    // in vite.config.ts and no-let.js). The generation retires a timer whose
    // press was cancelled: the timer API is not cancelled itself, so a press
    // that ends and a new one that starts within the hold cannot be fired by
    // the first press's timer.
    const state = {
        pressed: null as TouchPoint | null,
        generation: 0,
    };
    function cancel(): void {
        state.pressed = null;
        state.generation += 1;
    }
    return {
        start(touches) {
            cancel();
            if (touches.length !== 1) return;
            const pressed = touches[0];
            state.pressed = pressed;
            const generation = state.generation;
            timer.schedule(() => {
                if (generation !== state.generation || state.pressed === null) return;
                // One report per press: a further move or a drag finds nothing
                // to cancel, and the next touch starts afresh.
                cancel();
                onLongPress(pressed.x, pressed.y);
            }, LONG_PRESS_MS);
        },
        move(touches) {
            const pressed = state.pressed;
            if (pressed === null) return;
            const touch = touches.length === 1 ? touches[0] : null;
            if (touch === null || touch.id !== pressed.id) {
                cancel();
                return;
            }
            if (Math.hypot(touch.x - pressed.x, touch.y - pressed.y) > LONG_PRESS_SLOP_PX) cancel();
        },
        end: cancel,
        cancel,
    };
}

// The touches of a TouchEvent's `touches` list, in client pixels: the
// recogniser's input, and the frame `getBoundingClientRect` measures in.
export function touchPoints(
    touches: ArrayLike<{ identifier: number; clientX: number; clientY: number }>,
): TouchPoint[] {
    return Array.from(touches, (t) => ({ id: t.identifier, x: t.clientX, y: t.clientY }));
}

// The `longpress` event detail the host parses (MapLongPress.kt): latitude and
// longitude in degrees, six decimals (about 0.1 m), the precision of the
// host's geo: URIs.
export function longPressDetail(latitude: number, longitude: number): string {
    return `${latitude.toFixed(6)},${longitude.toFixed(6)}`;
}
