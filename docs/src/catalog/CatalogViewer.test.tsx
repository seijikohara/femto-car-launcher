// @vitest-environment jsdom
import {
    cleanup,
    fireEvent,
    render,
    screen,
    waitFor,
} from "@testing-library/react";
import {
    afterAll,
    afterEach,
    beforeAll,
    describe,
    expect,
    it,
    vi,
} from "vitest";
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

// jsdom 30's HTMLDialogElement has no working showModal()/close() (no
// open-attribute toggling, no `close` event) — stub the minimum
// CatalogViewer relies on so the tests exercise the real open/close path
// through React (onClose etc.), not a bypass around it.
let originalShowModal: typeof HTMLDialogElement.prototype.showModal;
let originalClose: typeof HTMLDialogElement.prototype.close;

beforeAll(() => {
    originalShowModal = HTMLDialogElement.prototype.showModal;
    originalClose = HTMLDialogElement.prototype.close;
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
        this.setAttribute("open", "");
    };
    HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
        this.removeAttribute("open");
        this.dispatchEvent(new Event("close"));
    };
});

afterAll(() => {
    HTMLDialogElement.prototype.showModal = originalShowModal;
    HTMLDialogElement.prototype.close = originalClose;
});

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

    it("opens the lightbox for an `open` id already in the URL on load", async () => {
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
    });

    it("moves to the neighbour on ArrowRight and updates the URL, keeping the same dialog element", async () => {
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
        fireEvent.keyDown(dialog, { key: "ArrowRight" });
        await waitFor(() =>
            expect(window.location.search).toContain(
                "open=floor__medium__light",
            ),
        );
        // Still the same <dialog> node — the boolean-keyed open effect must not
        // remount it on an entry-to-entry step, only on the closed<->open edge.
        expect(screen.getByRole("dialog")).toBe(dialog);
        expect(dialog.querySelector("img")?.getAttribute("src")).toBe(
            "/b/catalog/full/floor__medium__light.webp",
        );
    });

    it("changes the visible cells and the URL when a fixed-axis radio changes", async () => {
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
        fireEvent.click(screen.getByRole("radio", { name: "Dark" }));
        await waitFor(() =>
            expect(window.location.search).toContain("theme=dark"),
        );
        // The fixture only has light-theme entries, so every cell is now empty.
        expect(
            screen.queryAllByRole("button", { name: /^Open / }),
        ).toHaveLength(0);
    });

    it("closes when the backdrop — the dialog element itself as the click target — is clicked", async () => {
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
        fireEvent.click(dialog);
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
