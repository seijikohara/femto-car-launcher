import siteConfig from "@/site.config";

// One list shared by Header and Footer so the two can never disagree.
export function getEnabledNavItems(): Array<{
    name: string;
    href: string;
    section?: string;
}> {
    return siteConfig.nav.main;
}

// currentPath is under the base-prefixed target: an exact match counts (so a
// section equal to the current page is active), and so does anything one level
// or deeper below it; trailing-slash tolerant, since hrefs are written with one.
function isUnderBase(currentPath: string, target: string): boolean {
    const targetBase = target.endsWith("/") ? target.slice(0, -1) : target;
    return currentPath === target || currentPath.startsWith(targetBase + "/");
}

/**
 * Decide whether a nav item counts as the current page. Home ("/") only
 * matches the home page itself, or every page would read as "under home".
 * Every other item matches its own href, or — when given — the wider
 * `section` prefix, so one nav entry (e.g. "Features") can stay current
 * across a whole area instead of only its own landing page.
 */
export function isNavItemActive(
    currentPath: string,
    item: { href: string; section?: string },
    base: (path: string) => string,
): boolean {
    if (item.href === "/") return currentPath === base("/");
    if (isUnderBase(currentPath, base(item.href))) return true;
    return (
        item.section !== undefined &&
        isUnderBase(currentPath, base(item.section))
    );
}
