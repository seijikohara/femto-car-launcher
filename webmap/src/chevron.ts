// Self-location chevron helpers shared by every backend module. The chevron
// is a fixed-on-screen DOM overlay (#self-marker in index.html): the camera
// brings the location to the chevron's spot, so the chevron stays put and the
// map slides / rotates beneath it (car-nav style) instead of a geo-anchored
// marker sliding to catch the eased camera. While the camera is detached
// (free pan) the OSM/Mapbox backends swap to a geo-anchored clone built from
// the same element, so colour, ripple, and stale-greying stay identical.
import { LOCATION_STALE_THRESHOLD_MS } from "./camera";

export interface ChevronHandles {
    el: HTMLElement;
    path: SVGPathElement | null;
}

export function chevronHandles(): ChevronHandles {
    // #self-marker is committed in index.html; a missing node is a build
    // error, not a runtime state.
    const el = document.getElementById("self-marker") as HTMLElement;
    return { el, path: el.querySelector("path") };
}

// A fresh fix re-colours the chevron (Material primary from the host) and
// feeds the ripple the same colour via the CSS variable.
export function setChevronColor(chevron: ChevronHandles, color: string): void {
    if (!color) return;
    chevron.path?.setAttribute("fill", color);
    chevron.el.style.setProperty("--marker-color", color);
}

// Orient the chevron. turnDeg rotates it to the travel bearing (north-up
// mode, and always on a Google raster map); perspective lays it onto the
// tilted ground plane (the GL backends and the Google vector map — a raster
// map has no tilt plane).
export function setChevronTransform(
    el: HTMLElement,
    tiltDeg: number,
    turnDeg: number,
    perspective: boolean,
): void {
    el.style.transform = perspective
        ? `translate(-50%, -50%) perspective(600px) rotateX(${tiltDeg}deg) rotateZ(${turnDeg}deg)`
        : `translate(-50%, -50%) rotateZ(${turnDeg}deg)`;
}

// Build the geo-anchored clone element from the live chevron node, so the
// copy has identical structure (ripple span + SVG) without parsing HTML
// strings. The screen-pinning rules live on the #self-marker id; the clone
// keeps only the shared .self-marker class (placement is the map library's).
export function geoMarkerElement(chevron: ChevronHandles): HTMLElement {
    const el = chevron.el.cloneNode(true) as HTMLElement;
    // Strip the id: only one #self-marker may exist in the document.
    el.removeAttribute("id");
    el.style.width = "36px";
    el.style.height = "36px";
    el.style.position = "";
    el.style.left = "";
    el.style.top = "";
    el.style.display = "";
    return el;
}

// Grey the chevron and stop its ripple once fixes stop arriving (signal lost
// in a tunnel): the host pushes a fix per update and goes quiet on loss, so
// the page itself ages the last push. The .stale CSS class greyscales the
// chevron and hides the ripple; the next fix clears it. [lastFixMs] reads the
// backend's fix clock; [staleAlso] lets the OSM/Mapbox backends mirror the
// state onto the geo-anchored clone shown while detached.
export function startStaleTicker(
    chevron: ChevronHandles,
    lastFixMs: () => number,
    staleAlso?: () => HTMLElement | null,
): void {
    setInterval(() => {
        const stale = lastFixMs() > 0 && Date.now() - lastFixMs() > LOCATION_STALE_THRESHOLD_MS;
        chevron.el.classList.toggle("stale", stale);
        staleAlso?.()?.classList.toggle("stale", stale);
    }, 1_000);
}
