// Drives the OSM backend's follow engine against a fake MapLibre map that
// records each camera call, pinning the reflow choreography it shares with
// the Google Maps page (camera.ts and marker-motion.ts) and its detached zoom
// step. The test environment has no DOM, so the chevron element is a stub.
import { afterEach, beforeEach, describe, expect, it, vi } from "vite-plus/test";
import { DETACHED_ZOOM_STEP_MOTION, LAYOUT_REFLOW_MS } from "./camera";
import { createFollowEngine, type FollowCameraOpts } from "./follow-camera";

const FIX = { lat: 35.681, lon: 139.767 };

function setup() {
    const marker = {
        style: {
            left: "50%",
            top: "50%",
            transform: "",
            transition: "",
            display: "none",
            setProperty: vi.fn(),
            getPropertyValue: () => "",
        },
        classList: { remove: vi.fn(), toggle: vi.fn(), contains: () => false },
        querySelector: () => null,
        // The detached-mode clone (geoMarkerElement).
        cloneNode: () => ({ removeAttribute: vi.fn(), style: {} }),
    };
    const listeners = new Map<string, (ev: { originalEvent?: unknown }) => void>();
    const map = {
        eases: [] as FollowCameraOpts[],
        jumps: [] as FollowCameraOpts[],
        easeTo(opts: FollowCameraOpts) {
            this.eases.push(opts);
        },
        jumpTo(opts: FollowCameraOpts) {
            this.jumps.push(opts);
        },
        on(type: string, listener: (ev: { originalEvent?: unknown }) => void) {
            listeners.set(type, listener);
        },
        getBearing: () => 0,
        getContainer: () => ({ clientWidth: 853, clientHeight: 512 }) as HTMLElement,
        resize: vi.fn(),
        triggerRepaint: vi.fn(),
    };
    const engine = createFollowEngine({
        reporter: { log: vi.fn(), report: vi.fn(), reportErrorThrottled: vi.fn() },
        chevron: { el: marker as unknown as HTMLElement, path: null },
        map,
        createGeoMarker: () => ({
            setLngLat: vi.fn(),
            setRotation: vi.fn(),
            getElement: () => marker as unknown as HTMLElement,
            remove: vi.fn(),
        }),
    });
    const push = (fix: { lat: number; lon: number }, rightSafe = 0.428, zoom = 16): void =>
        engine.updateCamera(fix.lat, fix.lon, 90, zoom, 55, 70, 0.1, rightSafe, 0, "#3367d6");
    const gesture = (type: string): void => listeners.get(type)?.({ originalEvent: {} });
    return { engine, map, marker, push, gesture };
}

beforeEach(() => {
    vi.useFakeTimers({
        toFake: ["Date", "setTimeout", "clearTimeout", "setInterval", "clearInterval"],
    });
    vi.setSystemTime(1_000_000);
});

afterEach(() => {
    vi.useRealTimers();
});

describe("the OSM follow engine", () => {
    it("lands the chevron and the camera together when a fix arrives mid-reflow", () => {
        const page = setup();
        page.push(FIX);
        vi.advanceTimersByTime(1_000);
        // The cards go away: the chevron glides to the centre line with the
        // camera's padding.
        page.push(FIX, 0);
        expect(page.map.eases.at(-1)?.duration).toBe(LAYOUT_REFLOW_MS);
        vi.advanceTimersByTime(100);
        page.push({ lat: FIX.lat + 1e-4, lon: FIX.lon }, 0);
        // The chevron keeps gliding, and the camera moves over the time its
        // transition has left rather than the 100 ms since the last fix.
        expect(page.marker.style.transition).toContain(`${LAYOUT_REFLOW_MS}ms linear`);
        expect(page.map.eases.at(-1)?.duration).toBe(LAYOUT_REFLOW_MS - 100);
        vi.advanceTimersByTime(LAYOUT_REFLOW_MS);
        expect(page.marker.style.transition).toBe("");
    });

    it("lets a fix after the reflow jump the chevron again", () => {
        const page = setup();
        page.push(FIX);
        vi.advanceTimersByTime(1_000);
        page.push(FIX, 0);
        vi.advanceTimersByTime(LAYOUT_REFLOW_MS + 40);
        page.push({ lat: FIX.lat + 1e-4, lon: FIX.lon }, 0);
        expect(page.marker.style.transition).toBe("");
        expect(page.map.eases.at(-1)?.duration).toBe(LAYOUT_REFLOW_MS + 40);
    });

    it("zooms a detached map about the padded centre, where the chevron was", () => {
        // MapLibre keeps the camera padding the follow eases set, and a zoom
        // with no centre zooms about the padded centre: the Google page's
        // detached zoom step matches this.
        const page = setup();
        page.push(FIX);
        page.gesture("dragstart");
        page.push(FIX, 0.428, 17);
        expect(page.map.eases.at(-1)).toEqual({
            zoom: 17,
            duration: DETACHED_ZOOM_STEP_MOTION.durationMs,
            easing: DETACHED_ZOOM_STEP_MOTION.easing,
            essential: true,
        });
    });
});
