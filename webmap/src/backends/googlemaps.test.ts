// Drives the real Google Maps page module against a fake google.maps.Map and a
// stub DOM, pinning the wiring the pure-function tests cannot reach: the map
// heading each fix produces, where the chevron is placed, the lens that
// measures the tilted map's perspective, the rendering-mode resolve, the
// pivot, and the gesture options. The test environment has no DOM, so the
// page's few element and window accesses are stubbed here.
import { afterEach, beforeEach, describe, expect, it, vi } from "vite-plus/test";
import type { PendingBridgeCalls } from "../bridge";
import { LAYOUT_REFLOW_MS, smoothedBearing } from "../camera";
import { markerDrop, markerXFraction } from "../style";
import { init } from "./googlemaps";

// A fake google.maps.Map: moveCamera applies at once (as the real one does)
// and fires the change events the page listens to, so the page's gesture
// windows see the same sequence they see on a device. Its projection is a
// real pinhole camera whose field of view (optics.fovyDeg) the page is never
// told, so a tilted vector map shows a perspective the page must measure.
const fake = vi.hoisted(() => {
    type Listener = () => void;
    interface LatLng {
        lat: number;
        lng: number;
    }
    interface CameraOptions {
        center?: LatLng;
        zoom?: number;
        heading?: number;
        tilt?: number;
    }
    const maps: FakeMap[] = [];
    // The test's camera: the vertical field of view, and whether the 2D
    // MapCanvasProjection ignores the tilt (an implausible reading the page
    // must refuse) while the map itself still draws in perspective.
    const optics = { fovyDeg: 30, flatCanvas: false };
    // google.maps.OverlayView: the page subclasses it and reads the
    // projection in draw(), which the map calls as it renders.
    class FakeOverlayView {
        private host: FakeMap | null = null;
        setMap(map: FakeMap | null): void {
            this.host = map;
            if (!map) return;
            map.overlays.push(this);
            this.onAdd();
        }
        getProjection(): { fromLatLngToContainerPixel(latLng: LatLng): { x: number; y: number } } {
            const host = this.host as FakeMap;
            return {
                fromLatLngToContainerPixel: (latLng) => host.project(latLng, !optics.flatCanvas),
            };
        }
        onAdd(): void {}
        draw(): void {}
        onRemove(): void {}
    }
    // google.maps.WebGLOverlayView: Google adds one only to a map with a Map
    // ID; onDraw gets the transformer whose fromLatLngAltitude is the MVP
    // matrix (column-major) of a frame at that point, whose translation
    // column is the point's clip position.
    class FakeWebGLOverlayView {
        redrawRequests = 0;
        setMap(map: FakeMap | null): void {
            if (!map || !map.options.mapId) return;
            map.webglOverlays.push(this);
            this.onAdd();
            this.onContextRestored({ gl: {} });
        }
        requestRedraw(): void {
            this.redrawRequests += 1;
        }
        onAdd(): void {}
        onContextRestored(_options: unknown): void {}
        onDraw(_options: unknown): void {}
        onContextLost(): void {}
        onRemove(): void {}
    }
    class FakeMap {
        // A latitude's Mercator y as a fraction of the world's height,
        // growing north.
        static mercatorNorth(lat: number): number {
            return Math.log(Math.tan(Math.PI / 4 + (lat * Math.PI) / 360)) / (2 * Math.PI);
        }
        static viewport(): { width: number; height: number } {
            const win = (
                globalThis as unknown as { window: { innerWidth: number; innerHeight: number } }
            ).window;
            return { width: win.innerWidth, height: win.innerHeight };
        }
        center = { lat: 0, lng: 0 };
        zoom = 1;
        heading = 0;
        tilt = 0;
        // What getRenderingType() reports once tiles are in: the page reads it
        // at the first tilesloaded.
        renderingType = "VECTOR";
        // The map type's zoom ceiling: Google clamps a requested zoom to it.
        maxZoom = Number.POSITIVE_INFINITY;
        // The vector tilt ceiling at the zoom: Google clamps a requested tilt.
        maxTilt = Number.POSITIVE_INFINITY;
        readonly moves: CameraOptions[] = [];
        readonly options: Record<string, unknown>;
        readonly overlays: FakeOverlayView[] = [];
        readonly webglOverlays: FakeWebGLOverlayView[] = [];
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
            const tilt =
                camera.tilt === undefined ? undefined : Math.min(camera.tilt, this.maxTilt);
            if (tilt !== undefined && tilt !== this.tilt) {
                this.tilt = tilt;
                this.fire("tilt_changed");
            }
        }
        // A rendered frame: the overlays redraw against the camera it shows.
        render(): void {
            for (const overlay of this.overlays) overlay.draw();
            const { width, height } = FakeMap.viewport();
            const transformer = {
                fromLatLngAltitude: (at: LatLng & { altitude?: number }) => {
                    const px = this.project(at, true);
                    // Any positive w: the page must divide it out.
                    const w = 1.7;
                    const m = new Float64Array(16);
                    m[12] = ((2 * px.x) / width - 1) * w;
                    m[13] = (1 - (2 * px.y) / height) * w;
                    m[15] = w;
                    return m;
                },
                getCameraParams: () => {
                    const { lat, lng } = this.center;
                    return {
                        center: { lat: () => lat, lng: () => lng },
                        heading: this.heading,
                        tilt: this.tilt,
                        zoom: this.zoom,
                    };
                },
            };
            for (const overlay of this.webglOverlays) overlay.onDraw({ gl: {}, transformer });
        }
        // Where [loc] lands in the container, in px: flat Web Mercator turned
        // by the heading, then seen through the pinhole camera at the tilt,
        // whose focal length and distance both follow from the field of view
        // (so tilt 0 is the flat map). A raster map is flat and north-up.
        // Without [perspective] the tilt is ignored.
        project(loc: LatLng, perspective = true): { x: number; y: number } {
            const { width, height } = FakeMap.viewport();
            const vector = this.renderingType === "VECTOR";
            const worldPx = 256 * 2 ** this.zoom;
            const east =
                (((((loc.lng - this.center.lng) % 360) + 540) % 360) - 180) * (worldPx / 360);
            const south =
                (FakeMap.mercatorNorth(this.center.lat) - FakeMap.mercatorNorth(loc.lat)) * worldPx;
            const th = ((vector ? this.heading : 0) * Math.PI) / 180;
            const x = Math.cos(th) * east + Math.sin(th) * south;
            const forward = Math.sin(th) * east - Math.cos(th) * south;
            const tilt = ((vector ? this.tilt : 0) * Math.PI) / 180;
            if (!perspective || tilt === 0) return { x: width / 2 + x, y: height / 2 - forward };
            const focal = height / 2 / Math.tan((optics.fovyDeg * Math.PI) / 360);
            const depth = focal + forward * Math.sin(tilt);
            return {
                x: width / 2 + (focal * x) / depth,
                y: height / 2 - (focal * forward * Math.cos(tilt)) / depth,
            };
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
    return { FakeMap, FakeOverlayView, FakeTrafficLayer, FakeWebGLOverlayView, optics, maps };
});

type FakeMap = InstanceType<typeof fake.FakeMap>;

vi.mock("@googlemaps/js-api-loader", () => ({
    setOptions: vi.fn(),
    importLibrary: vi.fn(async () => ({
        Map: fake.FakeMap,
        OverlayView: fake.FakeOverlayView,
        WebGLOverlayView: fake.FakeWebGLOverlayView,
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

// Where [loc] lands on screen, in px from the viewport centre (x right, y
// down), through the fake map's perspective camera.
function screenOf(map: FakeMap, loc: { lat: number; lng: number }): { x: number; y: number } {
    const at = map.project(loc);
    const win = window as unknown as { innerWidth: number; innerHeight: number };
    return { x: at.x - win.innerWidth / 2, y: at.y - win.innerHeight / 2 };
}

// The location [eastPx] / [northPx] flat px from [loc] at [zoom].
function offsetBy(
    loc: { lat: number; lng: number },
    eastPx: number,
    northPx: number,
    zoom: number,
): { lat: number; lng: number } {
    const worldPx = 256 * 2 ** zoom;
    const north = fake.FakeMap.mercatorNorth(loc.lat) + northPx / worldPx;
    return {
        lat: (360 / Math.PI) * Math.atan(Math.exp(2 * Math.PI * north)) - 90,
        lng: loc.lng + (eastPx * 360) / worldPx,
    };
}

// The location [px] flat px from [loc] along [bearing].
function aheadOf(
    loc: { lat: number; lng: number },
    bearing: number,
    px: number,
    zoom = 16,
): { lat: number; lng: number } {
    const b = (bearing * Math.PI) / 180;
    return offsetBy(loc, px * Math.sin(b), px * Math.cos(b), zoom);
}

// The camera centre that puts [loc] at screen offset ([x], [y]) px under a
// flat Web Mercator camera at [zoom] and [heading]: the inverse of screenOf
// at tilt 0.
function centreShowing(
    loc: { lat: number; lng: number },
    x: number,
    y: number,
    zoom: number,
    heading: number,
): { lat: number; lng: number } {
    const th = (heading * Math.PI) / 180;
    const east = Math.cos(th) * x - Math.sin(th) * y;
    const south = Math.sin(th) * x + Math.cos(th) * y;
    return offsetBy(loc, -east, south, zoom);
}

// How far the road ahead leans at the chevron: the horizontal px between the
// fix and a point further along [bearing] on screen.
function leanPx(map: FakeMap, fix: { lat: number; lng: number }, bearing: number): number {
    return Math.abs(screenOf(map, aheadOf(fix, bearing, 120)).x - screenOf(map, fix).x);
}

// The bearing the page last reported to the host's compass.
function lastCompass(): string | undefined {
    const calls = reporter.report.mock.calls.filter(([kind]) => kind === "bearing");
    return calls.at(-1)?.[1] as string | undefined;
}

interface PushOptions {
    tilt?: number;
    zoom?: number;
    rightSafe?: number;
}

// One host push, with the host's default layout.
function push(
    win: Window,
    fix: { lat: number; lng: number },
    bearing: number,
    { tilt = 55, zoom = 16, rightSafe = 0.428 }: PushOptions = {},
): void {
    win.updateCamera(fix.lat, fix.lng, bearing, zoom, tilt, 70, 0.1, rightSafe, 0, "#3367d6");
}

interface BootOptions {
    // The Cloud Map ID the host passes; "" for none.
    mapId?: string;
    width?: number;
    height?: number;
}

// Stub the DOM the page touches, then run its init against the fake map.
// [rendering] is the host's choice; [renderingType] is what the map reports
// at its first tilesloaded.
async function boot(
    rendering: string,
    renderingType: string,
    { mapId = "map-id", width = W, height = H }: BootOptions = {},
) {
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
        querySelector: (selector: string) => (selector === "path" ? path : null),
    };
    const win = {
        innerWidth: width,
        innerHeight: height,
        femtoBridge: {
            onMapEvent: vi.fn(),
            googleMapsApiKey: () => "key",
            googleMapsMapId: () => mapId,
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
    vi.stubGlobal("requestAnimationFrame", (callback: (now: number) => void) => {
        frames.push(callback);
        return frames.length;
    });
    vi.stubGlobal("performance", { now: () => Date.now() });
    await init(reporter, noPendingCalls());
    const map = fake.maps[fake.maps.length - 1];
    map.renderingType = renderingType;
    // Advance the clock by [ms], run the animation frames queued so far, then
    // render the frame they produced (the map redraws its overlays).
    const advance = (ms: number): void => {
        vi.advanceTimersByTime(ms);
        for (const callback of frames.splice(0)) callback(Date.now());
        map.render();
    };
    const run = (count: number): void => {
        Array.from({ length: count }).forEach(() => advance(16));
    };
    return { win, map, marker, advance, run };
}

// Make Date.now advance by 1 ms on every call, as a real clock can between
// two reads; returns the restore.
function tickingClock(): () => void {
    const base = Date.now();
    const clock = { calls: 0 };
    const spy = vi.spyOn(Date, "now").mockImplementation(() => {
        clock.calls += 1;
        return base + clock.calls;
    });
    return () => spy.mockRestore();
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

beforeEach(() => {
    vi.useFakeTimers({
        toFake: ["Date", "setTimeout", "clearTimeout", "setInterval", "clearInterval"],
    });
    vi.setSystemTime(1_000_000);
    fake.maps.length = 0;
    fake.optics.fovyDeg = 30;
    fake.optics.flatCanvas = false;
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

    // Random cameras the page is never told about: field of view, viewport
    // and the fixes' run of headings vary; the tilt is 0°, 45° or the
    // default 55°.
    const rand = seeded(442);
    const cameras = [0, 45, 55].flatMap((tilt) =>
        Array.from({ length: 4 }, () => ({
            tilt,
            fovyDeg: 15 + rand() * 40,
            width: Math.round(480 + rand() * 1200),
            height: Math.round(360 + rand() * 600),
        })),
    );
    it.each(cameras)(
        "holds the fix under the chevron beside the cards, the road ahead vertical (tilt $tilt°, fovy $fovyDeg°, $width×$height)",
        async ({ tilt, fovyDeg, width, height }) => {
            fake.optics.fovyDeg = fovyDeg;
            const page = await boot("VECTOR", "VECTOR", { width, height });
            const run = [90, 90.8, 135, 200, 10];
            run.reduce<{ fix: { lat: number; lng: number }; heading: number | null }>(
                (previous, raw) => {
                    const fix = aheadOf(previous.fix, raw, 40);
                    push(page.win, fix, raw, { tilt });
                    // Past the longest cadence-matched glide (MAX_EASE_MS)
                    // and the compass throttle after it: the glide lasts as
                    // long as the interval since the previous push.
                    page.run(140);
                    const heading = smoothedBearing(previous.heading, raw);
                    // The chevron sits at the OSM spot, pointing straight up.
                    expect(page.marker.style.left).toBe(`${(0.5 - MX) * 100}%`);
                    expect(page.marker.style.transform).toContain("rotateZ(0deg)");
                    // The fix lands under it, well within a pixel...
                    const at = screenOf(page.map, fix);
                    expect(Math.hypot(at.x + MX * width, at.y - DROP * height)).toBeLessThan(1e-6);
                    // ...the direction of travel runs straight up through it...
                    expect(leanPx(page.map, fix, heading)).toBeLessThan(1e-6);
                    // ...and the compass shows the travel heading, not the
                    // map's biased one.
                    expect(lastCompass()).toBe(heading.toFixed(1));
                    return { fix, heading };
                },
                { fix: FIX, heading: null },
            );
            expect(reporter.report).not.toHaveBeenCalledWith("follow", false);
        },
    );

    it("biases the heading of a tilted map toward the chevron's side", async () => {
        const page = await boot("VECTOR", "VECTOR");
        push(page.win, FIX, 90);
        page.run(50);
        // The chevron sits left of centre, so the map turns past the travel
        // heading: the road ahead points left of the camera's forward axis.
        expect(page.map.heading).toBeGreaterThan(90.5);
        expect(page.map.heading).toBeLessThan(110);
    });

    it("keeps the chevron at the OSM spot with no yaw until the lens is measured", async () => {
        const page = await boot("VECTOR", "VECTOR");
        push(page.win, FIX, 90);
        // No frame has rendered, so nothing has been measured yet: the
        // chevron still takes the OSM spot, and the map turns to the travel
        // heading itself (the road ahead leans until the lens is measured).
        expect(page.marker.style.left).toBe(`${(0.5 - MX) * 100}%`);
        expect(page.map.heading).toBe(90);
        expect(leanPx(page.map, FIX, 90)).toBeGreaterThan(1);
        // The first frame measures it, and the camera glides to the lens's
        // yaw and exact anchor without waiting for the next fix.
        page.advance(16);
        expect(page.marker.style.left).toBe(`${(0.5 - MX) * 100}%`);
        page.run(30);
        const at = screenOf(page.map, FIX);
        expect(Math.hypot(at.x + MX * W, at.y - DROP * H)).toBeLessThan(1e-6);
        expect(leanPx(page.map, FIX, 90)).toBeLessThan(1e-6);
        expect(reporter.report).not.toHaveBeenCalledWith("follow", false);
    });

    it("lands the chevron and the camera together when a fix arrives mid-glide", async () => {
        const page = await boot("VECTOR", "VECTOR");
        push(page.win, FIX, 90);
        page.run(50);
        // The cards go away: the chevron glides to the centre line.
        push(page.win, FIX, 90, { rightSafe: 0 });
        page.advance(200);
        const next = aheadOf(FIX, 90, 10);
        // A device clock moves on between any two reads of it, so the push
        // must decide on one reading of the time the chevron's glide has
        // left, not compare two.
        const restoreClock = tickingClock();
        push(page.win, next, 90, { rightSafe: 0 });
        restoreClock();
        // The chevron keeps gliding; the camera moves over the ~60 ms the
        // glide has left rather than the 200 ms since the last fix, so the
        // two land together.
        expect(page.marker.style.transition).toContain(`${LAYOUT_REFLOW_MS}ms linear`);
        page.advance(LAYOUT_REFLOW_MS - 200 + 16);
        const at = screenOf(page.map, next);
        expect(Math.hypot(at.x, at.y - DROP * H)).toBeLessThan(1e-6);
    });

    it("re-places the camera when a viewport resize changes the lens, without a fix", async () => {
        const page = await boot("VECTOR", "VECTOR");
        push(page.win, FIX, 90);
        page.run(50);
        // An orientation change or a split screen: the map re-renders at the
        // new size, the lens is re-measured, and the parked car sends no fix.
        const win = page.win as unknown as { innerWidth: number; innerHeight: number };
        win.innerWidth = 700;
        win.innerHeight = 380;
        page.run(30);
        const at = screenOf(page.map, FIX);
        expect(Math.hypot(at.x + MX * 700, at.y - DROP * 380)).toBeLessThan(1e-6);
        expect(leanPx(page.map, FIX, 90)).toBeLessThan(1e-6);
    });

    it("corrects for the tilt the map shows when Google clamps the requested one", async () => {
        const page = await boot("VECTOR", "VECTOR");
        page.map.maxTilt = 40;
        push(page.win, FIX, 90);
        page.run(50);
        expect(page.map.tilt).toBe(40);
        const at = screenOf(page.map, FIX);
        expect(Math.hypot(at.x + MX * W, at.y - DROP * H)).toBeLessThan(1e-6);
        expect(leanPx(page.map, FIX, 90)).toBeLessThan(1e-6);
        expect(lastCompass()).toBe("90.0");
    });

    it("measures the lens through the WebGL overlay on a map with a Map ID", async () => {
        // The 2D projection shows no perspective here; the transformer does.
        fake.optics.flatCanvas = true;
        const page = await boot("VECTOR", "VECTOR");
        push(page.win, FIX, 90);
        page.run(50);
        const at = screenOf(page.map, FIX);
        expect(Math.hypot(at.x + MX * W, at.y - DROP * H)).toBeLessThan(1e-6);
        expect(leanPx(page.map, FIX, 90)).toBeLessThan(1e-6);
        // The WebGL overlay is the measurement, it draws nothing, and it asks
        // for no frames beyond the ones the map renders.
        expect(page.map.webglOverlays).toHaveLength(1);
        expect(page.map.overlays).toHaveLength(0);
        expect(page.map.webglOverlays[0].redrawRequests).toBe(0);
    });

    it("measures the lens through the OverlayView projection without a Map ID", async () => {
        const page = await boot("VECTOR", "VECTOR", { mapId: "" });
        push(page.win, FIX, 90);
        page.run(50);
        const at = screenOf(page.map, FIX);
        expect(Math.hypot(at.x + MX * W, at.y - DROP * H)).toBeLessThan(1e-6);
        expect(leanPx(page.map, FIX, 90)).toBeLessThan(1e-6);
        expect(page.map.overlays).toHaveLength(1);
    });

    it("keeps the OSM spot with no yaw when the projection shows no perspective", async () => {
        fake.optics.flatCanvas = true;
        const page = await boot("VECTOR", "VECTOR", { mapId: "" });
        push(page.win, FIX, 90);
        page.run(50);
        expect(page.marker.style.left).toBe(`${(0.5 - MX) * 100}%`);
        expect(page.map.heading).toBe(90);
    });

    // What the page told the host's diagnostics about its lens.
    function lensReports(): string[] {
        return reporter.report.mock.calls
            .filter(([kind]) => kind === "lens")
            .map(([, detail]) => detail as string);
    }

    it("reports the measured lens, its source and field of view, to the diagnostics once", async () => {
        fake.optics.fovyDeg = 27.3;
        const page = await boot("VECTOR", "VECTOR");
        push(page.win, FIX, 90);
        page.run(50);
        push(page.win, aheadOf(FIX, 90, 20), 90);
        page.run(50);
        expect(lensReports()).toEqual(["measured,source=webgl,fovy=27.3"]);
    });

    it("reports the 2D-projection lens without a Map ID, and an unmeasured one", async () => {
        const measured = await boot("VECTOR", "VECTOR", { mapId: "" });
        push(measured.win, FIX, 90);
        measured.run(50);
        expect(lensReports()).toEqual(["measured,source=canvas,fovy=30.0"]);
        reporter.report.mockClear();
        fake.optics.flatCanvas = true;
        const flat = await boot("VECTOR", "VECTOR", { mapId: "" });
        push(flat.win, FIX, 90);
        flat.run(50);
        expect(lensReports()).toEqual(["unmeasured,source=canvas"]);
    });

    it("keeps the chevron beside the cards on a flat vector map", async () => {
        const page = await boot("VECTOR", "VECTOR");
        push(page.win, FIX, 90, { tilt: 0 });
        expect(page.marker.style.left).toBe(`${(0.5 - MX) * 100}%`);
        const at = screenOf(page.map, FIX);
        expect(at.x).toBeCloseTo(-MX * W, 6);
        expect(at.y).toBeCloseTo(DROP * H, 6);
        expect(page.map.heading).toBe(90);
    });

    it("keeps the chevron beside the cards on a raster map, which gets no heading or tilt", async () => {
        const page = await boot("RASTER", "RASTER");
        push(page.win, FIX, 45);
        page.run(10);
        expect(page.marker.style.left).toBe(`${(0.5 - MX) * 100}%`);
        expect(page.marker.style.transform).toBe("translate(-50%, -50%) rotateZ(45deg)");
        expect(page.map.moves.every((m) => m.heading === undefined && m.tilt === undefined)).toBe(
            true,
        );
        const at = screenOf(page.map, FIX);
        expect(at.x).toBeCloseTo(-MX * W, 6);
        expect(at.y).toBeCloseTo(DROP * H, 6);
    });

    it("turns a north-up flip about the chevron, north running straight up through it", async () => {
        const page = await boot("VECTOR", "VECTOR");
        push(page.win, FIX, 90);
        page.run(50);
        page.win.setNorthUp(true);
        expect(page.marker.style.transform).toContain("rotateZ(90deg)");
        const drift = Array.from({ length: 30 }, () => {
            page.advance(16);
            const at = screenOf(page.map, FIX);
            return Math.hypot(at.x + MX * W, at.y - DROP * H);
        });
        expect(Math.max(...drift)).toBeLessThan(1e-5);
        // The map carries the same yaw as in heading-up, so north — not the
        // camera's forward axis — runs up through the chevron, and the
        // compass reads north.
        expect(page.map.heading).toBeGreaterThan(0.5);
        expect(page.map.heading).toBeLessThan(20);
        expect(leanPx(page.map, FIX, 0)).toBeLessThan(1e-6);
        expect(lastCompass()).toBe("0.0");
        expect(reporter.report).not.toHaveBeenCalledWith("follow", false);
    });

    it("resolves a vector request that renders raster: a snap under the chevron", async () => {
        const page = await boot("VECTOR", "RASTER");
        push(page.win, FIX, 45);
        expect(page.marker.style.left).toBe(`${(0.5 - MX) * 100}%`);
        page.map.moves.length = 0;
        page.map.fire("tilesloaded");
        // The snap: the fix under the chevron, north-up, with no heading or
        // tilt sent to the raster map; the chevron stays where it is.
        const snapped = screenOf(page.map, FIX);
        expect(snapped.x).toBeCloseTo(-MX * W, 6);
        expect(snapped.y).toBeCloseTo(DROP * H, 6);
        expect(page.marker.style.left).toBe(`${(0.5 - MX) * 100}%`);
        page.run(20);
        expect(screenOf(page.map, FIX).x).toBeCloseTo(-MX * W, 6);
        expect(page.map.moves.every((m) => m.heading === undefined && m.tilt === undefined)).toBe(
            true,
        );
    });

    it("resolves an AUTO map that renders vector: heading and tilt snap, then the lens glides", async () => {
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
        // The chevron keeps the OSM spot; the first rendered frame measures
        // the lens, and the camera glides to its yaw and exact anchor.
        expect(page.marker.style.left).toBe(`${(0.5 - MX) * 100}%`);
        page.run(50);
        const at = screenOf(page.map, FIX);
        expect(Math.hypot(at.x + MX * W, at.y - DROP * H)).toBeLessThan(1e-6);
        expect(leanPx(page.map, FIX, 45)).toBeLessThan(1e-6);
        expect(reporter.report).not.toHaveBeenCalledWith("follow", false);
    });

    it("starts a re-follow where the map is after a zoom step the map clamped", async () => {
        // Google caps the zoom at the map type's ceiling; the glide still owns
        // the zoom it asked for, and its first frame must start from the
        // centre the map shows.
        const page = await boot("VECTOR", "VECTOR");
        page.map.maxZoom = 18;
        push(page.win, FIX, 90, { zoom: 18 });
        page.run(50);
        page.map.fire("dragstart");
        page.map.center = { lat: 35.69, lng: 139.75 };
        push(page.win, FIX, 90, { zoom: 19 });
        page.run(20);
        expect(page.map.zoom).toBe(18);
        const before = { ...page.map.center };
        page.win.setFollow(true);
        page.advance(0);
        const jump = screenOf(page.map, before);
        const centre = screenOf(page.map, page.map.center);
        expect(Math.hypot(jump.x - centre.x, jump.y - centre.y)).toBeLessThan(1e-6);
    });

    it("re-follows about the chevron as drawn after a layout change moved its spot while detached", async () => {
        // While the chevron is hidden, a layout change moves its spot with
        // no placement to carry the camera along. The re-follow must read
        // the map against the spot the chevron re-appears at, or its glide
        // pivots on the spot the chevron has left.
        const page = await boot("VECTOR", "VECTOR");
        push(page.win, FIX, 90, { tilt: 0 });
        page.map.fire("dragstart");
        push(page.win, FIX, 90, { tilt: 0, rightSafe: 0 });
        page.win.setNorthUp(true);
        // The fix already sits where the chevron re-appears, on the centre
        // line now the cards are gone, so the re-follow only turns the map
        // north-up about it.
        page.map.center = centreShowing(FIX, 0, DROP * H, 16, 90);
        page.win.setFollow(true);
        expect(page.marker.style.left).toBe("50%");
        const drift = Array.from({ length: 45 }, () => {
            page.advance(16);
            const at = screenOf(page.map, FIX);
            return Math.hypot(at.x, at.y - DROP * H);
        });
        expect(Math.max(...drift)).toBeLessThan(1e-5);
        expect(page.map.heading).toBe(0);
    });

    it.each([
        ["VECTOR", 55],
        ["VECTOR", 0],
        ["RASTER", 0],
    ])(
        "zooms a %s map (tilt %d°) about the chevron's spot while detached",
        async (rendering, tilt) => {
            // The OSM map's detached zoom step keeps its camera padding, so it
            // zooms about the padded centre, where the chevron was; this map
            // must zoom about the same spot.
            const page = await boot(rendering, rendering);
            push(page.win, FIX, 90, { tilt });
            page.run(50);
            page.map.fire("dragstart");
            const spot = screenOf(page.map, FIX);
            push(page.win, FIX, 90, { tilt, zoom: 17 });
            const drift = Array.from({ length: 20 }, () => {
                page.advance(16);
                const at = screenOf(page.map, FIX);
                return Math.hypot(at.x - spot.x, at.y - spot.y);
            });
            expect(page.map.zoom).toBe(17);
            expect(Math.max(...drift)).toBeLessThan(1e-6);
            expect(spot.x).toBeCloseTo(-MX * W, 6);
        },
    );

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
