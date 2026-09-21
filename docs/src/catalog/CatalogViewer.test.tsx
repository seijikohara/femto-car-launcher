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
// through React (onClose etc.), not a bypass around it. showModal() also
// throws on an already-open dialog, matching real browsers, so a test can
// prove the open/close effect is keyed on the open/closed boolean rather
// than on the entry id: calling showModal() again on an entry-to-entry step
// would fail the test instead of silently resetting focus.
let originalShowModal: typeof HTMLDialogElement.prototype.showModal;
let originalClose: typeof HTMLDialogElement.prototype.close;

beforeAll(() => {
    originalShowModal = HTMLDialogElement.prototype.showModal;
    originalClose = HTMLDialogElement.prototype.close;
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
        if (this.open) {
            throw new DOMException(
                "The dialog is already open.",
                "InvalidStateError",
            );
        }
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
        // aria-labelledby points at the caption span rather than the whole
        // figcaption, so the accessible name stops before "open the file".
        expect(
            screen.getByRole("dialog", { name: /Floor · Medium · Light/ }),
        ).toBe(dialog);
        fireEvent.keyDown(dialog, { key: "Escape" });
        await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
        expect(window.location.search).not.toContain("open=");
    });

    it("preserves the router's history state when it rewrites the URL", async () => {
        // Simulates an entry the ClientRouter already touched: it keeps
        // {index, scrollX, scrollY} in history.state and ignores popstate
        // when that state is null, so a viewer interaction must carry the
        // existing state forward through replaceState rather than clobber it.
        window.history.replaceState({ index: 3 }, "", "/");
        stubFetch({ ok: true, status: 200, body: manifest });
        render(
            <CatalogViewer
                manifestUrl="/b/catalog/manifest.json"
                assetBase="/b/catalog/"
            />,
        );
        await screen.findByRole("table");
        fireEvent.click(screen.getByRole("radio", { name: "Dark" }));
        await waitFor(() =>
            expect(window.location.search).toContain("theme=dark"),
        );
        expect(window.history.state).toEqual({ index: 3 });
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

    it("focuses the Close button when the lightbox opens", async () => {
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
        const closeButton = screen.getByRole("button", { name: "Close" });
        expect(closeButton.hasAttribute("autofocus")).toBe(true);
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
        // Still open — the showModal() stub throws on a second call while
        // already open, so this also proves the boolean-keyed open effect
        // did not call it again for this entry-to-entry step.
        expect(dialog.hasAttribute("open")).toBe(true);
        // An arrow step only swaps state.open; nothing here moves focus, so
        // it must stay exactly where the user left it.
        expect(document.activeElement).toBe(closeButton);
        expect(dialog.querySelector("img")?.getAttribute("src")).toBe(
            "/b/catalog/full/floor__medium__light.webp",
        );
    });

    it("keeps focus on an arrow button after it becomes edge-disabled by its own activation", async () => {
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
        fireEvent.click(nextColumn);
        await waitFor(() =>
            expect(window.location.search).toContain(
                "open=floor__medium__light",
            ),
        );
        expect(nextColumn.getAttribute("aria-disabled")).toBe("true");
        // The native `disabled` attribute would have dropped this button from
        // the focus tree in the same render; aria-disabled keeps it
        // focusable so the dialog's own keydown handler keeps receiving
        // arrow/Escape keys without the user needing to tab back in.
        expect(document.activeElement).toBe(nextColumn);
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
