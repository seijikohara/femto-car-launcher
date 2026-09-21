// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
    applyTheme,
    installThemeSync,
    readThemeMode,
    resolveAppearance,
    STORAGE_KEY,
} from "./theme";

const meta = () => document.getElementById("theme-color-meta");

beforeEach(() => {
    document.head.innerHTML =
        '<meta id="theme-color-meta" name="theme-color" content="" />';
    document.documentElement.removeAttribute("data-theme");
    document.documentElement.removeAttribute("data-theme-mode");
    localStorage.clear();
});
afterEach(() => vi.restoreAllMocks());

describe("readThemeMode", () => {
    it("returns the stored mode, including system", () => {
        localStorage.setItem(STORAGE_KEY, "system");
        expect(readThemeMode()).toBe("system");
        localStorage.setItem(STORAGE_KEY, "dark");
        expect(readThemeMode()).toBe("dark");
    });
    it("falls back to system for a missing or unknown value", () => {
        expect(readThemeMode()).toBe("system");
        localStorage.setItem(STORAGE_KEY, "sepia");
        expect(readThemeMode()).toBe("system");
    });
    it("survives a throwing localStorage", () => {
        vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
            throw new Error("blocked");
        });
        expect(readThemeMode()).toBe("system");
    });
});

describe("resolveAppearance", () => {
    it("maps system through the media query and passes explicit modes through", () => {
        expect(resolveAppearance("system", () => true)).toBe("dark");
        expect(resolveAppearance("system", () => false)).toBe("light");
        expect(resolveAppearance("light", () => true)).toBe("light");
        expect(resolveAppearance("dark", () => false)).toBe("dark");
    });
});

describe("applyTheme", () => {
    it("writes both attributes, the meta colour and the stored mode", () => {
        applyTheme("dark");
        expect(document.documentElement.dataset.theme).toBe("dark");
        expect(document.documentElement.dataset.themeMode).toBe("dark");
        expect(meta()?.getAttribute("content")).toBe("#0a0a0a");
        expect(localStorage.getItem(STORAGE_KEY)).toBe("dark");
    });
    it("persists system verbatim and resolves it", () => {
        vi.spyOn(window, "matchMedia").mockImplementation(
            (q) =>
                ({
                    matches: false,
                    media: q,
                    addEventListener() {},
                    removeEventListener() {},
                }) as unknown as MediaQueryList,
        );
        applyTheme("system");
        expect(document.documentElement.dataset.theme).toBe("light");
        expect(document.documentElement.dataset.themeMode).toBe("system");
        expect(meta()?.getAttribute("content")).toBe("#ffffff");
        expect(localStorage.getItem(STORAGE_KEY)).toBe("system");
    });
    it("can target another document (the incoming page of a view transition)", () => {
        const incoming = document.implementation.createHTMLDocument("next");
        incoming.head.innerHTML =
            '<meta id="theme-color-meta" name="theme-color" content="" />';
        applyTheme("dark", incoming);
        expect(incoming.documentElement.dataset.theme).toBe("dark");
        expect(
            incoming
                .getElementById("theme-color-meta")
                ?.getAttribute("content"),
        ).toBe("#0a0a0a");
        expect(document.documentElement.dataset.theme).toBeUndefined();
    });
});

describe("installThemeSync", () => {
    it("carries the stored mode onto the new document before a swap", () => {
        localStorage.setItem(STORAGE_KEY, "dark");
        const dispose = installThemeSync();
        const incoming = document.implementation.createHTMLDocument("next");
        const event = new Event("astro:before-swap") as Event & {
            newDocument: Document;
        };
        event.newDocument = incoming;
        document.dispatchEvent(event);
        expect(incoming.documentElement.dataset.theme).toBe("dark");
        dispose();
        const later = document.implementation.createHTMLDocument("later");
        const again = new Event("astro:before-swap") as Event & {
            newDocument: Document;
        };
        again.newDocument = later;
        document.dispatchEvent(again);
        expect(later.documentElement.dataset.theme).toBeUndefined();
    });
});
