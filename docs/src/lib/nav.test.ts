import { describe, expect, it } from "vitest";
import { isNavItemActive } from "./nav";

// Mirrors withBase()'s idempotency (see lib/url.ts): prefixing an
// already-prefixed path must not double it up.
const base = (path: string): string =>
    path === "/b" || path.startsWith("/b/") ? path : `/b${path}`;

const home = { href: "/" };
const features = { href: "/features/live-map/", section: "/features/" };
const catalog = { href: "/catalog/" };

describe("isNavItemActive", () => {
    it("matches home only on the exact base home path", () => {
        expect(isNavItemActive("/b/", home, base)).toBe(true);
        expect(isNavItemActive("/b/install/", home, base)).toBe(false);
    });

    it("matches Features under its section, not just its own href", () => {
        expect(
            isNavItemActive("/b/features/driving-data/", features, base),
        ).toBe(true);
        expect(isNavItemActive("/b/features/", features, base)).toBe(true);
        expect(isNavItemActive("/b/featuresx/", features, base)).toBe(false);
    });

    it("matches Catalog by href when no section is given", () => {
        expect(isNavItemActive("/b/catalog/", catalog, base)).toBe(true);
        expect(isNavItemActive("/b/catalog/anything/", catalog, base)).toBe(
            true,
        );
        expect(isNavItemActive("/b/", catalog, base)).toBe(false);
    });

    it("does not double the base prefix when base() is idempotent", () => {
        expect(base("/features/")).toBe("/b/features/");
        expect(base("/b/features/")).toBe("/b/features/");
        expect(isNavItemActive("/b/features/", features, base)).toBe(true);
    });
});
