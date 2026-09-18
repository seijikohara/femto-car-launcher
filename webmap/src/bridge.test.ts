import { describe, expect, it } from "vite-plus/test";
import { frameSampleDetail, redactSecrets } from "./bridge";

describe("redactSecrets", () => {
    it("redacts an access_token query value", () => {
        expect(
            redactSecrets(
                "Failed to fetch https://tiles.example.com/styles/v1/standard?sdk=js-3.25.0&access_token=pk.abc.DEF-123",
            ),
        ).toBe(
            "Failed to fetch https://tiles.example.com/styles/v1/standard?sdk=js-3.25.0&access_token=<redacted>",
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

describe("frameSampleDetail", () => {
    it("reports the median and worst interval, rounded, with the sample count", () => {
        expect(frameSampleDetail([16.7, 33.4, 16.6, 100.2, 16.7])).toBe(
            "median=17,worst=100,samples=5",
        );
    });

    it("is defined for an empty run", () => {
        expect(frameSampleDetail([])).toBe("median=0,worst=0,samples=0");
    });
});
