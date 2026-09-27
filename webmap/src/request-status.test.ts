import { AJAXError } from "maplibre-gl";
import { describe, expect, it } from "vite-plus/test";
import { requestStatusOf } from "./request-status";

describe("requestStatusOf", () => {
    it("reads the status a failed request carries", () => {
        const refused = new AJAXError(
            404,
            "Not Found",
            "https://styles.example.test/s.json",
            new Blob(),
        );
        expect(requestStatusOf(refused)).toBe(404);
    });

    it("reads status 0 for a request that got no response", () => {
        const failed = new AJAXError(
            0,
            "Failed to fetch",
            "https://tiles.openfreemap.org/planet",
            new Blob(),
        );
        expect(requestStatusOf(failed)).toBe(0);
    });

    it("has no status for any other error", () => {
        expect(requestStatusOf(new SyntaxError("Unexpected token '<'"))).toBeNull();
        expect(requestStatusOf(undefined)).toBeNull();
    });
});
