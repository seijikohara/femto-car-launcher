import siteConfig from "@/site.config";

// One list shared by Header and Footer so the two can never disagree.
export function getEnabledNavItems(): Array<{ name: string; href: string }> {
    return siteConfig.nav.main;
}
