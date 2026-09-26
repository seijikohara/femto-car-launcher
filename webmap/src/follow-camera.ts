// The camera-follow state machine of the MapLibre (OSM) backend: the whole
// follow/detach/refollow machine, the smooth per-fix camera easing, and the
// screen-pinned chevron choreography, parameterised over a minimal structural
// map interface rather than the maplibre-gl types directly. The Google Maps
// backend keeps its own machine (its camera-change events carry no
// user-vs-programmatic flag and it has no mapId-free geo marker — see
// backends/googlemaps.ts) but moves the camera by the same rules: the
// motion policy (followMotion and the one-shot motions, curves included) and
// the orientation rule (followOrientation) live in camera.ts. The one-shot
// moves pass their curve to easeTo explicitly: it is MapLibre's own default,
// so the OSM map moves as it always has, and both backends read the curve
// from the one symbol.
import {
    AUTO_REFOLLOW_MS,
    appliedBearing,
    type CameraMotion,
    DETACHED_ZOOM_STEP_MOTION,
    followMotion,
    followOrientation,
    isPaddingOnlyReflow,
    isRealPosition,
    LAYOUT_REFLOW_MS,
    LOCATION_STALE_THRESHOLD_MS,
    ORIENTATION_FLIP_MOTION,
    REFLOW_MOTION,
    REFOLLOW_MOTION,
    smoothedBearing,
} from "./camera";
import { createBearingReporter, type PageReporter } from "./bridge";
import {
    type ChevronHandles,
    geoMarkerElement,
    setChevronColor,
    setChevronTransform,
    startStaleTicker,
} from "./chevron";
import { createMarkerTransition } from "./marker-motion";
import { markerPadLeft, markerPadRight, markerPadTop, markerSpot } from "./style";

// The camera-option shape this engine passes to easeTo/jumpTo — a structural
// subset of maplibre-gl's camera options.
export interface FollowCameraOpts {
    center?: [number, number];
    bearing?: number;
    zoom?: number;
    pitch?: number;
    padding?: { top: number; bottom: number; left: number; right: number };
    duration?: number;
    easing?: (t: number) => number;
    essential?: boolean;
}

// The map surface the engine drives; maplibregl.Map satisfies it structurally.
export interface GlFollowMap {
    easeTo(opts: FollowCameraOpts): unknown;
    jumpTo(opts: FollowCameraOpts): unknown;
    on(type: string, listener: (ev: { originalEvent?: unknown }) => void): unknown;
    getBearing(): number;
    getContainer(): HTMLElement;
    resize(): unknown;
    triggerRepaint(): unknown;
}

// The geo-anchored marker surface (the detached-mode clone); a maplibre Marker
// satisfies it structurally.
export interface GeoMarkerLike {
    setLngLat(lngLat: [number, number]): unknown;
    setRotation(deg: number): unknown;
    getElement(): HTMLElement;
    remove(): unknown;
}

export interface FollowEngineDeps {
    reporter: PageReporter;
    chevron: ChevronHandles;
    map: GlFollowMap;
    // Construct AND place a geo-anchored marker on the map (rotation aligned
    // to the map plane) — the one line that needs the concrete GL library.
    createGeoMarker(el: HTMLElement, lngLat: [number, number]): GeoMarkerLike;
}

// The bridge functions the engine implements; the backend module installs
// them on window (and replays any pending boot-stub calls).
export interface FollowEngine {
    updateCamera: Window["updateCamera"];
    setFollow: Window["setFollow"];
    setNorthUp: Window["setNorthUp"];
    // Host lifecycle resume: re-measure and repaint so the map recovers after
    // a pause/resume cycle (guards a stale GL surface on Android).
    onHostResume(): void;
}

export function createFollowEngine(deps: FollowEngineDeps): FollowEngine {
    const { reporter, chevron, map } = deps;
    const markerEl = chevron.el;
    // Lockstep control for a layout reflow — see isPaddingOnlyReflow
    // and marker-motion.ts.
    const markerTransition = createMarkerTransition(markerEl, LAYOUT_REFLOW_MS);

    // Mutable engine state in one const holder (let/var are banned — see
    // the lint block in vite.config.ts and no-let.js). Camera pushes and
    // follow flips all read and write through here.
    const state = {
        firstCamera: true,
        // Wall-clock ms of the last camera push; 0 until the first fix. The
        // stale ticker ages this to grey the chevron, and updateCamera
        // measures the inter-fix interval from it to match its ease duration.
        lastFixMs: 0,
        // The bearing last applied to the camera, for jitter smoothing; null
        // until the first fix (and reset after a signal gap) so those adopt
        // the raw value.
        lastBearing: null as number | null,
        // Camera-follow state: true (default) keeps the camera glued to the
        // fixes; a user gesture detaches it (free pan), and AUTO_REFOLLOW_MS
        // after the last gesture — or an explicit host setFollow(true) —
        // re-attaches.
        following: true,
        refollowTimer: 0 as ReturnType<typeof setTimeout> | 0,
        // North-up pins the map to north and rotates the chevron to the
        // heading instead of rotating the map; pushed by the host from the
        // persisted setting.
        northUp: false,
        // While detached the screen-fixed chevron is wrong (it points at
        // arbitrary map), so a geo-anchored clone tracks the real GPS
        // position instead.
        geoMarker: null as GeoMarkerLike | null,
        // The latest pushed fix, kept so a re-attach can ease the camera home
        // and the geo marker can be (re)placed without waiting for the next
        // fix.
        lastFix: null as {
            lon: number;
            lat: number;
            heading: number;
            zoom: number;
            tilt: number;
            markerPos: number;
            bottomSafe: number;
            rightSafe: number;
            leftSafe: number;
        } | null,
        // The zoom last pushed by the host; a change while detached is the
        // user's +/- button, applied to the free camera around its own centre.
        lastPushedZoom: 0,
    };

    // Grey the chevron (and the detached-mode clone) when fixes stop arriving.
    startStaleTicker(
        chevron,
        () => state.lastFixMs,
        () => state.geoMarker?.getElement() ?? null,
    );

    function syncGeoMarker(): void {
        const fix = state.lastFix;
        if (!fix) return;
        if (!state.geoMarker) {
            state.geoMarker = deps.createGeoMarker(geoMarkerElement(chevron), [fix.lon, fix.lat]);
        } else {
            state.geoMarker.setLngLat([fix.lon, fix.lat]);
        }
        state.geoMarker.setRotation(fix.heading);
        // Mirror the DOM chevron (the single source for marker colour and
        // staleness) so an accent change or a signal loss while detached
        // reaches the clone too.
        const el = state.geoMarker.getElement();
        const fill = chevron.path?.getAttribute("fill");
        const clonePath = el.querySelector("path");
        if (fill && clonePath) clonePath.setAttribute("fill", fill);
        el.style.setProperty("--marker-color", markerEl.style.getPropertyValue("--marker-color"));
        el.classList.toggle("stale", markerEl.classList.contains("stale"));
    }

    // North-up keeps the map pinned to north and rotates the chevron to the
    // heading instead; heading-up rotates the map and the chevron points up
    // (followOrientation — MapLibre always rotates). The perspective
    // transform lays the chevron onto the tilted ground plane.
    function syncChevron(tilt: number, heading: number): void {
        const { chevronTurn } = followOrientation(state.northUp, heading, true);
        setChevronTransform(markerEl, tilt, chevronTurn, true);
    }

    function easeHome(motion: CameraMotion): void {
        const fix = state.lastFix;
        if (!fix) return;
        map.easeTo({
            center: [fix.lon, fix.lat],
            bearing: appliedBearing(state.northUp, fix.heading),
            zoom: fix.zoom,
            pitch: fix.tilt,
            padding: {
                top: markerPadTop(
                    fix.markerPos,
                    fix.bottomSafe,
                    map.getContainer().clientHeight || 0,
                ),
                bottom: 0,
                left: markerPadLeft(fix.leftSafe, map.getContainer().clientWidth || 0),
                right: markerPadRight(fix.rightSafe, map.getContainer().clientWidth || 0),
            },
            duration: motion.durationMs,
            easing: motion.easing,
            essential: true,
        });
    }

    function setFollowing(follow: boolean): void {
        if (state.following === follow) return;
        state.following = follow;
        reporter.report("follow", follow);
        if (follow) {
            if (state.refollowTimer) clearTimeout(state.refollowTimer);
            state.refollowTimer = 0;
            state.geoMarker?.remove();
            state.geoMarker = null;
            markerEl.style.display = "block";
            // Ease home in one continuous transition; the per-fix cadence
            // easing resumes from the next push.
            easeHome(REFOLLOW_MOTION);
        } else {
            // The screen-fixed chevron points at arbitrary map while
            // detached; the geo-anchored clone tracks the real position
            // instead.
            markerEl.style.display = "none";
            syncGeoMarker();
        }
    }

    function armRefollow(): void {
        if (state.refollowTimer) clearTimeout(state.refollowTimer);
        state.refollowTimer = setTimeout(() => setFollowing(true), AUTO_REFOLLOW_MS);
    }

    // dragstart fires only for user drags; zoom/rotate/pitch start also fire
    // for camera API moves, so those gate on originalEvent (user input only).
    map.on("dragstart", () => {
        setFollowing(false);
        if (state.refollowTimer) clearTimeout(state.refollowTimer);
    });
    for (const ev of ["zoomstart", "rotatestart", "pitchstart"] as const) {
        map.on(ev, (e) => {
            if (e.originalEvent) {
                setFollowing(false);
                if (state.refollowTimer) clearTimeout(state.refollowTimer);
            }
        });
    }
    for (const ev of ["dragend", "zoomend", "rotateend", "pitchend"] as const) {
        map.on(ev, () => {
            if (!state.following) armRefollow();
        });
    }

    // Report the camera bearing so the host's compass overlay can track the
    // map orientation in either mode (throttled — see createBearingReporter).
    const reportBearing = createBearingReporter(reporter.report);
    map.on("move", () => reportBearing(map.getBearing()));

    return {
        // Android -> JS: smooth heading-up camera follow (easeTo interpolates
        // between sparse GPS fixes). The first fix jumps (no fly-in from
        // [0,0]); the rest ease. The chevron is pinned on screen (not
        // geo-anchored), so only the camera moves and the chevron stays put
        // while the map slides + rotates beneath it. The chevron's screen
        // position tracks markerPos and the safe-area fractions, matching the
        // camera padding so the location renders right under it.
        // [markerColor] (Material primary) self-heals if the first push raced
        // page load.
        updateCamera(
            lat,
            lon,
            bearing,
            zoom,
            tilt,
            markerPos,
            bottomSafe,
            rightSafe,
            leftSafe,
            markerColor,
        ): void {
            // Drop a push the Kotlin gate should already have filtered rather
            // than letting it become the camera target; the last good fix stays.
            if (!isRealPosition(lat, lon)) return;
            // Captured before state.lastFix below is overwritten with this
            // push: compared against it to tell a genuine GPS fix (the center
            // moves) from a layout reflow (the center holds, only the
            // safe-area padding changes) — see isPaddingOnlyReflow.
            const previousFix = state.lastFix;
            // Measure the inter-fix interval BEFORE refreshing lastFixMs: the
            // ease duration matches it so each ease finishes as the next fix
            // lands (followMotion).
            const now = Date.now();
            const sinceLastFixMs = state.lastFixMs > 0 ? now - state.lastFixMs : 0;
            // A signal gap restarts bearing smoothing from the raw value (and
            // snaps the camera — followMotion).
            const signalGap = sinceLastFixMs > LOCATION_STALE_THRESHOLD_MS;
            if (signalGap) state.lastBearing = null;
            // A fresh fix: re-colour the chevron, feed the ripple the same
            // colour, and clear any stale greyout from a prior signal gap.
            setChevronColor(chevron, markerColor);
            state.lastFixMs = now;
            markerEl.classList.remove("stale");
            const heading = smoothedBearing(state.lastBearing, bearing || 0);
            state.lastBearing = heading;
            const previousZoom = state.lastPushedZoom;
            state.lastPushedZoom = Number.isFinite(zoom) ? zoom : 16;
            state.lastFix = {
                lon,
                lat,
                heading,
                zoom: Number.isFinite(zoom) ? zoom : 16,
                tilt: tilt || 0,
                markerPos: markerPos || 0,
                bottomSafe: bottomSafe || 0,
                rightSafe: rightSafe || 0,
                leftSafe: leftSafe || 0,
            };
            if (!state.following) {
                // Detached (free pan): the camera stays the user's, the
                // geo-anchored marker tracks the fixes — except a pushed ZOOM
                // change is the user's +/- button, applied to the free camera
                // around its own centre.
                syncGeoMarker();
                if (previousZoom > 0 && state.lastPushedZoom !== previousZoom) {
                    map.easeTo({
                        zoom: state.lastPushedZoom,
                        duration: DETACHED_ZOOM_STEP_MOTION.durationMs,
                        easing: DETACHED_ZOOM_STEP_MOTION.easing,
                        essential: true,
                    });
                }
                return;
            }
            const motion = followMotion({
                firstCamera: state.firstCamera,
                signalGap,
                // A dashboard layout change (dock position, card visibility,
                // driver side) re-pushes the SAME fix with only the padding
                // changed.
                reflow: isPaddingOnlyReflow(previousFix, {
                    lon,
                    lat,
                    markerPos: markerPos || 0,
                    bottomSafe: bottomSafe || 0,
                    rightSafe: rightSafe || 0,
                    leftSafe: leftSafe || 0,
                }),
                sinceLastFixMs,
            });
            state.firstCamera = false;
            // Lockstep: arm the marker's CSS transition on a reflow so its
            // left/top write below glides instead of jumping; clear it
            // otherwise so a real fix keeps snapping the screen-pinned marker
            // while the camera eases the ground underneath it.
            markerTransition.setActive(motion === REFLOW_MOTION);
            const spot = markerSpot({ markerPos, bottomSafe, rightSafe, leftSafe });
            markerEl.style.left = `${(0.5 + spot.x) * 100}%`;
            markerEl.style.top = `${(0.5 + spot.y) * 100}%`;
            syncChevron(tilt || 0, heading);
            markerEl.style.display = "block";
            const opts: FollowCameraOpts = {
                center: [lon, lat],
                bearing: appliedBearing(state.northUp, heading),
                zoom: Number.isFinite(zoom) ? zoom : 16,
                pitch: tilt || 0,
                padding: {
                    top: markerPadTop(markerPos, bottomSafe, map.getContainer().clientHeight || 0),
                    bottom: 0,
                    left: markerPadLeft(leftSafe, map.getContainer().clientWidth || 0),
                    right: markerPadRight(rightSafe, map.getContainer().clientWidth || 0),
                },
            };
            if (motion === null) {
                map.jumpTo(opts);
            } else {
                map.easeTo({
                    ...opts,
                    duration: motion.durationMs,
                    easing: motion.easing,
                    essential: true,
                });
            }
        },
        setFollow(follow): void {
            setFollowing(!!follow);
        },
        setNorthUp(enabled): void {
            state.northUp = !!enabled;
            // Re-orient immediately while following; a detached camera keeps
            // the user's rotation until re-attach. The chevron flips with the
            // camera — waiting for the next fix would leave it pointing wrong
            // for up to one GPS interval.
            const fix = state.lastFix;
            if (state.following && fix) {
                map.easeTo({
                    bearing: appliedBearing(state.northUp, fix.heading),
                    duration: ORIENTATION_FLIP_MOTION.durationMs,
                    easing: ORIENTATION_FLIP_MOTION.easing,
                    essential: true,
                });
                syncChevron(fix.tilt, fix.heading);
            }
        },
        onHostResume(): void {
            map.resize();
            map.triggerRepaint();
        },
    };
}
