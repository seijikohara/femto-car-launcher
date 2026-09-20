import type { Element } from "hast";
import { describe, expect, it } from "vitest";
import satteriRepoLinks, {
    rewriteRepoHref,
    type RepoLinksOptions,
} from "./satteri-repo-links";

const options: RepoLinksOptions = {
    repoBlobBase:
        "https://github.com/seijikohara/femto-car-launcher/blob/main/",
    routes: {
        "README.md": "/",
        "PRIVACY.md": "/privacy/",
        "TERMS.md": "/terms/",
    },
};

// A minimal stand-in for HastVisitorContext: the plugin under test only calls
// `setProperty`, so that is all the fake context needs to implement.
const setProperty = (target: Element, name: string, value: string) => {
    target.properties[name] = value;
};

describe("rewriteRepoHref", () => {
    it("maps repository Markdown the site renders to its root-relative route, keeping fragments", () => {
        expect(rewriteRepoHref("PRIVACY.md", options)).toBe("/privacy/");
        expect(rewriteRepoHref("TERMS.md#contact", options)).toBe(
            "/terms/#contact",
        );
        expect(rewriteRepoHref("README.md", options)).toBe("/");
    });

    it("sends other relative repository paths to GitHub", () => {
        expect(rewriteRepoHref("LICENSE", options)).toBe(
            "https://github.com/seijikohara/femto-car-launcher/blob/main/LICENSE",
        );
        expect(
            rewriteRepoHref("app/src/test/resources/README.md#map", options),
        ).toBe(
            "https://github.com/seijikohara/femto-car-launcher/blob/main/app/src/test/resources/README.md#map",
        );
    });

    it("leaves root-relative, absolute, protocol-relative, mailto and anchor links to other plugins", () => {
        for (const href of [
            "/terms/",
            "https://policies.google.com/privacy",
            "//example.com/x",
            "mailto:someone@example.com",
            "#data-the-app-accesses",
        ]) {
            expect(rewriteRepoHref(href, options)).toBeUndefined();
        }
    });
});

describe("satteriRepoLinks", () => {
    it("rewrites the href of an anchor element through the plugin context", () => {
        const node: Element = {
            type: "element",
            tagName: "a",
            properties: { href: "PRIVACY.md" },
            children: [{ type: "text", value: "Privacy Policy" }],
        };
        const plugin = satteriRepoLinks(options);
        const element = plugin.element;
        if (!element || Array.isArray(element))
            throw new Error("the plugin must define a single element pass");
        element.visit(node, { setProperty } as never);
        expect(node.properties.href).toBe("/privacy/");
    });
});
