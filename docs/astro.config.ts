import mdx from "@astrojs/mdx";
import { satteri } from "@astrojs/markdown-satteri";
import react from "@astrojs/react";
import sitemap from "@astrojs/sitemap";
import { defineConfig } from "astro/config";
import satteriBaseUrls from "./src/lib/satteri-base-urls";
import satteriRepoLinks from "./src/lib/satteri-repo-links";
import siteBase from "./site.base.json" with { type: "json" };

// site + base come from site.base.json, the one home of the GitHub Pages
// address (scripts/check-dist-links.ts reads the same file).
export default defineConfig({
    site: siteBase.site,
    base: siteBase.base,
    output: "static",
    // GitHub Pages 301-redirects slash-less directory requests to the
    // trailing-slash form, so internal links must match to avoid a redirect
    // hop and a canonical/link mismatch.
    trailingSlash: "always",
    build: {
        format: "directory",
        inlineStylesheets: "auto",
    },
    // React only for the catalog island (src/catalog); every other page
    // ships no client framework.
    integrations: [mdx(), sitemap(), react()],
    // AVIF/WebP output is chosen per image (see ui/Picture.astro's `formats`
    // prop to astro:assets Picture) — Astro's `image` config has no global
    // format key.
    image: {
        service: { entrypoint: "astro/assets/services/sharp" },
    },
    // Astro 7 renders Markdown with Sätteri; MDX inherits this config. The
    // hast pass prefixes `base` onto root-relative URLs an author writes in a
    // body (Astro leaves body URLs untouched). Task 3 prepends the plugin that
    // maps repository-file links.
    markdown: {
        processor: satteri({
            hastPlugins: [
                // Order matters: repo links first (they may yield a root-relative
                // route), then the base prefix.
                satteriRepoLinks({
                    repoBlobBase:
                        "https://github.com/seijikohara/femto-car-launcher/blob/main/",
                    routes: {
                        "README.md": "/",
                        "PRIVACY.md": "/privacy/",
                        "TERMS.md": "/terms/",
                    },
                }),
                satteriBaseUrls(siteBase.base),
            ],
        }),
    },
    vite: {
        // The site imports the repository's logo.svg, the golden PNGs and the
        // legal Markdown from outside docs/; the dev server must be allowed to
        // serve them (the production build bundles them regardless).
        server: { fs: { allow: [".."] } },
    },
});
