import { describe, expect, it } from "vite-plus/test";
import { type CameraMotion, linearEase } from "./camera";
import { type CameraPose, createCameraGlide, lerpPose } from "./camera-glide";

const REST: CameraPose = { lat: 0, lng: 0, zoom: 10, heading: 0, tilt: 0 };
const SECOND: CameraMotion = { durationMs: 1_000, easing: linearEase };

describe("lerpPose", () => {
    it("interpolates position, zoom and tilt linearly", () => {
        const to: CameraPose = { lat: 1, lng: 2, zoom: 12, heading: 0, tilt: 40 };
        expect(lerpPose(REST, to, 0.25)).toEqual({
            lat: 0.25,
            lng: 0.5,
            zoom: 10.5,
            heading: 0,
            tilt: 10,
        });
    });

    it("rotates the heading the short way across the north seam", () => {
        expect(lerpPose({ ...REST, heading: 350 }, { heading: 10 }, 0.5).heading).toBe(0);
        expect(lerpPose({ ...REST, heading: 10 }, { heading: 350 }, 0.25).heading).toBe(5);
        expect(lerpPose({ ...REST, heading: 10 }, { heading: 350 }, 0.75).heading).toBe(355);
    });

    it("returns only the fields the target names", () => {
        expect(lerpPose(REST, { zoom: 12 }, 0.5)).toEqual({ zoom: 11 });
    });

    it("holds the target exactly when the start is within read-back noise of it", () => {
        // A camera read back from the map can differ from what was set by a
        // rounding hair; interpolating that hair would re-set the value every
        // frame (and re-lay-out a vector map's labels each time).
        expect(lerpPose({ ...REST, heading: 47.3 + 1e-10 }, { heading: 47.3 }, 0.5).heading).toBe(
            47.3,
        );
    });
});

describe("createCameraGlide", () => {
    // A fake map, frame queue and clock: tick() advances the clock and runs
    // every frame requested so far in one go (as a browser runs all callbacks
    // queued for the next frame together), so each test drives the glide
    // deterministically. The map clamps zoom at [maxZoom], as Google clamps
    // to the map type's ceiling.
    function harness(maxZoom = Infinity) {
        const clock = { now: 1_000 };
        const frames: Array<(now: number) => void> = [];
        const applied: Array<Partial<CameraPose>> = [];
        const camera: CameraPose = { ...REST };
        const glide = createCameraGlide({
            current: () => ({ ...camera }),
            apply: (pose) => {
                applied.push(pose);
                Object.assign(camera, pose);
                camera.zoom = Math.min(camera.zoom, maxZoom);
            },
            requestFrame: (cb) => {
                frames.push(cb);
            },
            now: () => clock.now,
        });
        const tick = (ms: number): void => {
            clock.now += ms;
            for (const cb of frames.splice(0)) cb(clock.now);
        };
        return { glide, applied, frames, tick, camera };
    }

    const TARGET: CameraPose = { lat: 1, lng: 1, zoom: 12, heading: 90, tilt: 0 };

    it("eases from the map's current camera to the target over the duration", () => {
        const h = harness();
        h.glide.to(TARGET, SECOND);
        expect(h.applied).toEqual([]);
        h.tick(250);
        expect(h.applied).toEqual([{ lat: 0.25, lng: 0.25, zoom: 10.5, heading: 22.5, tilt: 0 }]);
        h.tick(750);
        expect(h.applied[1]).toEqual(TARGET);
        expect(h.frames).toHaveLength(0);
    });

    it("shapes the progress with the easing curve", () => {
        const h = harness();
        h.glide.to(TARGET, { durationMs: 1_000, easing: (t) => t * t });
        h.tick(500);
        expect(h.applied[0]?.lat).toBe(0.25);
    });

    it("clamps a late frame to the target and stops", () => {
        const h = harness();
        h.glide.to(TARGET, SECOND);
        h.tick(5_000);
        expect(h.applied).toEqual([TARGET]);
        expect(h.frames).toHaveLength(0);
    });

    it("eases only the fields a partial target names", () => {
        const h = harness();
        h.glide.to({ zoom: 12 }, SECOND);
        h.tick(500);
        expect(h.applied).toEqual([{ zoom: 11 }]);
    });

    it("jumps straight to the target without a frame", () => {
        const h = harness();
        h.glide.jump({ zoom: 14 });
        expect(h.applied).toEqual([{ zoom: 14 }]);
        expect(h.frames).toHaveLength(0);
    });

    it("treats a zero duration as a jump", () => {
        const h = harness();
        h.glide.to(TARGET, { durationMs: 0, easing: linearEase });
        expect(h.applied).toEqual([TARGET]);
        expect(h.frames).toHaveLength(0);
    });

    it("restarts a new glide from wherever the camera is now", () => {
        const h = harness();
        h.glide.to(TARGET, SECOND);
        h.tick(500);
        // Half-way: lat 0.5. A new target from here eases from 0.5, not 0.
        h.glide.to({ ...TARGET, lat: 2 }, SECOND);
        h.tick(500);
        expect(h.applied[1]?.lat).toBe(1.25);
    });

    it("ignores the superseded glide's pending frame", () => {
        const h = harness();
        h.glide.to(TARGET, SECOND);
        h.glide.to({ ...TARGET, lat: 2 }, SECOND);
        // Both glides have a frame queued; only the live one may apply.
        h.tick(500);
        expect(h.applied).toEqual([{ lat: 1, lng: 0.5, zoom: 11, heading: 45, tilt: 0 }]);
    });

    it("starts from the pose it last applied, not the map's clamped read-back", () => {
        // Google clamps zoom to the map type's ceiling; a glide that read the
        // clamped value back would re-interpolate toward the unreachable
        // target on every push, re-setting zoom (and reopening its gesture
        // window) every frame.
        const h = harness(18);
        h.glide.jump({ ...TARGET, zoom: 19 });
        expect(h.camera.zoom).toBe(18);
        h.glide.to({ ...TARGET, zoom: 19 }, SECOND);
        h.tick(500);
        expect(h.applied[1]?.zoom).toBe(19);
    });

    it("owns only the fields it applied; the rest come from the map", () => {
        const h = harness();
        h.glide.jump({ zoom: 14 });
        h.camera.lat = 5;
        h.glide.to({ ...TARGET, lat: 6 }, SECOND);
        h.tick(500);
        expect(h.applied[1]?.lat).toBe(5.5);
        expect(h.applied[1]?.zoom).toBe(13);
    });

    it("release stops the running glide", () => {
        const h = harness();
        h.glide.to(TARGET, SECOND);
        h.glide.release();
        h.tick(500);
        expect(h.applied).toEqual([]);
    });

    it("release hands the camera back: the next glide starts from what the map shows", () => {
        const h = harness(18);
        h.glide.jump({ ...TARGET, zoom: 19 });
        h.glide.release();
        h.glide.to({ ...TARGET, zoom: 20 }, SECOND);
        h.tick(500);
        expect(h.applied[1]?.zoom).toBe(19);
    });
});
