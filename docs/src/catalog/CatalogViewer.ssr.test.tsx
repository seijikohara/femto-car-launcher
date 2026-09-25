// @vitest-environment node
// Astro server-renders every island's initial HTML — client:load included —
// before any client hydrates it (see the `resolution` comment in
// CatalogViewer.tsx). A render-phase `window`/`document` access only ever
// surfaced as an `astro build` failure (ReferenceError: window is not
// defined, rendering /catalog/index.html); this reproduces that same pass in
// milliseconds, under plain Node with no jsdom, instead of a full site build.
import { renderToString } from "react-dom/server";
import { describe, expect, it } from "vitest";
import CatalogViewer from "./CatalogViewer";

describe("CatalogViewer SSR", () => {
    it("renders the loading state server-side without throwing", () => {
        const html = renderToString(
            <CatalogViewer
                channels={[
                    {
                        id: "stable",
                        label: "Stable",
                        manifestUrl: "/b/catalog/manifest.json",
                        assetBase: "/b/catalog/",
                    },
                    {
                        id: "nightly",
                        label: "Nightly",
                        manifestUrl: "/b/catalog/nightly/manifest.json",
                        assetBase: "/b/catalog/nightly/",
                    },
                ]}
            />,
        );
        expect(html).toContain("Loading the catalog");
    });
});
