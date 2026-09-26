import { describe, expect, it } from "vite-plus/test";
import {
    type CameraMotion,
    followOrientation,
    linearEase,
    ORIENTATION_FLIP_MOTION,
    REFLOW_MOTION,
    REFOLLOW_MOTION,
    smoothedBearing,
} from "./camera";
import {
    anchorAt,
    type CameraPose,
    cameraCenterFor,
    createCameraGlide,
    type LatLng,
    lerpPose,
} from "./camera-glide";

const REST: CameraPose = { lat: 0, lng: 0, zoom: 10, heading: 0, tilt: 0, offsetX: 0, offsetY: 0 };
const SECOND: CameraMotion = { durationMs: 1_000, easing: linearEase };

// A head-unit-like layout on a flat map: the chevron 183 px left of and
// 75 px below the viewport centre.
const CHEVRON = { offsetX: -183, offsetY: 75 };
const TOKYO: LatLng = { lat: 35.681, lng: 139.767 };

// A latitude's Mercator y as a fraction of the world's height, growing north.
function mercatorNorth(lat: number): number {
    return Math.log(Math.tan(Math.PI / 4 + (lat * Math.PI) / 360)) / (2 * Math.PI);
}

// Where [loc] lands on screen, in px from the viewport centre (x right, y
// down), under a flat Web Mercator camera centred on [center] and turned
// clockwise by [heading]: the oracle the centre math is checked against,
// written apart from camera-glide.ts. 256 * 2^zoom px span the world.
function screenOffsetOf(
    loc: LatLng,
    center: LatLng,
    zoom: number,
    heading: number,
): { x: number; y: number } {
    const worldPx = 256 * 2 ** zoom;
    const east = (((((loc.lng - center.lng) % 360) + 540) % 360) - 180) * (worldPx / 360);
    const south = (mercatorNorth(center.lat) - mercatorNorth(loc.lat)) * worldPx;
    const th = (heading * Math.PI) / 180;
    return {
        x: Math.cos(th) * east + Math.sin(th) * south,
        y: -Math.sin(th) * east + Math.cos(th) * south,
    };
}

// The Google backend derives each frame's camera centre from the frame with
// cameraCenterFor; the frame's anchor must then sit at the frame's offset.
function expectAnchorAtChevron(frame: CameraPose, anchor: LatLng): void {
    const at = screenOffsetOf(anchor, cameraCenterFor(frame, frame), frame.zoom, frame.heading);
    expect(at.x).toBeCloseTo(frame.offsetX, 6);
    expect(at.y).toBeCloseTo(frame.offsetY, 6);
}

describe("cameraCenterFor", () => {
    it("puts the anchor at the chevron's screen offset at any heading", () => {
        for (const heading of [0, 33.3, 90, 180, 270]) {
            const center = cameraCenterFor(TOKYO, { zoom: 16, heading, ...CHEVRON });
            const at = screenOffsetOf(TOKYO, center, 16, heading);
            expect(at.x).toBeCloseTo(CHEVRON.offsetX, 6);
            expect(at.y).toBeCloseTo(CHEVRON.offsetY, 6);
        }
    });

    it("moves the centre east of the anchor for a screen-left offset at heading 0, south at 90", () => {
        const left = { zoom: 16, offsetX: -100, offsetY: 0 };
        const northUp = cameraCenterFor(TOKYO, { ...left, heading: 0 });
        expect(northUp.lng).toBeGreaterThan(TOKYO.lng);
        expect(northUp.lat).toBeCloseTo(TOKYO.lat, 12);
        const eastUp = cameraCenterFor(TOKYO, { ...left, heading: 90 });
        expect(eastUp.lat).toBeLessThan(TOKYO.lat);
        expect(eastUp.lng).toBeCloseTo(TOKYO.lng, 12);
    });

    it("spans the world's width in 256 px at zoom 0", () => {
        // 128 px at zoom 1 is a quarter of the world.
        const center = cameraCenterFor(
            { lat: 0, lng: 0 },
            { zoom: 1, heading: 0, offsetX: -128, offsetY: 0 },
        );
        expect(center.lng).toBeCloseTo(90, 9);
        expect(center.lat).toBeCloseTo(0, 9);
    });

    it("wraps a centre pushed across the antimeridian", () => {
        const center = cameraCenterFor(
            { lat: 0, lng: 179.9999 },
            { zoom: 16, heading: 0, offsetX: -500, offsetY: 0 },
        );
        expect(center.lng).toBeGreaterThanOrEqual(-180);
        expect(center.lng).toBeLessThan(-179.98);
    });
});

describe("anchorAt", () => {
    it("inverts cameraCenterFor at every quarter heading and across the antimeridian", () => {
        for (const heading of [0, 90, 180, 270]) {
            for (const anchor of [
                TOKYO,
                { lat: -33.9, lng: 179.9999 },
                { lat: 64.1, lng: -179.9999 },
            ]) {
                const view = { zoom: 16, heading, ...CHEVRON };
                const back = anchorAt(cameraCenterFor(anchor, view), view);
                expect(back.lat).toBeCloseTo(anchor.lat, 9);
                expect(back.lng).toBeCloseTo(anchor.lng, 9);
            }
        }
    });
});

describe("lerpPose", () => {
    it("interpolates zoom, tilt and the chevron's offset linearly", () => {
        // The offset glides linearly for the reason the marker's CSS
        // transition does: a reflow moves the two in lockstep.
        const to: CameraPose = { ...REST, zoom: 12, tilt: 40, offsetX: -180, offsetY: 80 };
        expect(lerpPose(REST, to, 0.25)).toEqual({
            ...REST,
            zoom: 10.5,
            tilt: 10,
            offsetX: -45,
            offsetY: 20,
        });
    });

    it("moves the anchor along the Mercator line, as MapLibre moves its centre", () => {
        const half = lerpPose(REST, { ...REST, lat: 60, lng: 20 }, 0.5);
        expect(half.lng).toBe(10);
        // Half-way up the Mercator y axis to 60° is atan(1/√2), not 30°.
        expect(half.lat).toBeCloseTo((Math.atan(Math.SQRT1_2) * 180) / Math.PI, 9);
    });

    it("rotates the heading the short way across the north seam", () => {
        expect(lerpPose({ ...REST, heading: 350 }, { heading: 10 }, 0.5).heading).toBe(0);
        expect(lerpPose({ ...REST, heading: 10 }, { heading: 350 }, 0.25).heading).toBe(5);
        expect(lerpPose({ ...REST, heading: 10 }, { heading: 350 }, 0.75).heading).toBe(355);
    });

    it("moves the anchor the short way across the antimeridian", () => {
        expect(lerpPose({ ...REST, lng: 179 }, { lng: -179 }, 0.25).lng).toBe(179.5);
        expect(lerpPose({ ...REST, lng: 179 }, { lng: -179 }, 0.75).lng).toBe(-179.5);
        expect(lerpPose({ ...REST, lng: -179 }, { lng: 179 }, 0.75).lng).toBe(179.5);
    });

    it("returns only the fields the target names", () => {
        expect(lerpPose(REST, { zoom: 12 }, 0.5)).toEqual({ zoom: 11 });
    });

    it("holds the target exactly when the start is within read-back noise of it", () => {
        // A camera read back from the map can differ from what was set by a
        // rounding hair; interpolating that hair would re-set the value every
        // frame (and, for zoom or tilt, keep its gesture window open).
        expect(lerpPose({ ...REST, zoom: 16 + 1e-10 }, { zoom: 16 }, 0.5).zoom).toBe(16);
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

    const TARGET: CameraPose = {
        lat: 0,
        lng: 1,
        zoom: 12,
        heading: 90,
        tilt: 0,
        offsetX: -100,
        offsetY: 40,
    };

    it("eases from the map's current camera to the target over the duration", () => {
        const h = harness();
        h.glide.to(TARGET, SECOND);
        expect(h.applied).toEqual([]);
        h.tick(250);
        expect(h.applied).toEqual([
            { lat: 0, lng: 0.25, zoom: 10.5, heading: 22.5, tilt: 0, offsetX: -25, offsetY: 10 },
        ]);
        h.tick(750);
        expect(h.applied[1]).toEqual(TARGET);
        expect(h.frames).toHaveLength(0);
    });

    it("shapes the progress with the easing curve", () => {
        const h = harness();
        h.glide.to(TARGET, { durationMs: 1_000, easing: (t) => t * t });
        h.tick(500);
        expect(h.applied[0]?.lng).toBe(0.25);
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
        // Half-way: lng 0.5. A new target from here eases from 0.5, not 0.
        h.glide.to({ ...TARGET, lng: 2 }, SECOND);
        h.tick(500);
        expect(h.applied[1]?.lng).toBe(1.25);
    });

    it("ignores the superseded glide's pending frame", () => {
        const h = harness();
        h.glide.to(TARGET, SECOND);
        h.glide.to({ ...TARGET, lng: 2 }, SECOND);
        // Both glides have a frame queued; only the live one may apply.
        h.tick(500);
        expect(h.applied).toEqual([
            { lat: 0, lng: 1, zoom: 11, heading: 45, tilt: 0, offsetX: -50, offsetY: 20 },
        ]);
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
        h.camera.lng = 5;
        h.glide.to({ ...TARGET, lng: 6 }, SECOND);
        h.tick(500);
        expect(h.applied[1]?.lng).toBe(5.5);
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

    it("lands the map heading exactly on every smoothed bearing, however small the turn", () => {
        // The removed Google dead band held the map on its last heading until
        // the bearing drifted 4° away, leaving the road and the arrow off
        // vertical by the residual. Every segment now lands on the heading the
        // fix asks for, as the OSM map's easeTo does.
        const h = harness();
        const home: CameraPose = {
            ...TOKYO,
            zoom: 16,
            heading: 90,
            tilt: 55,
            offsetX: 0,
            offsetY: 75,
        };
        h.glide.jump(home);
        [90, 90.8, 91.3, 90.9].reduce<number | null>((previous, raw) => {
            const heading = smoothedBearing(previous, raw);
            h.glide.to(
                { ...home, heading: followOrientation(false, heading, true).mapBearing },
                SECOND,
            );
            h.tick(1_000);
            expect(h.camera.heading).toBe(heading);
            return heading;
        }, null);
    });

    it("retargets a turn across north from the heading it has reached", () => {
        const h = harness();
        h.glide.jump({ ...TARGET, heading: 350 });
        h.glide.to({ ...TARGET, heading: 10 }, SECOND);
        h.tick(500);
        expect(h.camera.heading).toBe(0);
        h.glide.to({ ...TARGET, heading: 20 }, SECOND);
        h.tick(500);
        expect(h.camera.heading).toBe(10);
        h.tick(500);
        expect(h.camera.heading).toBe(20);
    });

    it("pivots a north-up flip on the chevron: the fix never leaves it", () => {
        const h = harness();
        const home: CameraPose = { ...TOKYO, zoom: 16, heading: 180, tilt: 0, ...CHEVRON };
        h.glide.jump({ ...home, heading: 0 });
        h.glide.to(home, ORIENTATION_FLIP_MOTION);
        Array.from({ length: 30 }).forEach(() => {
            h.tick(16);
            expectAnchorAtChevron(h.camera, TOKYO);
        });
        expect(h.camera.heading).toBe(180);
        // Interpolating the camera centre instead, as this glide once did, runs
        // the centre along the chord between the two end centres, which passes
        // over the fix half-way round: the fix then sits at the screen centre,
        // nearly 200 px from the chevron.
        const northUp = cameraCenterFor(TOKYO, { ...home, heading: 0 });
        const southUp = cameraCenterFor(TOKYO, home);
        const chord = {
            lat: (northUp.lat + southUp.lat) / 2,
            lng: (northUp.lng + southUp.lng) / 2,
        };
        const drift = screenOffsetOf(TOKYO, chord, 16, 270);
        expect(Math.hypot(drift.x - CHEVRON.offsetX, drift.y - CHEVRON.offsetY)).toBeGreaterThan(
            190,
        );
    });

    it("pivots a zoom step on the chevron", () => {
        const h = harness();
        const home: CameraPose = {
            ...TOKYO,
            zoom: 16,
            heading: 30,
            tilt: 45,
            offsetX: 0,
            offsetY: 75,
        };
        h.glide.jump({ ...home, zoom: 14 });
        h.glide.to(home, SECOND);
        Array.from({ length: 70 }).forEach(() => {
            h.tick(16);
            expectAnchorAtChevron(h.camera, TOKYO);
        });
        expect(h.camera.zoom).toBe(16);
    });

    it("eases a re-follow from the location under the chevron to the fix", () => {
        // After a pan the glide starts from what the map shows, read back as
        // the location under the chevron's spot; every frame then keeps the
        // eased location under the eased spot, as MapLibre's padded easeTo
        // does, and the last frame lands the fix under the chevron.
        const h = harness();
        const view = { zoom: 15, heading: 10, offsetX: 0, offsetY: 75 };
        const pannedCenter: LatLng = { lat: 35.7, lng: 139.74 };
        const underChevron = anchorAt(pannedCenter, view);
        Object.assign(h.camera, { ...underChevron, ...view, tilt: 45 });
        h.glide.release();
        const home: CameraPose = { ...TOKYO, zoom: 16, heading: 80, tilt: 45, ...CHEVRON };
        h.glide.to(home, REFOLLOW_MOTION);
        h.tick(1);
        // The first frame barely moves the camera off where the user left it.
        const firstCenter = cameraCenterFor(h.camera, h.camera);
        expect(Math.abs(firstCenter.lat - pannedCenter.lat)).toBeLessThan(1e-4);
        expect(Math.abs(firstCenter.lng - pannedCenter.lng)).toBeLessThan(1e-4);
        Array.from({ length: 40 }).forEach(() => {
            h.tick(16);
            expectAnchorAtChevron(h.camera, h.camera);
        });
        expect(h.camera).toEqual(home);
        expectAnchorAtChevron(h.camera, TOKYO);
    });

    it("glides the chevron's offset with the reflow motion, in step with the marker", () => {
        // The marker's CSS transition runs linearly for LAYOUT_REFLOW_MS; the
        // camera's offset must track it frame by frame, the fix under both.
        const h = harness();
        const home: CameraPose = { ...TOKYO, zoom: 16, heading: 30, tilt: 55, ...CHEVRON };
        h.glide.jump(home);
        h.glide.to({ ...home, offsetX: 0 }, REFLOW_MOTION);
        h.tick(REFLOW_MOTION.durationMs / 4);
        expect(h.camera.offsetX).toBeCloseTo(CHEVRON.offsetX * 0.75, 9);
        expectAnchorAtChevron(h.camera, TOKYO);
        h.tick(REFLOW_MOTION.durationMs);
        expect(h.camera.offsetX).toBe(0);
    });
});
