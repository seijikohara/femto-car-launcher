import { resolve } from "node:path";
// vitest/config re-exports Vite's defineConfig with the `test` block typed.
import { defineConfig } from "vitest/config";

export default defineConfig({
	// Relative asset URLs: the page is served from
	// https://appassets.androidplatform.net/assets/web/map.html, so absolute
	// "/assets/..." URLs would escape the web/ asset subtree.
	base: "./",
	build: {
		// Android 13's factory WebView is Chromium 109; aftermarket AI boxes
		// without Play Services may never update it, so never emit newer syntax.
		target: "chrome109",
		// dist/web/ mirrors the assets/web/ layout the Kotlin host expects; the
		// whole dist/ directory is wired into the Android assets source set.
		outDir: "dist/web",
		rollupOptions: {
			input: {
				map: resolve(import.meta.dirname, "map.html"),
				mapbox: resolve(import.meta.dirname, "mapbox.html"),
				googlemaps: resolve(import.meta.dirname, "googlemaps.html"),
			},
		},
	},
	test: {
		// style.ts is pure data transformation; no DOM environment needed.
		environment: "node",
		include: ["src/**/*.test.ts"],
	},
});
