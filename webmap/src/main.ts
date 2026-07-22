// Entry module of the single map page (index.html) hosted in the launcher's
// WebView — see WebMapView.kt for the host side of every contract here. The
// host selects the backend with index.html?backend=<osm|mapbox|googlemaps>;
// this module resolves it, installs the bridge stubs and global error hooks
// synchronously (so `onPageFinished` on the Kotlin side can push state
// immediately — the stubs buffer the latest call per function), then
// dynamically imports the one backend module, which builds the map and
// replays the buffered pushes. Vite code-splits each backend into its own
// chunk, so a page only ever fetches the library it renders with.
import { resolveBackend } from "./backend-name";
import { createReporter, installGlobalErrorHooks, installPendingStubs } from "./bridge";

const backend = resolveBackend(window.location.search);
const reporter = createReporter(backend);
installGlobalErrorHooks(reporter);
const pending = installPendingStubs();

const loadBackend = async (): Promise<void> => {
    switch (backend) {
        case "mapbox": {
            const mod = await import("./backends/mapbox");
            mod.init(reporter, pending);
            return;
        }
        case "googlemaps": {
            const mod = await import("./backends/googlemaps");
            await mod.init(reporter, pending);
            return;
        }
        case "osm": {
            const mod = await import("./backends/osm");
            mod.init(reporter, pending);
            return;
        }
    }
};

loadBackend().catch((e) => {
    // A failed chunk fetch or an exception escaping the backend's async init:
    // the page will stay blank forever, so tell the host (which may retry by
    // reloading the page).
    const msg = e instanceof Error ? e.message : String(e);
    reporter.log(`backend-load-failed: ${msg}`);
    reporter.report("fatal", `backend-load-failed: ${msg}`.slice(0, 200));
});
