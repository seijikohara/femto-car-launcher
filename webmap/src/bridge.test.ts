import { describe, expect, it } from "vite-plus/test";
import { redactSecrets } from "./bridge";

describe("redactSecrets", () => {
    it("redacts a Mapbox access_token query value", () => {
        expect(
            redactSecrets(
                "Failed to fetch https://api.mapbox.com/styles/v1/mapbox/standard?sdk=js-3.25.0&access_token=pk.abc.DEF-123",
            ),
        ).toBe(
            "Failed to fetch https://api.mapbox.com/styles/v1/mapbox/standard?sdk=js-3.25.0&access_token=<redacted>",
        );
    });

    it("redacts a Google Maps key query value", () => {
        expect(
            redactSecrets("https://maps.googleapis.com/maps/api/js?key=AIzaSyExample_-9&v=weekly"),
        ).toBe("https://maps.googleapis.com/maps/api/js?key=<redacted>&v=weekly");
    });

    it("redacts every credential parameter in one string", () => {
        expect(redactSecrets("a?token=t1&x=1&api_key=k2&apikey=k3")).toBe(
            "a?token=<redacted>&x=1&api_key=<redacted>&apikey=<redacted>",
        );
    });

    it("keeps credential-free details unchanged", () => {
        const plain =
            "style-load-failed: Failed to fetch https://tiles.openfreemap.org/styles/positron";
        expect(redactSecrets(plain)).toBe(plain);
    });
});
