// @vitest-environment jsdom
import {
    cleanup,
    fireEvent,
    render,
    screen,
    waitFor,
} from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import CatalogViewer from "./CatalogViewer";
import type { SiteManifest } from "./manifest";

const value = (id: string, label = id) => ({ id, label });
const entry = (geometry: string, scale: string) => ({
    id: `${geometry}__${scale}__light`,
    widthDp: 800,
    heightDp: 480,
    values: { geometry, scale, theme: "light" },
    full: `full/${geometry}__${scale}__light.webp`,
    thumb: `thumb/${geometry}__${scale}__light.webp`,
    widthPx: 800,
    heightPx: 480,
});
const manifest: SiteManifest = {
    schemaVersion: 1,
    generatedAt: "2026-09-20T12:00:00Z",
    gitSha: "abc1234def",
    thumbWidth: 480,
    axes: [
        {
            id: "geometry",
            label: "Geometry",
            values: [value("floor", "Floor"), value("head-unit", "Head unit")],
        },
        {
            id: "scale",
            label: "Display size",
            values: [value("small", "Small"), value("medium", "Medium")],
        },
        {
            id: "theme",
            label: "Theme",
            values: [value("light", "Light"), value("dark", "Dark")],
        },
    ],
    entries: [
        entry("floor", "small"),
        entry("floor", "medium"),
        entry("head-unit", "small"),
    ],
};

const stubFetch = (response: { ok: boolean; status: number; body?: unknown }) =>
    vi.stubGlobal(
        "fetch",
        vi.fn(async () => ({
            ok: response.ok,
            status: response.status,
            json: async () => response.body,
        })),
    );

afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    window.history.replaceState(null, "", "/");
});

describe("CatalogViewer", () => {
    it("explains when the build carries no catalog", async () => {
        stubFetch({ ok: false, status: 404 });
        render(
            <CatalogViewer
                manifestUrl="/b/catalog/manifest.json"
                assetBase="/b/catalog/"
            />,
        );
        await waitFor(() =>
            expect(screen.getByRole("status").textContent).toMatch(
                /no catalog/i,
            ),
        );
    });

    it("renders the matrix with axis headers, thumbnails and an empty cell marker", async () => {
        stubFetch({ ok: true, status: 200, body: manifest });
        render(
            <CatalogViewer
                manifestUrl="/b/catalog/manifest.json"
                assetBase="/b/catalog/"
            />,
        );
        await screen.findByRole("table");
        expect(
            screen.getByRole("columnheader", { name: "Small" }),
        ).toBeTruthy();
        expect(
            screen.getByRole("rowheader", { name: "Head unit" }),
        ).toBeTruthy();
        // Thumbnails are decorative (alt="") inside labelled buttons, so they carry no img role.
        const thumbs = screen
            .getAllByRole("button", { name: /^Open / })
            .map((button) => button.querySelector("img")?.getAttribute("src"));
        expect(thumbs).toContain("/b/catalog/thumb/floor__small__light.webp");
        expect(screen.getByText("—")).toBeTruthy();
        expect(screen.getByText(/abc1234/)).toBeTruthy();
    });

    it("opens the lightbox with the full image and mirrors the state into the URL", async () => {
        stubFetch({ ok: true, status: 200, body: manifest });
        render(
            <CatalogViewer
                manifestUrl="/b/catalog/manifest.json"
                assetBase="/b/catalog/"
            />,
        );
        await screen.findByRole("table");
        fireEvent.click(screen.getByRole("button", { name: /Floor · Medium/ }));
        const dialog = await screen.findByRole("dialog");
        expect(dialog.querySelector("img")?.getAttribute("src")).toBe(
            "/b/catalog/full/floor__medium__light.webp",
        );
        expect(window.location.search).toContain("open=floor__medium__light");
        fireEvent.keyDown(dialog, { key: "Escape" });
        await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
        expect(window.location.search).not.toContain("open=");
    });

    it("switches the row axis from the controls", async () => {
        stubFetch({ ok: true, status: 200, body: manifest });
        render(
            <CatalogViewer
                manifestUrl="/b/catalog/manifest.json"
                assetBase="/b/catalog/"
            />,
        );
        await screen.findByRole("table");
        fireEvent.change(screen.getByLabelText("Rows"), {
            target: { value: "theme" },
        });
        await waitFor(() =>
            expect(
                screen.getByRole("rowheader", { name: "Dark" }),
            ).toBeTruthy(),
        );
        expect(window.location.search).toContain("rows=theme");
    });
});
