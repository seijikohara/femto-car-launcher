import { beforeEach, describe, expect, it } from "vite-plus/test";
import {
    createLongPressRecognizer,
    LONG_PRESS_MS,
    LONG_PRESS_SLOP_PX,
    type LongPressRecognizer,
    longPressDetail,
    type TouchPoint,
    touchPoints,
} from "./long-press";

// A fake clock: timers run when the test advances time past their due time.
function fakeTimer() {
    const clock = { now: 0, timers: [] as { dueMs: number; callback: () => void }[] };
    return {
        timer: {
            schedule: (callback: () => void, delayMs: number) => {
                clock.timers.push({ dueMs: clock.now + delayMs, callback });
            },
        },
        advance: (ms: number): void => {
            clock.now += ms;
            const due = clock.timers.filter((t) => t.dueMs <= clock.now);
            clock.timers = clock.timers.filter((t) => t.dueMs > clock.now);
            due.forEach((t) => t.callback());
        },
    };
}

const finger = (x: number, y: number, id = 1): TouchPoint => ({ id, x, y });

describe("createLongPressRecognizer", () => {
    const fired: { x: number; y: number }[] = [];
    const setup = (): { recognizer: LongPressRecognizer; advance: (ms: number) => void } => {
        const clock = fakeTimer();
        const recognizer = createLongPressRecognizer((x, y) => fired.push({ x, y }), clock.timer);
        return { recognizer, advance: clock.advance };
    };

    beforeEach(() => {
        fired.length = 0;
    });

    it("fires at the pressed point once one finger is held for the hold time", () => {
        const { recognizer, advance } = setup();
        recognizer.start([finger(100, 200)]);

        advance(LONG_PRESS_MS);

        expect(fired).toEqual([{ x: 100, y: 200 }]);
    });

    it("does not fire before the hold time", () => {
        const { recognizer, advance } = setup();
        recognizer.start([finger(100, 200)]);

        advance(LONG_PRESS_MS - 1);

        expect(fired).toEqual([]);
    });

    it("does not fire when the finger lifts before the hold time", () => {
        const { recognizer, advance } = setup();
        recognizer.start([finger(100, 200)]);
        advance(LONG_PRESS_MS / 2);

        recognizer.end();
        advance(LONG_PRESS_MS);

        expect(fired).toEqual([]);
    });

    it("tolerates a wander within the slop", () => {
        const { recognizer, advance } = setup();
        recognizer.start([finger(100, 200)]);

        recognizer.move([finger(100 + LONG_PRESS_SLOP_PX, 200)]);
        advance(LONG_PRESS_MS);

        expect(fired).toEqual([{ x: 100, y: 200 }]);
    });

    it("cancels on a move beyond the slop", () => {
        const { recognizer, advance } = setup();
        recognizer.start([finger(100, 200)]);

        recognizer.move([finger(100 + LONG_PRESS_SLOP_PX, 201)]);
        advance(LONG_PRESS_MS);

        expect(fired).toEqual([]);
    });

    it("cancels when a second finger comes down", () => {
        const { recognizer, advance } = setup();
        recognizer.start([finger(100, 200)]);

        recognizer.start([finger(100, 200), finger(300, 200, 2)]);
        advance(LONG_PRESS_MS);

        expect(fired).toEqual([]);
    });

    it("does not start with two fingers down", () => {
        const { recognizer, advance } = setup();

        recognizer.start([finger(100, 200), finger(300, 200, 2)]);
        advance(LONG_PRESS_MS);

        expect(fired).toEqual([]);
    });

    it("cancels when a move reports a second finger", () => {
        const { recognizer, advance } = setup();
        recognizer.start([finger(100, 200)]);

        recognizer.move([finger(100, 200), finger(300, 200, 2)]);
        advance(LONG_PRESS_MS);

        expect(fired).toEqual([]);
    });

    it("cancels when the map reports a gesture of its own", () => {
        const { recognizer, advance } = setup();
        recognizer.start([finger(100, 200)]);

        recognizer.cancel();
        advance(LONG_PRESS_MS);

        expect(fired).toEqual([]);
    });

    it("fires once per press, however long it is held", () => {
        const { recognizer, advance } = setup();
        recognizer.start([finger(100, 200)]);

        advance(LONG_PRESS_MS);
        recognizer.move([finger(100, 200)]);
        advance(LONG_PRESS_MS * 3);

        expect(fired).toHaveLength(1);
    });

    it("is not fired by the timer of a press that was cancelled", () => {
        const { recognizer, advance } = setup();
        recognizer.start([finger(100, 200)]);
        advance(LONG_PRESS_MS / 2);
        recognizer.end();

        // A new press inside the first one's hold: only its own timer counts.
        recognizer.start([finger(400, 50, 2)]);
        advance(LONG_PRESS_MS / 2);
        expect(fired).toEqual([]);

        advance(LONG_PRESS_MS / 2);
        expect(fired).toEqual([{ x: 400, y: 50 }]);
    });
});

describe("touchPoints", () => {
    it("keeps each touch's identifier and client position", () => {
        expect(
            touchPoints([
                { identifier: 3, clientX: 10, clientY: 20 },
                { identifier: 4, clientX: 30, clientY: 40 },
            ]),
        ).toEqual([
            { id: 3, x: 10, y: 20 },
            { id: 4, x: 30, y: 40 },
        ]);
    });
});

describe("longPressDetail", () => {
    it("joins latitude and longitude with six decimals, as the host parses them", () => {
        expect(longPressDetail(35.6812362, 139.7671248)).toBe("35.681236,139.767125");
    });

    it("keeps the sign of a southern and western point", () => {
        expect(longPressDetail(-33.8688, -151.2093)).toBe("-33.868800,-151.209300");
    });
});
