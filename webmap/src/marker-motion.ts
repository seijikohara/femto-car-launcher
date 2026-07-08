// Screen-pinned self-marker CSS-transition control, shared by the OSM and
// Mapbox pages (main.ts / mapbox-main.ts render the same #self-marker element
// positioned via left/top percentages — see style.ts for the percentage
// math). The Google Maps page does not use this: its camera has no native
// easing to lock the marker step against (see the note in
// googlemaps-main.ts), so its marker and camera already update in the same
// synchronous call.
//
// A layout reflow (see camera.ts isPaddingOnlyReflow) needs the marker
// to glide left/top over the SAME fixed duration the camera eases over,
// instead of the marker's normal instant (screen-pinned) jump; a genuine GPS
// fix needs that instant jump back, so the transition must be armed only for
// the span of one reflow and cleared immediately for the next real fix. An
// in-flight reflow interrupted by a real fix simply snaps to its
// already-settled target, since a fix and a reflow write the same left/top
// for an unchanged layout.
export interface MarkerTransition {
	// Arms (or clears) the CSS transition; call before writing the new
	// left/top so the write itself is what animates (or jumps).
	setActive(active: boolean): void;
}

export function createMarkerTransition(
	el: HTMLElement,
	durationMs: number,
): MarkerTransition {
	// Mutable state in one const holder (let/var are banned — see biome.json
	// and no-let.grit). Guards the self-clearing timeout below against a stale
	// clear: two reflows in quick succession (a rapid layout toggle) must not
	// have the first reflow's timeout wipe out the second reflow's
	// still-running transition.
	const marker = { generation: 0 };
	return {
		setActive(active: boolean): void {
			marker.generation += 1;
			const thisGeneration = marker.generation;
			if (!active) {
				el.style.transition = "";
				return;
			}
			el.style.transition = `left ${durationMs}ms linear, top ${durationMs}ms linear`;
			// Self-clearing: a reflow with no follow-up push (GPS momentarily
			// idle) must not leave the transition armed forever, where it would
			// silently animate some unrelated later left/top write.
			setTimeout(() => {
				if (thisGeneration === marker.generation) el.style.transition = "";
			}, durationMs);
		},
	};
}
