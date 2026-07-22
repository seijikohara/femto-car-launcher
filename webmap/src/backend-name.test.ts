import { describe, expect, it } from "vite-plus/test";
import { resolveBackend } from "./backend-name";

describe("resolveBackend", () => {
    it("resolves each backend the host can request", () => {
        expect(resolveBackend("?backend=osm")).toBe("osm");
        expect(resolveBackend("?backend=mapbox")).toBe("mapbox");
        expect(resolveBackend("?backend=googlemaps")).toBe("googlemaps");
    });

    it("falls back to osm for a missing parameter", () => {
        expect(resolveBackend("")).toBe("osm");
        expect(resolveBackend("?other=1")).toBe("osm");
    });

    it("falls back to osm for an unknown value", () => {
        expect(resolveBackend("?backend=bing")).toBe("osm");
        expect(resolveBackend("?backend=")).toBe("osm");
    });

    it("ignores unrelated parameters around the backend", () => {
        expect(resolveBackend("?a=1&backend=mapbox&b=2")).toBe("mapbox");
    });
});
