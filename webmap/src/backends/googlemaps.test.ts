// Drives the real Google Maps page module against a fake google.maps.Map and a
// stub DOM, pinning the wiring the pure-function tests cannot reach: the map
// heading each fix produces, where the chevron is placed, the rendering-mode
// resolve, the pivot, and the gesture options. The test environment has no
// DOM, so the page's few element and window accesses are stubbed here.
import { afterEach, beforeEach, describe, expect, it, vi } from "vite-plus/test";
import type { PendingBridgeCalls } from "../bridge";
import { LAYOUT_REFLOW_MS, smoothedBearing } from "../camera";
import { markerDrop, markerXFraction } from "../style";
import { init } from "./googlemaps";

// A fake google.maps.Map: moveCamera applies at once (as the real one does)
// and fires the change events the page listens to, so the page's gesture
// windows see the same sequence they see on a device.
const fake = vi.hoisted(() => {
    type Listener = () => void;
    interface CameraOptions {
        center?: { lat: number; lng: number };
        zoom?: number;
        heading?: number;
        tilt?: number;
    }
    const maps: FakeMap[] = [];
    class FakeMap {
        center = { lat: 0, lng: 0 };
        zoom = 1;
        heading = 0;
        tilt = 0;
        // What getRenderingType() reports once tiles are in: the page reads it
        // at the first tilesloaded.
        renderingType = "VECTOR";
        // The map type's zoom ceiling: Google clamps a requested zoom to it.
        maxZoom = Number.POSITIVE_INFINITY;
        readonly moves: CameraOptions[] = [];
        readonly options: Record<string, unknown>;
        private readonly listeners = new Map<string, Listener[]>();
        constructor(_el: unknown, options: Record<string, unknown>) {
            this.options = options;
            maps.push(this);
        }
        moveCamera(camera: CameraOptions): void {
            this.moves.push({ ...camera });
            if (camera.center) this.center = { ...camera.center };
            if (camera.zoom !== undefined) this.changeZoom(Math.min(camera.zoom, this.maxZoom));
            if (camera.heading !== undefined && camera.heading !== this.heading) {
                this.heading = camera.heading;
                this.fire("heading_changed");
            }
            if (camera.tilt !== undefined && camera.tilt !== this.tilt) {
                this.tilt = camera.tilt;
                this.fire("tilt_changed");
            }
        }
        // A user's pinch: the zoom changes with no moveCamera behind it.
        pinchTo(zoom: number): void {
            this.changeZoom(zoom);
        }
        setMapTypeId(): void {}
        getCenter(): { lat(): number; lng(): number } {
            const { lat, lng } = this.center;
            return { lat: () => lat, lng: () => lng };
        }
        getZoom(): number {
            return this.zoom;
        }
        getHeading(): number {
            return this.heading;
        }
        getTilt(): number {
            return this.tilt;
        }
        getRenderingType(): string {
            return this.renderingType;
        }
        addListener(event: string, listener: Listener): { remove(): void } {
            this.listeners.set(event, [...(this.listeners.get(event) ?? []), listener]);
            return {
                remove: () => {
                    const rest = (this.listeners.get(event) ?? []).filter((l) => l !== listener);
                    this.listeners.set(event, rest);
                },
            };
        }
        fire(event: string): void {
            // remove() replaces the list rather than mutating it, so a
            // listener that removes itself (the page's tilesloaded) is safe.
            for (const listener of this.listeners.get(event) ?? []) listener();
        }
        private changeZoom(zoom: number): void {
            if (zoom === this.zoom) return;
            this.zoom = zoom;
            this.fire("zoom_changed");
        }
    }
    class FakeTrafficLayer {
        setMap(): void {}
    }
    return { FakeMap, FakeTrafficLayer, maps };
});

type FakeMap = InstanceType<typeof fake.FakeMap>;

vi.mock("@googlemaps/js-api-loader", () => ({
    setOptions: vi.fn(),
    importLibrary: vi.fn(async () => ({
        Map: fake.FakeMap,
        TrafficLayer: fake.FakeTrafficLayer,
    })),
}));

// The head unit's page and the host's default layout: markerPos 70 above a
// 10% bottom band, right-hand cards over 42.8% of the width.
const W = 853;
const H = 512;
const DROP = markerDrop(70, 0.1);
const MX = markerXFraction(0.428);
const FIX = { lat: 35.681, lng: 139.767 };

const reporter = { log: vi.fn(), report: vi.fn(), reportErrorThrottled: vi.fn() };

function noPendingCalls(): PendingBridgeCalls {
    return {
        updateCamera: null,
        setStyleUrl: null,
        setFeatures: null,
        setGoogleMapsOptions: null,
        setFollow: null,
        setNorthUp: null,
        onHostResume: false,
    };
}

// A latitude's Mercator y as a fraction of the world's height, growing north.
function mercatorNorth(lat: number): number {
    return Math.log(Math.tan(Math.PI / 4 + (lat * Math.PI) / 360)) / (2 * Math.PI);
}

// Where [loc] lands on screen, in px from the viewport centre (x right, y
// down), under the fake map's camera: flat Web Mercator, turned by the
// heading on a vector map (a raster map stays north-up).
function screenOf(
    map: FakeMap,
    loc: { lat: number; lng: number },
    vector: boolean,
): { x: number; y: number } {
    const worldPx = 256 * 2 ** map.zoom;
    const east = (((((loc.lng - map.center.lng) % 360) + 540) % 360) - 180) * (worldPx / 360);
    const south = (mercatorNorth(map.center.lat) - mercatorNorth(loc.lat)) * worldPx;
    const th = ((vector ? map.heading : 0) * Math.PI) / 180;
    return {
        x: Math.cos(th) * east + Math.sin(th) * south,
        y: -Math.sin(th) * east + Math.cos(th) * south,
    };
}

interface PushOptions {
    tilt?: number;
    zoom?: number;
}

// One host push, with the host's default layout.
function push(
    win: Window,
    fix: { lat: number; lng: number },
    bearing: number,
    { tilt = 55, zoom = 16 }: PushOptions = {},
): void {
    win.updateCamera(fix.lat, fix.lng, bearing, zoom, tilt, 70, 0.1, 0.428, 0, "#3367d6");
}

// Stub the DOM the page touches, then run its init against the fake map.
// [rendering] is the host's choice; [renderingType] is what the map reports
// at its first tilesloaded.
async function boot(rendering: string, renderingType: string) {
    const ripple = { part: "ripple" };
    const arrow = { part: "svg" };
    const path = { setAttribute: vi.fn(), getAttribute: () => null };
    const marker = {
        style: {
            left: "50%",
            top: "50%",
            transform: "",
            transition: "",
            display: "none",
            setProperty: vi.fn(),
        },
        classList: { remove: vi.fn(), toggle: vi.fn(), contains: () => false },
        querySelector: (selector: string) =>
            ({ ".ripple": ripple, svg: arrow, path })[selector] ?? null,
    };
    const win = {
        innerWidth: W,
        innerHeight: H,
        femtoBridge: {
            onMapEvent: vi.fn(),
            googleMapsApiKey: () => "key",
            googleMapsMapId: () => "map-id",
            googleMapsRendering: () => rendering,
            googleMapsColorScheme: () => "LIGHT",
        },
        addEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
    } as unknown as Window;
    const frames: Array<(now: number) => void> = [];
    vi.stubGlobal("window", win);
    vi.stubGlobal("document", {
        getElementById: (id: string) => ({ map: { style: {} }, "self-marker": marker })[id] ?? null,
        createElement: () => ({
            getContext: () => ({ getExtension: () => null, getParameter: () => "fake" }),
        }),
    });
    // index.html's sizes: the 64 px ripple and the 34 px arrow.
    vi.stubGlobal("getComputedStyle", (el: { part?: string }) => ({
        width: el.part === "ripple" ? "64px" : "34px",
    }));
    vi.stubGlobal("requestAnimationFrame", (callback: (now: number) => void) => {
        frames.push(callback);
        return frames.length;
    });
    vi.stubGlobal("performance", { now: () => Date.now() });
    await init(reporter, noPendingCalls());
    const map = fake.maps[fake.maps.length - 1];
    map.renderingType = renderingType;
    // Advance the clock by [ms], then run the animation frames queued so far.
    const advance = (ms: number): void => {
        vi.advanceTimersByTime(ms);
        for (const callback of frames.splice(0)) callback(Date.now());
    };
    const run = (count: number): void => {
        Array.from({ length: count }).forEach(() => advance(16));
    };
    return { win, map, marker, advance, run };
}

beforeEach(() => {
    vi.useFakeTimers({
        toFake: ["Date", "setTimeout", "clearTimeout", "setInterval", "clearInterval"],
    });
    vi.setSystemTime(1_000_000);
    fake.maps.length = 0;
    reporter.report.mockClear();
});

afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
});

describe("the Google Maps page", () => {
    it.each(["AUTO", "RASTER", "VECTOR"])(
        "switches rotation gestures off with the %s rendering choice",
        async (rendering) => {
            // Left unset, a Map ID's cloud configuration decides, and a
            // two-finger twist would fight the follow glide: while following,
            // the heading's gesture window reopens on every frame of a turn.
            const page = await boot(rendering, rendering === "RASTER" ? "RASTER" : "VECTOR");
            expect(page.map.options.headingInteractionEnabled).toBe(false);
            expect("heading" in page.map.options).toBe(rendering === "VECTOR");
        },
    );

    it("turns the map to every smoothed bearing, the chevron pointing straight up", async () => {
        // Every step of this run stays under the 4° the removed dead band held
        // back; the map must land on each smoothed bearing, with no residual
        // left for the chevron to carry.
        const page = await boot("VECTOR", "VECTOR");
        const run = [
            { fix: FIX, raw: 90 },
            { fix: { lat: 35.6811, lng: 139.7671 }, raw: 90.8 },
            { fix: { lat: 35.6812, lng: 139.7672 }, raw: 91.3 },
            { fix: { lat: 35.6813, lng: 139.7673 }, raw: 90.9 },
        ];
        run.reduce<number | null>((previous, { fix, raw }) => {
            push(page.win, fix, raw);
            page.run(70);
            const heading = smoothedBearing(previous, raw);
            expect(page.map.heading).toBe(heading);
            expect(page.marker.style.transform).toContain("rotateZ(0deg)");
            const at = screenOf(page.map, fix, true);
            expect(at.x).toBeCloseTo(0, 6);
            expect(at.y).toBeCloseTo(DROP * H, 6);
            return heading;
        }, null);
        expect(reporter.report).not.toHaveBeenCalledWith("follow", false);
    });

    it("centres the chevron on a tilted vector map, over the perspective's vanishing point", async () => {
        const page = await boot("VECTOR", "VECTOR");
        push(page.win, FIX, 90);
        expect(page.marker.style.left).toBe("50%");
        expect(page.marker.style.top).toBe(`${(0.5 + DROP) * 100}%`);
        const at = screenOf(page.map, FIX, true);
        expect(at.x).toBeCloseTo(0, 6);
        expect(at.y).toBeCloseTo(DROP * H, 6);
    });

    it("keeps the chevron beside the cards on a flat vector map", async () => {
        const page = await boot("VECTOR", "VECTOR");
        push(page.win, FIX, 90, { tilt: 0 });
        expect(page.marker.style.left).toBe(`${(0.5 - MX) * 100}%`);
        const at = screenOf(page.map, FIX, true);
        expect(at.x).toBeCloseTo(-MX * W, 6);
        expect(at.y).toBeCloseTo(DROP * H, 6);
    });

    it("keeps the chevron beside the cards on a raster map, which gets no heading or tilt", async () => {
        const page = await boot("RASTER", "RASTER");
        push(page.win, FIX, 45);
        expect(page.marker.style.left).toBe(`${(0.5 - MX) * 100}%`);
        expect(page.marker.style.transform).toBe("translate(-50%, -50%) rotateZ(45deg)");
        expect(page.map.moves.every((m) => m.heading === undefined && m.tilt === undefined)).toBe(
            true,
        );
        const at = screenOf(page.map, FIX, false);
        expect(at.x).toBeCloseTo(-MX * W, 6);
        expect(at.y).toBeCloseTo(DROP * H, 6);
    });

    it("glides the chevron and the camera together when the tilt drops to 0", async () => {
        const page = await boot("VECTOR", "VECTOR");
        push(page.win, FIX, 90);
        page.run(10);
        push(page.win, FIX, 90, { tilt: 0 });
        expect(page.marker.style.left).toBe(`${(0.5 - MX) * 100}%`);
        expect(page.marker.style.transition).toContain(`${LAYOUT_REFLOW_MS}ms linear`);
        page.advance(LAYOUT_REFLOW_MS / 4);
        expect(screenOf(page.map, FIX, true).x).toBeCloseTo((-MX * W) / 4, 6);
        page.run(20);
        expect(screenOf(page.map, FIX, true).x).toBeCloseTo(-MX * W, 6);
        expect(page.map.tilt).toBe(0);
    });

    it("turns a north-up flip about the chevron", async () => {
        const page = await boot("VECTOR", "VECTOR");
        push(page.win, FIX, 90);
        page.win.setNorthUp(true);
        expect(page.marker.style.transform).toContain("rotateZ(90deg)");
        const drift = Array.from({ length: 30 }, () => {
            page.advance(16);
            const at = screenOf(page.map, FIX, true);
            return Math.hypot(at.x, at.y - DROP * H);
        });
        expect(Math.max(...drift)).toBeLessThan(1e-5);
        expect(page.map.heading).toBe(0);
        expect(reporter.report).not.toHaveBeenCalledWith("follow", false);
    });

    it("resolves a vector request that renders raster: a snap under the chevron, then a glide", async () => {
        const page = await boot("VECTOR", "RASTER");
        push(page.win, FIX, 45);
        expect(page.marker.style.left).toBe("50%");
        page.map.moves.length = 0;
        page.map.fire("tilesloaded");
        // The snap: the fix under the chevron where it still is, north-up,
        // with no heading or tilt sent to the raster map.
        const snapped = screenOf(page.map, FIX, false);
        expect(snapped.x).toBeCloseTo(0, 6);
        expect(snapped.y).toBeCloseTo(DROP * H, 6);
        expect(page.marker.style.left).toBe(`${(0.5 - MX) * 100}%`);
        expect(page.marker.style.transition).toContain(`${LAYOUT_REFLOW_MS}ms linear`);
        page.advance(LAYOUT_REFLOW_MS / 2);
        expect(screenOf(page.map, FIX, false).x).toBeCloseTo((-MX * W) / 2, 6);
        page.run(20);
        expect(screenOf(page.map, FIX, false).x).toBeCloseTo(-MX * W, 6);
        expect(page.map.moves.every((m) => m.heading === undefined && m.tilt === undefined)).toBe(
            true,
        );
    });

    it("resolves an AUTO map that renders vector: heading and tilt snap, then the chevron glides", async () => {
        const page = await boot("AUTO", "VECTOR");
        push(page.win, FIX, 45);
        // Placed as a raster map first: beside the cards, north-up.
        expect(page.marker.style.left).toBe(`${(0.5 - MX) * 100}%`);
        expect(page.map.moves.every((m) => m.heading === undefined && m.tilt === undefined)).toBe(
            true,
        );
        page.map.moves.length = 0;
        page.map.fire("tilesloaded");
        expect(page.map.moves[0]).toMatchObject({ heading: 45, tilt: 55 });
        expect(screenOf(page.map, FIX, true).x).toBeCloseTo(-MX * W, 6);
        expect(page.marker.style.left).toBe("50%");
        page.run(20);
        const at = screenOf(page.map, FIX, true);
        expect(at.x).toBeCloseTo(0, 6);
        expect(at.y).toBeCloseTo(DROP * H, 6);
        expect(reporter.report).not.toHaveBeenCalledWith("follow", false);
    });

    it("starts a re-follow where the map is after a zoom step the map clamped", async () => {
        // Google caps the zoom at the map type's ceiling; the glide still owns
        // the zoom it asked for, and its first frame must start from the
        // centre the map shows.
        const page = await boot("VECTOR", "VECTOR");
        page.map.maxZoom = 18;
        push(page.win, FIX, 90, { zoom: 18 });
        page.map.fire("dragstart");
        page.map.center = { lat: 35.69, lng: 139.75 };
        push(page.win, FIX, 90, { zoom: 19 });
        page.run(20);
        expect(page.map.zoom).toBe(18);
        const before = { ...page.map.center };
        page.win.setFollow(true);
        page.advance(0);
        const jump = screenOf(page.map, before, true);
        expect(Math.hypot(jump.x, jump.y)).toBeLessThan(1e-6);
    });

    it("still detects a user zoom while following a turn", async () => {
        const page = await boot("VECTOR", "VECTOR");
        push(page.win, FIX, 90);
        page.run(60);
        push(page.win, { lat: 35.6811, lng: 139.7671 }, 100);
        page.run(30);
        page.map.pinchTo(15.3);
        expect(reporter.report).toHaveBeenCalledWith("follow", false);
    });
});
