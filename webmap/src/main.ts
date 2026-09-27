// Entry module of the single map page (index.html) hosted in the launcher's
// WebView — see WebMapView.kt for the host side of every contract here. The
// host selects the backend with index.html?backend=<osm|googlemaps>;
// this module resolves it, installs the bridge stubs and global error hooks
// synchronously (so `onPageFinished` on the Kotlin side can push state
// immediately — the stubs buffer the latest call per function), then
// dynamically imports the one backend module, which builds the map and
// replays the buffered pushes. Vite code-splits each backend into its own
// chunk, so a page only ever fetches the library it renders with.
import { resolveBackend } from "./backend-name";
import {
    createReporter,
    installGlobalErrorHooks,
    installPendingStubs,
    startFrameSampler,
} from "./bridge";
import { initFailureDetail } from "./load-outcome";

const backend = resolveBackend(window.location.search);
const reporter = createReporter(backend);
installGlobalErrorHooks(reporter);
const pending = installPendingStubs();

const loadBackend = async (): Promise<void> => {
    switch (backend) {
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

// Started after the backend module is in and has begun rendering, so the
// first burst measures the map, not the chunk fetch.
loadBackend()
    .then(() => startFrameSampler(reporter.report))
    .catch((e) => {
        // A failed chunk fetch or an exception escaping the backend's async init:
        // the page will stay blank forever, so tell the host (which may retry by
        // reloading the page). initFailureDetail decides whether a reload can
        // help — only a map script that could not be fetched.
        const detail = initFailureDetail(e);
        reporter.log(detail);
        reporter.report("fatal", detail);
    });
