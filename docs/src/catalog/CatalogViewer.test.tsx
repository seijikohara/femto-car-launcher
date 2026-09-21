// @vitest-environment jsdom
import {
    cleanup,
    fireEvent,
    render,
    screen,
    waitFor,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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

    it("opens the lightbox with the full image and mirrors the state into the URL, then closes on Escape and returns focus to the originating cell", async () => {
        const user = userEvent.setup();
        stubFetch({ ok: true, status: 200, body: manifest });
        render(
            <CatalogViewer
                manifestUrl="/b/catalog/manifest.json"
                assetBase="/b/catalog/"
            />,
        );
        await screen.findByRole("table");
        const cellButton = screen.getByRole("button", {
            name: /Floor · Medium/,
        });
        await user.click(cellButton);
        // aria-labelledby points at DialogTitle alone, so the accessible name
        // stops before "open the file" — an exact string (not a regex) is
        // what proves the link text is out.
        const dialog = await screen.findByRole("dialog", {
            name: "Floor · Medium · Light · 800×480 dp",
        });
        expect(dialog.querySelector("img")?.getAttribute("src")).toBe(
            "/b/catalog/full/floor__medium__light.webp",
        );
        expect(window.location.search).toContain("open=floor__medium__light");

        fireEvent.keyDown(dialog, { key: "Escape" });
        await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
        expect(window.location.search).not.toContain("open=");
        await waitFor(() => expect(document.activeElement).toBe(cellButton));
    });

    it("preserves the router's history state when it rewrites the URL", async () => {
        // Simulates an entry the ClientRouter already touched: it keeps
        // {index, scrollX, scrollY} in history.state and ignores popstate
        // when that state is null, so a viewer interaction must carry the
        // existing state forward through replaceState rather than clobber it.
        const user = userEvent.setup();
        window.history.replaceState({ index: 3 }, "", "/");
        stubFetch({ ok: true, status: 200, body: manifest });
        render(
            <CatalogViewer
                manifestUrl="/b/catalog/manifest.json"
                assetBase="/b/catalog/"
            />,
        );
        await screen.findByRole("table");
        await user.click(
            screen.getByRole("button", { name: "Dark", pressed: false }),
        );
        await waitFor(() =>
            expect(window.location.search).toContain("theme=dark"),
        );
        expect(window.history.state).toEqual({ index: 3 });
    });

    it("switches the row axis from the controls", async () => {
        const user = userEvent.setup();
        stubFetch({ ok: true, status: 200, body: manifest });
        render(
            <CatalogViewer
                manifestUrl="/b/catalog/manifest.json"
                assetBase="/b/catalog/"
            />,
        );
        await screen.findByRole("table");
        const rowsSelect = screen.getByRole("combobox", { name: "Rows" });
        await user.click(rowsSelect);
        await user.click(await screen.findByRole("option", { name: "Theme" }));
        await waitFor(() =>
            expect(
                screen.getByRole("rowheader", { name: "Dark" }),
            ).toBeTruthy(),
        );
        expect(window.location.search).toContain("rows=theme");
        // Select.Value only resolves a label through the `items` map passed
        // to Select.Root — without it the trigger falls back to the raw
        // axis id ("theme") instead of the axis label.
        expect(rowsSelect.textContent).toContain("Theme");
    });

    it("swaps rows and columns when a select is set to the other axis's current value", async () => {
        const user = userEvent.setup();
        stubFetch({ ok: true, status: 200, body: manifest });
        render(
            <CatalogViewer
                manifestUrl="/b/catalog/manifest.json"
                assetBase="/b/catalog/"
            />,
        );
        await screen.findByRole("table");
        // Default: rows=geometry ("Floor"/"Head unit"), cols=scale ("Small"/"Medium").
        expect(screen.getByRole("rowheader", { name: "Floor" })).toBeTruthy();
        await user.click(screen.getByRole("combobox", { name: "Columns" }));
        // Picking the current rows axis for columns swaps the two instead of
        // collapsing the matrix down to a single axis.
        await user.click(
            await screen.findByRole("option", { name: "Geometry" }),
        );
        await waitFor(() =>
            expect(window.location.search).toContain("cols=geometry"),
        );
        expect(window.location.search).toContain("rows=scale");
        expect(screen.getByRole("rowheader", { name: "Small" })).toBeTruthy();
        expect(
            screen.getByRole("columnheader", { name: "Floor" }),
        ).toBeTruthy();
    });

    it("opens the lightbox for an `open` id already in the URL on load, with the fixed axis it implies", async () => {
        window.history.replaceState(null, "", "/?open=floor__medium__light");
        stubFetch({ ok: true, status: 200, body: manifest });
        render(
            <CatalogViewer
                manifestUrl="/b/catalog/manifest.json"
                assetBase="/b/catalog/"
            />,
        );
        const dialog = await screen.findByRole("dialog");
        expect(dialog.querySelector("img")?.getAttribute("src")).toBe(
            "/b/catalog/full/floor__medium__light.webp",
        );
        // geometry/scale resolve to rows/cols for this entry, leaving "theme"
        // as the sole fixed axis — parseState (matrix.ts, unchanged here)
        // resolves it from the entry's own values. `hidden: true` is
        // required here: the open modal dialog marks the rest of the tree
        // aria-hidden (Base UI's inert background), so the toggle group sits
        // outside the default accessibility-tree query until the dialog closes.
        expect(
            screen.getByRole("button", {
                name: "Light",
                pressed: true,
                hidden: true,
            }),
        ).toBeTruthy();
    });

    it("moves to the neighbour on ArrowRight and updates the URL, keeping the same dialog element and focus", async () => {
        stubFetch({ ok: true, status: 200, body: manifest });
        render(
            <CatalogViewer
                manifestUrl="/b/catalog/manifest.json"
                assetBase="/b/catalog/"
            />,
        );
        await screen.findByRole("table");
        fireEvent.click(screen.getByRole("button", { name: /Floor · Small/ }));
        const dialog = await screen.findByRole("dialog");
        // Focus the Close button before stepping, so a focus change caused by
        // the arrow step (there should be none) is observable below.
        const closeButton = screen.getByRole("button", { name: "Close" });
        closeButton.focus();
        fireEvent.keyDown(dialog, { key: "ArrowRight" });
        await waitFor(() =>
            expect(window.location.search).toContain(
                "open=floor__medium__light",
            ),
        );
        // Still the same dialog element — only state.open (the entry id)
        // changed, so React must not have torn the dialog down and back up.
        expect(screen.getByRole("dialog")).toBe(dialog);
        // An arrow step only swaps state.open; nothing here moves focus, so
        // it must stay exactly where the user left it.
        expect(document.activeElement).toBe(closeButton);
        expect(dialog.querySelector("img")?.getAttribute("src")).toBe(
            "/b/catalog/full/floor__medium__light.webp",
        );
    });

    it("keeps focus on an arrow button after it becomes edge-disabled by its own activation", async () => {
        const user = userEvent.setup();
        stubFetch({ ok: true, status: 200, body: manifest });
        render(
            <CatalogViewer
                manifestUrl="/b/catalog/manifest.json"
                assetBase="/b/catalog/"
            />,
        );
        await screen.findByRole("table");
        fireEvent.click(screen.getByRole("button", { name: /Floor · Small/ }));
        await screen.findByRole("dialog");
        // Floor · Small sits in column 0 of 2 ("Small"/"Medium"): "Next
        // column" is still enabled here, and becomes edge-disabled once the
        // step it triggers lands on the last column.
        const nextColumn = screen.getByRole("button", { name: "Next column" });
        nextColumn.focus();
        await user.click(nextColumn);
        await waitFor(() =>
            expect(window.location.search).toContain(
                "open=floor__medium__light",
            ),
        );
        expect(nextColumn.getAttribute("aria-disabled")).toBe("true");
        // focusableWhenDisabled reports the disabled state through
        // aria-disabled instead of the native `disabled` attribute, which
        // would have dropped this button from the focus tree in the same
        // render — the dialog's own keydown handler keeps receiving
        // arrow/Escape keys without the user needing to tab back in.
        expect(nextColumn.hasAttribute("disabled")).toBe(false);
        expect(document.activeElement).toBe(nextColumn);
    });

    it("changes the visible cells and the URL when a fixed-axis toggle changes", async () => {
        const user = userEvent.setup();
        stubFetch({ ok: true, status: 200, body: manifest });
        render(
            <CatalogViewer
                manifestUrl="/b/catalog/manifest.json"
                assetBase="/b/catalog/"
            />,
        );
        await screen.findByRole("table");
        expect(
            screen.getAllByRole("button", { name: /^Open / }).length,
        ).toBeGreaterThan(0);
        await user.click(
            screen.getByRole("button", { name: "Dark", pressed: false }),
        );
        await waitFor(() =>
            expect(window.location.search).toContain("theme=dark"),
        );
        // The fixture only has light-theme entries, so every cell is now empty.
        expect(
            screen.queryAllByRole("button", { name: /^Open / }),
        ).toHaveLength(0);
    });

    it("closes when the overlay is clicked, clearing `open=` from the URL", async () => {
        const user = userEvent.setup();
        stubFetch({ ok: true, status: 200, body: manifest });
        render(
            <CatalogViewer
                manifestUrl="/b/catalog/manifest.json"
                assetBase="/b/catalog/"
            />,
        );
        await screen.findByRole("table");
        fireEvent.click(screen.getByRole("button", { name: /Floor · Small/ }));
        await screen.findByRole("dialog");
        const overlay = document.querySelector('[data-slot="dialog-overlay"]');
        expect(overlay).not.toBeNull();
        await user.click(overlay as HTMLElement);
        await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
        expect(window.location.search).not.toContain("open=");
    });

    it("renders an error alert when the fetch rejects, and nothing else throws", async () => {
        vi.stubGlobal(
            "fetch",
            vi.fn(async () => {
                throw new Error("network down");
            }),
        );
        render(
            <CatalogViewer
                manifestUrl="/b/catalog/manifest.json"
                assetBase="/b/catalog/"
            />,
        );
        await waitFor(() =>
            expect(screen.getByRole("alert").textContent).toMatch(
                /network down/,
            ),
        );
    });

    it("renders an error alert for a manifest with an unsupported schema version", async () => {
        stubFetch({
            ok: true,
            status: 200,
            body: { ...manifest, schemaVersion: 2 },
        });
        render(
            <CatalogViewer
                manifestUrl="/b/catalog/manifest.json"
                assetBase="/b/catalog/"
            />,
        );
        await waitFor(() =>
            expect(screen.getByRole("alert").textContent).toMatch(
                /unsupported format/i,
            ),
        );
    });
});
