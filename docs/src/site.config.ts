// Site identity and navigation. The canonical origin + base path live in
// ../site.base.json (astro.config reads them); `url` here is only the fallback
// Seo uses when Astro.site is unavailable.
import dashboardLight from "../../app/src/test/screenshots/dashboard-head-unit-853x512.png";

export interface SiteConfig {
    name: string;
    title: string;
    description: string;
    url: string;
    /** Default social-card image (an astro:assets URL or a public/ path). */
    ogImage: string;
    /** BCP47 language tag for <html lang>. */
    lang: string;
    /** og:locale value; derived from `lang` when omitted. */
    ogLocale?: string;
    theme: {
        defaultColorMode: "light" | "dark" | "system";
        showThemeToggle: boolean;
    };
    nav: { main: Array<{ name: string; href: string }> };
    footer: { links: Array<{ name: string; href: string }> };
    repository: string;
    nightlyRelease: string;
}

const siteConfig: SiteConfig = {
    name: "Femto Car Launcher",
    title: "Femto Car Launcher — a glanceable Android home launcher for in-car displays",
    description:
        "A glanceable Android home launcher for in-car displays: live map, driving data, weather, calendar and media on one screen, built for aftermarket AI boxes, Android head units and car-mounted phones.",
    url: "https://seijikohara.github.io/femto-car-launcher",
    // The committed screenshot-test golden is the canonical dashboard look, so
    // it doubles as the social card; the CI-recorded PNG can never drift.
    ogImage: dashboardLight.src,
    lang: "en",
    ogLocale: "en_US",
    theme: {
        defaultColorMode: "system",
        showThemeToggle: true,
    },
    nav: {
        main: [
            { name: "Home", href: "/" },
            { name: "Features", href: "/features/live-map/" },
            { name: "Install", href: "/install/" },
        ],
    },
    footer: {
        links: [
            {
                name: "Source on GitHub",
                href: "https://github.com/seijikohara/femto-car-launcher",
            },
            {
                name: "Nightly APK",
                href: "https://github.com/seijikohara/femto-car-launcher/releases/tag/nightly",
            },
            { name: "Privacy Policy", href: "/privacy/" },
            { name: "Terms of Service", href: "/terms/" },
        ],
    },
    repository: "https://github.com/seijikohara/femto-car-launcher",
    nightlyRelease:
        "https://github.com/seijikohara/femto-car-launcher/releases/tag/nightly",
};

// Conventional region for language-only BCP47 tags, so 'en' → 'en_US' rather
// than 'en_EN'. Set `ogLocale` explicitly for exact control.
const COMMON_REGIONS: Record<string, string> = {
    en: "US",
    ja: "JP",
    zh: "CN",
    fr: "FR",
    de: "DE",
    es: "ES",
    pt: "PT",
    ko: "KR",
    it: "IT",
};

export function deriveOgLocale(lang: string): string {
    const [language = lang, region] = lang.split("-");
    const fallbackRegion = COMMON_REGIONS[language.toLowerCase()] ?? language;
    return `${language}_${(region ?? fallbackRegion).toUpperCase()}`;
}

export const resolvedOgLocale =
    siteConfig.ogLocale ?? deriveOgLocale(siteConfig.lang);

export default siteConfig;
