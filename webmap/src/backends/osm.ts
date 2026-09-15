// OSM / MapLibre backend module, dynamically imported by main.ts when the
// host loads index.html?backend=osm. Owns the MapLibre instance and the
// OSM-only style bridge (setStyleUrl / setFeatures with the ACCENT recolour
// palette and the 3D-buildings / terrain injection); the follow camera and
// chevron come from the shared follow-camera engine.
// MapLibre 6 is ESM-only and publishes no default export, so the classes come in
// by name. `MapLibreMap` is the library's own alias for its `Map` export, which
// would otherwise shadow the global `Map`.
import { MapLibreMap, Marker, setWorkerUrl } from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";
// MapLibre 6 resolves its worker through `import.meta.url`, which a bundler
// cannot honour, so every bundled consumer must hand it the URL. `?worker&url`
// (not a plain `?url`) is required: the published worker imports a sibling
// chunk, and only the worker transform bundles that sibling in. Without this
// call the build still succeeds and the page still loads — but the worker URL
// points at a file Vite never emits, so no tile is ever parsed and the map
// stays blank. Neither tsc nor the lint/test pass can catch it.
import maplibreWorkerUrl from "maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url";
import type { PageReporter, PendingBridgeCalls } from "../bridge";
import { webglSupport } from "../bridge";
import { chevronHandles } from "../chevron";
import { createFollowEngine } from "../follow-camera";
import { type AccentColors, injectFeatures, rewriteHost, UPSTREAM_TILE_HOST } from "../style";

setWorkerUrl(maplibreWorkerUrl);

// The OSM bridge extends the base femtoBridge with the host-supplied endpoints,
// read synchronously before the map is constructed (the same shape as the
// Google backend's key getters). Absent outside the launcher (`vp dev`), where
// the upstream defaults apply.
interface OsmFemtoBridge {
    onMapEvent(kind: string, detail: string): void;
    // The origin that serves tiles, styles, sprites and glyphs in the upstream
    // layout; every request under UPSTREAM_TILE_HOST is re-pointed at it.
    tileHost(): string;
    // Whether the host list has a host left to rotate to; gates the
    // source-error escalation below.
    tileHostFallback(): boolean;
    // The raster-DEM TileJSON injected while terrain is on.
    terrainTileJsonUrl(): string;
    // The style to construct the map with — the bundled base for the scheme the
    // host will push, so a cold start fetches no hosted style it is about to
    // replace.
    initialStyleUrl(): string;
}

function osmBridge(): OsmFemtoBridge | undefined {
    return window.femtoBridge as OsmFemtoBridge | undefined;
}

const DEFAULT_STYLE_URL = `${UPSTREAM_TILE_HOST}/styles/positron`;

// Cross-fade timing for a style swap — see applyStyleWithFade.
const STYLE_FADE_MS = 500;
const STYLE_FADE_MAX_WAIT_MS = 4000;

// Grace period for both outcome-gated fatals (armStyleLoadFatal and
// armTileHostFatal): long enough that a slow-but-healthy first load with an
// early flaky-tile error still beats the timer, short enough that a dead style
// or a dead tile host surfaces as a notice instead of an indefinite blank page.
const LOAD_FATAL_GRACE_MS = 10_000;

export function init(reporter: PageReporter, pending: PendingBridgeCalls): void {
    const { log, report, reportErrorThrottled } = reporter;

    // MapLibre 6 dropped the WebGL 1 fallback and only ever acquires a webgl2
    // context, so a missing one is a definitive never-going-to-render fact —
    // report it and stop. Constructing the
    // map anyway would only throw a second, noisier fatal (a
    // GPUInitializationError) from a page the host is about to tear down.
    if (!webglSupport().webgl2) {
        log("no-webgl-context");
        report("fatal", "no-webgl-context");
        return;
    }

    // Host-supplied endpoints, read once: they are fixed for the page's life
    // (a change on the Kotlin side rebuilds the WebView).
    const bridge = osmBridge();
    const tileHost = bridge?.tileHost() || UPSTREAM_TILE_HOST;
    const hostFallback = bridge?.tileHostFallback() ?? false;
    const terrainUrl = bridge?.terrainTileJsonUrl() || "";
    const initialStyleUrl = bridge?.initialStyleUrl() || DEFAULT_STYLE_URL;
    if (tileHost !== UPSTREAM_TILE_HOST) log(`tile host: ${tileHost}`);

    // Mutable style state in one const holder (let/var are banned — see the
    // lint block in vite.config.ts and no-let.js). The follow camera's state
    // lives inside the shared engine.
    const state = {
        // Set on the first successful render/load; gates the error policy
        // below (post-load errors are transient; a pre-load failure can mean
        // the style never arrives).
        styleLoaded: false,
        // One armed grace timer per page load — see armStyleLoadFatal.
        fatalArmed: false,
        // Set once a tile actually arrives; the outcome armTileHostFatal gates
        // on, with its own one-shot arm flag.
        tileArrived: false,
        hostFatalArmed: false,
        currentStyleUrl: initialStyleUrl,
        // Set by setStyleUrl for the ACCENT scheme, or null for a plain style.
        accentColors: null as AccentColors | null,
        buildings: false,
        terrain: false,
        // Theme-tracked 3D extrusion colour, set by setFeatures alongside the
        // toggle.
        buildingColor: "",
        styleFadeGen: 0,
    };

    try {
        const liveMap = new MapLibreMap({
            container: "map",
            style: initialStyleUrl,
            center: [0, 0],
            zoom: 1,
            attributionControl: false,
            // Every request the upstream origin would receive — tiles, sprites,
            // glyphs, hosted styles, the bundled styles' sources — goes to the
            // configured host instead, so a mirror needs no style rewriting.
            transformRequest: (url) => ({ url: rewriteHost(url, UPSTREAM_TILE_HOST, tileHost) }),
        });
        // Log the first rendered frame once, then detach: "render" fires on
        // every painted frame, so a persistent listener spews ~60 lines/sec
        // into logcat (and the in-app diagnostics tail) during the GPS camera
        // ease. The host learns the page loaded from onPageFinished, but only
        // this first painted frame proves the map actually renders, so it also
        // reports `ready` for the MAP diagnostics section.
        const onFirstRender = (): void => {
            if (!liveMap.isStyleLoaded()) return;
            state.styleLoaded = true;
            log("rendered");
            report("ready", "");
            liveMap.off("render", onFirstRender);
        };
        liveMap.on("render", onFirstRender);
        liveMap.on("load", () => {
            state.styleLoaded = true;
            log("load");
        });

        // WebGL context loss is usually TRANSIENT on mobile / WebView GPUs,
        // and MapLibre auto-recovers: it preventDefault()s the loss, saves
        // the style, and on webglcontextrestored rebuilds the painter and
        // re-renders. We rely on that built-in restore and do not fall back
        // to another backend.
        liveMap.on("webglcontextlost", () => log("webglcontextlost (awaiting MapLibre restore)"));
        liveMap.on("webglcontextrestored", () => log("webglcontextrestored"));

        // An error before the style has ever loaded CAN mean the style fetch
        // itself failed — then the map stays blank forever (MapLibre does not
        // re-fetch a failed style), which previously showed as a silent blank
        // page until a connectivity edge. But a pre-load error can also be a
        // single flaky tile on an otherwise healthy load, so the fatal is
        // outcome-gated, not message-gated: arm one grace timer and report
        // fatal only if the style has STILL not loaded when it fires. A
        // healthy load ends with styleLoaded=true well inside the grace and
        // the timer is a no-op; a dead style load cannot set it, so the host
        // gets a notice (and its online auto-retry) instead of a blank map.
        function armStyleLoadFatal(detail: string): void {
            if (state.styleLoaded || state.fatalArmed) return;
            state.fatalArmed = true;
            setTimeout(() => {
                if (state.styleLoaded) return;
                log(`style never loaded after error: ${detail}`);
                report("fatal", `style-load-failed: ${detail}`.slice(0, 200));
            }, LOAD_FATAL_GRACE_MS);
        }

        // The one positive signal that the tile host is answering. NOT
        // `isSourceLoaded`: a raster source declared with an inline tiles array
        // (the bundled light style's relief layer) reports itself loaded the
        // moment it is added, without a single request leaving the device.
        liveMap.on("sourcedata", (e) => {
            if (e.tile) state.tileArrived = true;
        });

        // An unreachable tile host does NOT fail the style load: the bundled
        // styles come from appassets and only their sources fail, so the style
        // loads, armStyleLoadFatal stays a no-op, and the map sits blank
        // forever after a single transient error (the dead source's TileJSON is
        // fetched once and never retried). Same shape as armStyleLoadFatal, on
        // the other outcome: arm one grace timer on the first error naming the
        // host and report fatal only if NO tile has arrived when it fires, so
        // the page reloads on the next tile host. Gated on there being a next
        // one — with a
        // single host the old rule stands and post-load errors stay log-only,
        // which is what keeps an ambiguous flaky-tile signal out of the UI.
        function armTileHostFatal(detail: string): void {
            if (!hostFallback || state.tileArrived || state.hostFatalArmed) return;
            // Only the configured host's own failures: a DEM or a hosted style
            // served from somewhere else must not rotate the tile host.
            if (!detail.includes(tileHost)) return;
            state.hostFatalArmed = true;
            setTimeout(() => {
                // A style that never loaded is armStyleLoadFatal's to report.
                if (state.tileArrived || !state.styleLoaded) return;
                log(`tile host unreachable: ${detail}`);
                report("fatal", `tile-host-unreachable: ${detail}`.slice(0, 200));
            }, LOAD_FATAL_GRACE_MS);
        }

        // Tile / style / DEM fetch failures surface here; the host only logs
        // them (transient by definition — never UI, never a backend switch)
        // unless one of the two outcome-gated fatals above concludes otherwise.
        liveMap.on("error", (e) => {
            const detail = e?.error?.message || "unknown map error";
            log(`error: ${detail}`);
            reportErrorThrottled(String(detail));
            if (!state.styleLoaded) armStyleLoadFatal(String(detail));
            armTileHostFatal(String(detail));
        });

        function applyStyle(): void {
            if (state.currentStyleUrl) {
                liveMap.setStyle(state.currentStyleUrl, {
                    transformStyle: (_previous, next) =>
                        injectFeatures(next, {
                            buildings: state.buildings,
                            terrain: state.terrain,
                            accent: state.accentColors,
                            buildingColor: state.buildingColor,
                            terrainUrl: terrainUrl,
                        }),
                });
            }
        }

        // Cross-fade a style swap: capture the outgoing style's last frame
        // from the GL canvas, pin it over the map, apply the new style, then
        // fade the capture out once the new style has loaded and presented a
        // frame. A timeout guard fades anyway so a style that never loads
        // (offline) cannot pin the stale capture; the generation counter lets
        // a rapid second swap cancel the first swap's pending callbacks.
        function applyStyleWithFade(): void {
            // Nothing rendered yet (initial load): swap without a fade.
            if (!liveMap.isStyleLoaded()) {
                applyStyle();
                return;
            }
            state.styleFadeGen += 1;
            const gen = state.styleFadeGen;
            const el = document.getElementById("style-fade");
            const img = el?.querySelector("img");
            if (!el || !(img instanceof HTMLImageElement)) {
                // #style-fade and its <img> are committed in index.html; a
                // missing node is a build error, not a runtime state. This
                // function has no surrounding try, so skip the cross-fade
                // (apply directly) rather than throw.
                log("style-fade nodes missing; applying style without fade");
                applyStyle();
                return;
            }
            // The GL buffer is only valid synchronously inside a render
            // event, so the capture happens there (no preserveDrawingBuffer
            // cost) — but the style swap itself is deferred OUT of the render
            // loop: a setStyle issued mid-render leaves the presented frame
            // stale on a static camera, so the new scheme only appeared as
            // the camera moved.
            liveMap.once("render", () => {
                if (gen !== state.styleFadeGen) return;
                try {
                    img.src = liveMap.getCanvas().toDataURL("image/jpeg", 0.85);
                    el.style.transition = "none";
                    el.style.opacity = "1";
                    el.style.display = "block";
                } catch (e) {
                    log(`style-fade capture failed: ${e instanceof Error ? e.message : e}`);
                }
                setTimeout(() => {
                    if (gen !== state.styleFadeGen) return;
                    applyStyle();
                    const fade = { done: false };
                    const fadeOut = (): void => {
                        if (fade.done || gen !== state.styleFadeGen) return;
                        fade.done = true;
                        // Detach here too: the MAX_WAIT timeout path reaches
                        // fadeOut without going through `check`, so the
                        // render listener would otherwise leak for the page
                        // lifetime when a style never loads (offline).
                        liveMap.off("render", check);
                        el.style.transition = `opacity ${STYLE_FADE_MS}ms ease`;
                        el.style.opacity = "0";
                        setTimeout(() => {
                            if (gen === state.styleFadeGen) el.style.display = "none";
                        }, STYLE_FADE_MS + 100);
                    };
                    // Fade once the swapped-in style has loaded and a frame
                    // with it has been rendered (the same readiness signal the
                    // host uses); "idle" would be nicer but never fires while
                    // the GPS camera keeps easing.
                    const check = (): void => {
                        // Superseded swap, or the new style has presented a
                        // frame: fadeOut() runs the off("render", check)
                        // teardown in both cases.
                        if (gen !== state.styleFadeGen || liveMap.isStyleLoaded()) fadeOut();
                    };
                    liveMap.on("render", check);
                    setTimeout(fadeOut, STYLE_FADE_MAX_WAIT_MS);
                    liveMap.triggerRepaint();
                }, 0);
            });
            liveMap.triggerRepaint();
        }

        const engine = createFollowEngine({
            reporter,
            chevron: chevronHandles(),
            map: liveMap,
            createGeoMarker: (el, lngLat) =>
                new Marker({
                    element: el,
                    rotationAlignment: "map",
                    pitchAlignment: "map",
                })
                    .setLngLat(lngLat)
                    .addTo(liveMap),
        });

        window.updateCamera = engine.updateCamera;
        window.setFollow = engine.setFollow;
        window.setNorthUp = engine.setNorthUp;
        window.onHostResume = engine.onHostResume;
        // Android -> JS: switch the base style and (for the ACCENT scheme)
        // its recolour palette; an empty bg means a plain, non-accent style.
        window.setStyleUrl = (
            url,
            bg,
            water,
            land,
            roadMajor,
            roadMinor,
            roadCasing,
            building,
            label,
        ) => {
            if (!url) return;
            state.currentStyleUrl = url;
            state.accentColors = bg
                ? {
                      background: bg,
                      water: water,
                      land: land,
                      roadMajor: roadMajor,
                      roadMinor: roadMinor,
                      roadCasing: roadCasing,
                      building: building,
                      label: label,
                  }
                : null;
            applyStyleWithFade();
        };
        // Android -> JS: flip the LIVE feature toggles (plus the
        // theme-tracked extrusion colour), then re-apply the style so
        // transformStyle injects (or omits) the matching layers/sources.
        window.setFeatures = (buildings, terrain, buildingColor) => {
            state.buildings = !!buildings;
            state.terrain = !!terrain;
            state.buildingColor = buildingColor || "";
            applyStyleWithFade();
        };

        // Replay any bridge calls the boot stubs recorded before this module
        // loaded. Order mirrors the host's push sequence.
        if (pending.setStyleUrl) window.setStyleUrl(...pending.setStyleUrl);
        if (pending.setFeatures) window.setFeatures(...pending.setFeatures);
        if (pending.setNorthUp != null) window.setNorthUp(pending.setNorthUp);
        if (pending.setFollow != null) window.setFollow(pending.setFollow);
        if (pending.updateCamera) window.updateCamera(...pending.updateCamera);
        if (pending.onHostResume) window.onHostResume();
    } catch (e) {
        log(`exception:${e instanceof Error ? e.message : e}`);
        // The map object never came up; this page will stay blank forever.
        report("fatal", `map-init-exception: ${e instanceof Error ? e.message : e}`.slice(0, 200));
    }
}
