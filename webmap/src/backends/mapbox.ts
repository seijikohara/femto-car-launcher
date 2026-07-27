// Mapbox GL JS backend module, dynamically imported by main.ts when the host
// loads index.html?backend=mapbox. Owns the mapbox-gl instance (BYO public
// token from the host bridge) and the Mapbox-only style bridge
// (setMapboxStyle + the traffic overlay); the follow camera and chevron come
// from the shared follow-camera engine.
//
// Bundling note: mapbox-gl ships as a self-contained UMD bundle whose
// built-in worker uses `new URL('./worker', import.meta.url)`. Vite's
// module-worker transform rewrites that URL at build time — but only for ESM
// workers, not for workers embedded in a pre-built UMD. The UMD bundle is
// therefore loaded as a classic <script> (injected below) so its internal
// worker URL resolves against the final asset path at runtime, not against
// Vite's build graph. Vite still processes the CSS import and the `?url`
// asset import, emitting both as hashed local files with no CDN dependency.
import "mapbox-gl/dist/mapbox-gl.css";
// Types only — the runtime object comes from the UMD global set by the script.
import type * as MapboxGLTypes from "mapbox-gl";
// `?url` makes Vite emit the pre-built UMD as a hashed local asset; we inject
// it as a classic <script> so the self-contained worker loads without Vite's
// module-worker transform (which breaks at build.target chrome109).
import mapboxglUrl from "mapbox-gl/dist/mapbox-gl.js?url";
import type { PageReporter, PendingBridgeCalls } from "../bridge";
import { webglSupport } from "../bridge";
import { chevronHandles } from "../chevron";
import { createFollowEngine } from "../follow-camera";
import {
    mapboxStyleUrl,
    styleApplyMode,
    TRAFFIC_LAYER_ID,
    TRAFFIC_SOURCE_ID,
    TRAFFIC_SOURCE_SPEC,
    trafficLayerSpec,
} from "../mapbox-style";

// The Mapbox bridge extends the base femtoBridge with mapboxToken(), which is
// only present when the host has wired up the Mapbox backend.
interface MapboxFemtoBridge {
    onMapEvent(kind: string, detail: string): void;
    // Synchronous getter injected by the host; returns the Mapbox
    // public-token (`pk.*`). Returns empty string when unconfigured.
    mapboxToken(): string;
}

function mapboxBridge(): MapboxFemtoBridge | undefined {
    return window.femtoBridge as MapboxFemtoBridge | undefined;
}

// The UMD bundle sets `window.mapboxgl` when loaded as a classic script.
// Its shape is the library's default export (the `mapboxgl` namespace
// object), not the ES module namespace.
type MapboxGLNamespace = typeof MapboxGLTypes.default & {
    Map: typeof MapboxGLTypes.Map;
    Marker: typeof MapboxGLTypes.Marker;
};
function getMapboxGL(): MapboxGLNamespace {
    return (window as unknown as { mapboxgl: MapboxGLNamespace }).mapboxgl;
}

export function init(reporter: PageReporter, pending: PendingBridgeCalls): void {
    const { log, report, reportErrorThrottled } = reporter;

    // Verify WebGL 2 is available before spending any budget on map
    // construction. mapbox-gl v3 has no WebGL 1 fallback — it only ever
    // acquires a webgl2 context — so a missing webgl2 context is a definitive
    // never-going-to-render fact: report it and stop, skipping the UMD load
    // for a page the host is about to tear down.
    if (!webglSupport().webgl2) {
        log("no-webgl-context");
        report("fatal", "no-webgl-context");
        return;
    }

    // Mutable style state in one const holder (let/var are banned — see the
    // lint block in vite.config.ts and no-let.js).
    const state = {
        styleLoaded: false,
        // Last style URL handed to the map (initial construction or a
        // setStyle swap). setMapboxStyle compares against it to tell an
        // unchanged-URL push (day/night flip, traffic toggle) from a genuine
        // style swap — see styleApplyMode.
        appliedStyleUrl: undefined as string | undefined,
    };

    function initMap(): void {
        const mapboxgl = getMapboxGL();

        // A missing token means the backend cannot function; report a fatal
        // so the host shows an explanatory notice rather than a blank map.
        // Method-optional like the googlemaps bridge accessors: outside the
        // launcher a femtoBridge stand-in may not carry this getter.
        const token = mapboxBridge()?.mapboxToken?.() ?? "";
        if (!token) {
            log("mapbox-token-missing");
            report("fatal", "mapbox-token-missing");
            return;
        }
        mapboxgl.accessToken = token;

        try {
            // Mapbox ToS require the logo and attribution text to stay
            // visible at all times; the launcher keeps every backend's credit
            // in the bottom-left corner (see .claude/rules/webmap.md), so
            // both are pinned there. The constructor's attribution control is
            // disabled and re-added manually only to place it bottom-left; a
            // compact control keeps it small on head units.
            const initialStyleUrl = mapboxStyleUrl("standard");
            const liveMap = new mapboxgl.Map({
                container: "map",
                style: initialStyleUrl,
                center: [0, 0],
                zoom: 1,
                attributionControl: false,
                // Stated explicitly (this matches the library default) so a
                // future default change cannot drift the wordmark out of the
                // shared corner.
                logoPosition: "bottom-left",
            });
            liveMap.addControl(new mapboxgl.AttributionControl({ compact: true }), "bottom-left");
            // Record the constructor's style so the first setMapboxStyle push
            // can tell an unchanged-URL fragment update from a real swap.
            state.appliedStyleUrl = initialStyleUrl;

            // Log the first rendered frame once; detach immediately after to
            // avoid flooding logcat at ~60 lines/sec during GPS camera
            // easing. The first successful render also marks the style as
            // loaded so any later error is treated as transient, never as a
            // token failure.
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

            liveMap.on("webglcontextlost", () => log("webglcontextlost (awaiting Mapbox restore)"));
            liveMap.on("webglcontextrestored", () => log("webglcontextrestored"));

            liveMap.on("error", (e) => {
                const detail =
                    (e as { error?: { message?: string } })?.error?.message ?? "unknown map error";
                log(`error: ${detail}`);
                // An error before the style has ever loaded is almost always
                // an invalid/blank access token (or no network) — surface a
                // fatal so the host shows the token notice instead of a
                // silent blank map. A slow but valid load fires no error, so
                // this never false-positives on it. Errors after the style
                // loaded are transient (flaky tiles): log-only.
                if (state.styleLoaded) {
                    reportErrorThrottled(String(detail));
                } else {
                    report("fatal", String(detail));
                }
            });

            function applyTraffic(on: boolean): void {
                const has = liveMap.getLayer(TRAFFIC_LAYER_ID) != null;
                if (on && !has) {
                    if (liveMap.getSource(TRAFFIC_SOURCE_ID) == null) {
                        liveMap.addSource(
                            TRAFFIC_SOURCE_ID,
                            TRAFFIC_SOURCE_SPEC as MapboxGLTypes.SourceSpecification,
                        );
                    }
                    liveMap.addLayer(trafficLayerSpec() as MapboxGLTypes.LayerSpecification);
                } else if (!on && has) {
                    liveMap.removeLayer(TRAFFIC_LAYER_ID);
                }
            }

            const engine = createFollowEngine({
                reporter,
                chevron: chevronHandles(),
                map: liveMap,
                createGeoMarker: (el, lngLat) =>
                    new mapboxgl.Marker({
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
            // Android -> JS: switch Mapbox base style, apply the Standard
            // lightPreset, and restore the traffic layer. mapbox-gl v3 fires
            // `style.load` only on a full style load, so a
            // lightPreset/traffic push on the SAME style URL (the day/night
            // flip, the traffic toggle) takes setStyle's diff path, never
            // re-fires `style.load`, and would be silently dropped while
            // leaking a stale once-listener. So apply the fragment properties
            // directly when the URL is unchanged on a loaded style, and force
            // a full reload (diff:false) on a genuine swap so `style.load`
            // fires exactly once for this push.
            window.setMapboxStyle = (styleId, lightPreset, traffic) => {
                const url = mapboxStyleUrl(styleId);
                if (
                    styleApplyMode(url, state.appliedStyleUrl, liveMap.isStyleLoaded()) ===
                    "apply-now"
                ) {
                    // Standard v3 fragment API; safe no-op on non-Standard
                    // styles.
                    liveMap.setConfigProperty("basemap", "lightPreset", lightPreset);
                    applyTraffic(traffic);
                    return;
                }
                state.appliedStyleUrl = url;
                // mapbox-gl types mark localFontFamily /
                // localIdeographFontFamily required though they are optional
                // at runtime; pass undefined to keep the library defaults
                // while forcing diff:false (a full, deterministic reload).
                liveMap.setStyle(url, {
                    diff: false,
                    localFontFamily: undefined,
                    localIdeographFontFamily: undefined,
                });
                liveMap.once("style.load", () => {
                    liveMap.setConfigProperty("basemap", "lightPreset", lightPreset);
                    applyTraffic(traffic);
                });
            };

            // Replay any bridge calls the boot stubs recorded before the UMD
            // loaded. Order mirrors the host's push sequence.
            if (pending.setMapboxStyle) window.setMapboxStyle(...pending.setMapboxStyle);
            if (pending.setNorthUp != null) window.setNorthUp(pending.setNorthUp);
            if (pending.setFollow != null) window.setFollow(pending.setFollow);
            if (pending.updateCamera) window.updateCamera(...pending.updateCamera);
            if (pending.onHostResume) window.onHostResume();
        } catch (e) {
            log(`exception: ${e instanceof Error ? e.message : e}`);
            report(
                "fatal",
                `map-init-exception: ${e instanceof Error ? e.message : e}`.slice(0, 200),
            );
        }
    }

    // Inject the UMD bundle as a classic <script>. The script sets
    // window.mapboxgl before its load event fires, so initMap can safely call
    // new mapboxgl.Map.
    const s = document.createElement("script");
    s.src = mapboxglUrl;
    s.addEventListener("load", initMap);
    s.addEventListener("error", () => report("fatal", "mapbox-lib-load-failed"));
    document.head.appendChild(s);
}
