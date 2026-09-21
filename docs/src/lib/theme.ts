// Theme preference shared by the pre-paint script in BaseLayout (which must
// stay inline and duplicates the read/resolve logic), the SiteHeader island's
// menu, and the view-transition hook that carries the choice onto the next page.
export type ThemeMode = "system" | "light" | "dark";
export type Appearance = "light" | "dark";

export const THEME_MODES: readonly ThemeMode[] = ["system", "light", "dark"];
export const STORAGE_KEY = "theme";

// These hex values mirror --background in global.css (light #ffffff / dark
// #0a0a0a) — change them together; this module runs before the stylesheet
// is available to read from.
const THEME_COLOR: Record<Appearance, string> = {
    dark: "#0a0a0a",
    light: "#ffffff",
};

const isMode = (value: string | null): value is ThemeMode =>
    value !== null && (THEME_MODES as readonly string[]).includes(value);

// localStorage can throw (Safari "block all cookies", private mode, sandboxed
// iframes); a failure must degrade to "system", never break the page.
export function readThemeMode(): ThemeMode {
    try {
        const stored = localStorage.getItem(STORAGE_KEY);
        return isMode(stored) ? stored : "system";
    } catch {
        return "system";
    }
}

export function resolveAppearance(
    mode: ThemeMode,
    prefersDark: () => boolean,
): Appearance {
    if (mode === "system") return prefersDark() ? "dark" : "light";
    return mode;
}

const systemPrefersDark = (): boolean => {
    try {
        return window.matchMedia("(prefers-color-scheme: dark)").matches;
    } catch {
        return false;
    }
};

/** Apply `mode` to `doc` (default: the current document) and persist it. */
export function applyTheme(mode: ThemeMode, doc: Document = document): void {
    const appearance = resolveAppearance(mode, systemPrefersDark);
    doc.documentElement.dataset.theme = appearance;
    doc.documentElement.dataset.themeMode = mode;
    doc.getElementById("theme-color-meta")?.setAttribute(
        "content",
        THEME_COLOR[appearance],
    );
    try {
        localStorage.setItem(STORAGE_KEY, mode);
    } catch {
        /* preference simply does not persist */
    }
}

/**
 * Keep the theme alive across client-side navigations and OS changes.
 * `astro:before-swap` hands over the incoming document before it replaces the
 * current one; writing the attributes there avoids a flash, because the router
 * copies only the server-rendered <html> attributes (which carry no theme).
 */
export function installThemeSync(): () => void {
    const onBeforeSwap = (event: Event) => {
        const incoming = (event as Event & { newDocument?: Document })
            .newDocument;
        if (incoming) applyTheme(readThemeMode(), incoming);
    };
    document.addEventListener("astro:before-swap", onBeforeSwap);

    let media: MediaQueryList | null = null;
    const onMediaChange = () => {
        if (readThemeMode() === "system") applyTheme("system");
    };
    try {
        media = window.matchMedia("(prefers-color-scheme: dark)");
        media.addEventListener("change", onMediaChange);
    } catch {
        media = null;
    }
    return () => {
        document.removeEventListener("astro:before-swap", onBeforeSwap);
        media?.removeEventListener("change", onMediaChange);
    };
}
