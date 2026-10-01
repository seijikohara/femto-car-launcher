import { describe, expect, it } from "vite-plus/test";
import {
    calibrateLens,
    type LensCalibration,
    lensGroundOffset,
    lensProbeDistancePx,
    lensYawDeg,
} from "./lens";

// A pinhole camera looking at the map centre, the model the lens must recover
// without being told its field of view: focal length [focalPx] in screen px,
// camera [distancePx] from the centre in flat (untilted) map px, tilted
// [tiltDeg] from straight down. Ground coordinates are camera-aligned flat px
// (x right, y forward); the result is screen px from the viewport centre (x
// right, y down).
interface Pinhole {
    focalPx: number;
    distancePx: number;
    tiltDeg: number;
}

function project(cam: Pinhole, gx: number, gy: number): { x: number; y: number } {
    const th = (cam.tiltDeg * Math.PI) / 180;
    const depth = cam.distancePx + gy * Math.sin(th);
    return {
        x: (cam.focalPx * gx) / depth,
        y: (-cam.focalPx * gy * Math.cos(th)) / depth,
    };
}

// What the page measures through Google's projection: the two probes ahead of
// and behind the centre, as vertical px from the centre's own pixel.
function probe(cam: Pinhole, heightPx: number) {
    const d = lensProbeDistancePx(heightPx);
    return {
        tiltDeg: cam.tiltDeg,
        probeDistancePx: d,
        aheadUpPx: -project(cam, 0, d).y,
        behindDownPx: project(cam, 0, -d).y,
        viewportHeightPx: heightPx,
    };
}

// A deterministic stand-in for Math.random (mulberry32), so a failure
// reproduces.
function seeded(seed: number): () => number {
    const s = { v: seed >>> 0 };
    return () => {
        s.v = (s.v + 0x6d2b79f5) >>> 0;
        const a = Math.imul(s.v ^ (s.v >>> 15), 1 | s.v);
        const b = (a + Math.imul(a ^ (a >>> 7), 61 | a)) ^ a;
        return ((b ^ (b >>> 14)) >>> 0) / 4294967296;
    };
}

// Random cameras across the plausible range: field of view 15–55°, tilt
// 5–67.5° (Google's vector ceiling), any viewport from a phone mount to a
// wide head unit, and a camera distance that need not equal the focal length.
function randomCameras(count: number) {
    const rand = seeded(442);
    return Array.from({ length: count }, () => {
        const widthPx = 360 + rand() * 1600;
        const heightPx = 360 + rand() * 900;
        const fovyDeg = 15 + rand() * 40;
        const focalPx = heightPx / 2 / Math.tan((fovyDeg * Math.PI) / 360);
        return {
            widthPx,
            heightPx,
            cam: {
                focalPx,
                distancePx: focalPx * (0.8 + rand() * 0.4),
                tiltDeg: 5 + rand() * 62.5,
            },
            // A chevron anywhere in the lower half, either side of centre.
            spot: { x: (rand() - 0.5) * 0.7 * widthPx, y: rand() * 0.45 * heightPx },
        };
    });
}

describe("calibrateLens", () => {
    it("recovers the focal length and the camera distance from two probes", () => {
        for (const { cam, heightPx } of randomCameras(200)) {
            const lens = calibrateLens(probe(cam, heightPx));
            expect(lens).not.toBeNull();
            expect((lens as LensCalibration).focalPx / cam.focalPx).toBeCloseTo(1, 9);
            expect((lens as LensCalibration).distancePx / cam.distancePx).toBeCloseTo(1, 9);
        }
    });

    it("refuses a flat projection, which carries no perspective to measure", () => {
        // A projection that ignores the tilt: both probes the same distance
        // from the centre.
        expect(
            calibrateLens({
                tiltDeg: 55,
                probeDistancePx: 128,
                aheadUpPx: 128,
                behindDownPx: 128,
                viewportHeightPx: 512,
            }),
        ).toBeNull();
    });

    it("refuses a map at tilt 0, where the probes cannot tell focal length from distance", () => {
        const cam = { focalPx: 1000, distancePx: 1000, tiltDeg: 0 };
        expect(calibrateLens(probe(cam, 512))).toBeNull();
    });

    it("refuses an implied field of view outside 10–60°", () => {
        const height = 512;
        const focalFor = (fovyDeg: number) => height / 2 / Math.tan((fovyDeg * Math.PI) / 360);
        for (const fovyDeg of [8, 65]) {
            const cam = { focalPx: focalFor(fovyDeg), distancePx: focalFor(fovyDeg), tiltDeg: 45 };
            expect(calibrateLens(probe(cam, height))).toBeNull();
        }
        const ok = { focalPx: focalFor(30), distancePx: focalFor(30), tiltDeg: 45 };
        expect(calibrateLens(probe(ok, height))).not.toBeNull();
    });

    it("refuses a probe behind the camera or a non-finite reading", () => {
        const base = {
            tiltDeg: 55,
            probeDistancePx: 128,
            aheadUpPx: 100,
            behindDownPx: 160,
            viewportHeightPx: 512,
        };
        expect(calibrateLens({ ...base, aheadUpPx: -5 })).toBeNull();
        expect(calibrateLens({ ...base, behindDownPx: Number.NaN })).toBeNull();
    });
});

describe("lensGroundOffset", () => {
    it("puts the fix exactly under the chevron through the measured perspective", () => {
        for (const { cam, heightPx, spot } of randomCameras(200)) {
            const lens = calibrateLens(probe(cam, heightPx));
            const ground = lensGroundOffset(lens, spot.x, spot.y, cam.tiltDeg);
            // The ground offset is flat px, x right and y down the screen;
            // the camera frame's forward is up.
            const at = project(cam, ground.x, -ground.y);
            expect(at.x).toBeCloseTo(spot.x, 6);
            expect(at.y).toBeCloseTo(spot.y, 6);
        }
    });

    it("is the screen offset itself while uncalibrated and on a flat map", () => {
        const lens = { focalPx: 900, distancePx: 900 };
        expect(lensGroundOffset(null, -120, 80, 55)).toEqual({ x: -120, y: 80 });
        expect(lensGroundOffset(lens, -120, 80, 0)).toEqual({ x: -120, y: 80 });
    });

    it("falls back to the screen offset for a spot at or above the horizon", () => {
        // 89° of tilt puts the horizon a hair above centre; a spot well above
        // it has no ground point under it.
        const lens = { focalPx: 900, distancePx: 900 };
        expect(lensGroundOffset(lens, 10, -900, 89)).toEqual({ x: 10, y: -900 });
    });
});

describe("lensYawDeg", () => {
    it("turns the road ahead vertical through the chevron, whatever the camera", () => {
        for (const { cam, heightPx, spot } of randomCameras(200)) {
            const lens = calibrateLens(probe(cam, heightPx));
            const ground = lensGroundOffset(lens, spot.x, spot.y, cam.tiltDeg);
            // The map turns to the travel heading minus the yaw, so the
            // direction of travel points [yaw] clockwise of the camera's
            // forward axis.
            const yaw = (lensYawDeg(lens, spot.x, cam.tiltDeg) * Math.PI) / 180;
            const lean = [20, 60, 150, 400].map((t) => {
                const at = project(
                    cam,
                    ground.x + t * Math.sin(yaw),
                    -ground.y + t * Math.cos(yaw),
                );
                return Math.abs(at.x - spot.x);
            });
            expect(Math.max(...lean)).toBeLessThan(1e-6);
        }
    });

    it("takes the sign of the chevron's side and vanishes on the centre line", () => {
        const lens = { focalPx: 900, distancePx: 900 };
        expect(lensYawDeg(lens, -180, 55)).toBeLessThan(0);
        expect(lensYawDeg(lens, 180, 55)).toBeGreaterThan(0);
        expect(lensYawDeg(lens, 0, 55)).toBe(0);
    });

    it("is 0 while uncalibrated and on a flat map", () => {
        expect(lensYawDeg(null, -180, 55)).toBe(0);
        expect(lensYawDeg({ focalPx: 900, distancePx: 900 }, -180, 0)).toBe(0);
    });
});
