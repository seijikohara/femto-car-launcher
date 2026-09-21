// @vitest-environment jsdom
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import SiteHeader from "./SiteHeader";

const props = {
    brand: { name: "Femto Car Launcher", href: "/b/", logoSrc: "/b/logo.svg" },
    items: [
        { name: "Home", href: "/b/", active: false },
        { name: "Catalog", href: "/b/catalog/", active: true },
    ],
    showThemeToggle: true,
};

beforeEach(() => {
    document.head.innerHTML =
        '<meta id="theme-color-meta" name="theme-color" content="" />';
    document.documentElement.dataset.theme = "light";
    document.documentElement.dataset.themeMode = "system";
    localStorage.clear();
});
afterEach(cleanup);

describe("SiteHeader", () => {
    it("renders the brand and the nav with the current page marked", () => {
        render(<SiteHeader {...props} />);
        expect(
            screen.getByRole("link", { name: /Femto Car Launcher/ }),
        ).toHaveProperty("href", expect.stringContaining("/b/"));
        const nav = screen.getByRole("navigation", { name: "Main navigation" });
        const current = nav.querySelector('a[aria-current="page"]');
        expect(current?.textContent).toBe("Catalog");
    });

    it("opens the mobile menu in a sheet and closes it when a link is chosen", async () => {
        const user = userEvent.setup();
        render(<SiteHeader {...props} />);
        await user.click(screen.getByRole("button", { name: "Open menu" }));
        const dialog = await screen.findByRole("dialog", { name: "Menu" });
        const link = dialog.querySelector('a[href="/b/catalog/"]');
        expect(link).not.toBeNull();
        await user.click(link as HTMLElement);
        await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    });

    it("switches the theme from the menu and persists the mode", async () => {
        const user = userEvent.setup();
        render(<SiteHeader {...props} />);
        await user.click(screen.getByRole("button", { name: /Theme/ }));
        await user.click(
            await screen.findByRole("menuitemradio", { name: "Dark" }),
        );
        await waitFor(() =>
            expect(document.documentElement.dataset.theme).toBe("dark"),
        );
        expect(document.documentElement.dataset.themeMode).toBe("dark");
        expect(localStorage.getItem("theme")).toBe("dark");
        expect(
            document
                .getElementById("theme-color-meta")
                ?.getAttribute("content"),
        ).toBe("#0e1318");
    });

    it("closes an open sheet before a client-side navigation swap", async () => {
        const user = userEvent.setup();
        render(<SiteHeader {...props} />);
        await user.click(screen.getByRole("button", { name: "Open menu" }));
        await screen.findByRole("dialog", { name: "Menu" });
        document.dispatchEvent(new Event("astro:before-swap"));
        await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    });
});
