import { describe, expect, it } from "vite-plus/test";
import {
    BEARING_REPORT_INTERVAL_MS,
    createBearingReporter,
    frameSampleDetail,
    redactSecrets,
} from "./bridge";

describe("redactSecrets", () => {
    it("redacts an access_token query value", () => {
        expect(
            redactSecrets(
                "Failed to fetch https://tiles.example.com/styles/v1/standard?sdk=js-3.25.0&access_token=pk.abc.DEF-123",
            ),
        ).toBe(
            "Failed to fetch https://tiles.example.com/styles/v1/standard?sdk=js-3.25.0&access_token=<redacted>",
        );
    });

    it("redacts a Google Maps key query value", () => {
        expect(
            redactSecrets("https://maps.googleapis.com/maps/api/js?key=AIzaSyExample_-9&v=weekly"),
        ).toBe("https://maps.googleapis.com/maps/api/js?key=<redacted>&v=weekly");
    });

    it("redacts every credential parameter in one string", () => {
        expect(redactSecrets("a?token=t1&x=1&api_key=k2&apikey=k3")).toBe(
            "a?token=<redacted>&x=1&api_key=<redacted>&apikey=<redacted>",
        );
    });

    it("keeps credential-free details unchanged", () => {
        const plain =
            "style-load-failed: Failed to fetch https://tiles.openfreemap.org/styles/positron";
        expect(redactSecrets(plain)).toBe(plain);
    });
});

// A fake clock and timer queue: tick() advances the clock, flush() runs
// the timers that have come due — kept separate so a report can arrive
// between a timer's due time and its callback, as in a browser.
function bearingHarness() {
    const clock = { now: 10_000 };
    const timers: Array<{ at: number; run: () => void }> = [];
    const sent: string[] = [];
    const onBearing = createBearingReporter((kind, detail) => sent.push(`${kind}=${detail}`), {
        now: () => clock.now,
        schedule: (run, delayMs) => {
            timers.push({ at: clock.now + delayMs, run });
        },
    });
    const tick = (ms: number): void => {
        clock.now += ms;
    };
    const flush = (): void => {
        for (const timer of timers.splice(0)) {
            if (timer.at <= clock.now) timer.run();
            else timers.push(timer);
        }
    };
    return { onBearing, sent, tick, flush };
}

describe("createBearingReporter", () => {
    it("reports the first bearing at once, rounded to a tenth", () => {
        const h = bearingHarness();
        h.onBearing(47.26);
        expect(h.sent).toEqual(["bearing=47.3"]);
    });

    it("holds bearings inside the interval and reports the latest when it ends", () => {
        const h = bearingHarness();
        h.onBearing(10);
        h.tick(50);
        h.onBearing(12);
        h.tick(50);
        h.onBearing(14);
        expect(h.sent).toEqual(["bearing=10.0"]);
        // The source stops changing here (a glide has landed); the last
        // value must still reach the compass.
        h.tick(50);
        h.flush();
        expect(h.sent).toEqual(["bearing=10.0", "bearing=14.0"]);
    });

    it("reports a bearing arriving after the interval at once", () => {
        const h = bearingHarness();
        h.onBearing(10);
        h.tick(BEARING_REPORT_INTERVAL_MS);
        h.onBearing(20);
        expect(h.sent).toEqual(["bearing=10.0", "bearing=20.0"]);
    });

    it("never repeats the bearing it last sent", () => {
        const h = bearingHarness();
        h.onBearing(10);
        h.tick(50);
        h.onBearing(10.04);
        h.tick(100);
        h.flush();
        h.tick(BEARING_REPORT_INTERVAL_MS);
        h.onBearing(10);
        expect(h.sent).toEqual(["bearing=10.0"]);
    });

    it("drops a held bearing that a later immediate report supersedes", () => {
        const h = bearingHarness();
        h.onBearing(10);
        h.tick(50);
        h.onBearing(12);
        // The hold's timer is due at +150; a report at +160 runs first (the
        // event and the timer are separate tasks) and wins.
        h.tick(110);
        h.onBearing(30);
        h.flush();
        expect(h.sent).toEqual(["bearing=10.0", "bearing=30.0"]);
    });

    it("keeps the interval when a late timer meets a newer immediate report", () => {
        const h = bearingHarness();
        h.onBearing(10);
        h.tick(50);
        h.onBearing(12);
        // The timer due at +150 runs late: a report at +160 is sent at once,
        // another at +170 is held, and the late timer runs at +170 — it must
        // not send the held value 10 ms after the previous report.
        h.tick(110);
        h.onBearing(30);
        h.tick(10);
        h.onBearing(40);
        h.flush();
        expect(h.sent).toEqual(["bearing=10.0", "bearing=30.0"]);
        h.tick(BEARING_REPORT_INTERVAL_MS);
        h.flush();
        expect(h.sent).toEqual(["bearing=10.0", "bearing=30.0", "bearing=40.0"]);
    });
});

describe("frameSampleDetail", () => {
    it("reports the median and worst interval, rounded, with the sample count", () => {
        expect(frameSampleDetail([16.7, 33.4, 16.6, 100.2, 16.7])).toBe(
            "median=17,worst=100,samples=5",
        );
    });

    it("is defined for an empty run", () => {
        expect(frameSampleDetail([])).toBe("median=0,worst=0,samples=0");
    });
});
