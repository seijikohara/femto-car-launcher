import type { HastPluginDefinition } from "satteri";
import { classifyHref } from "./url";

export interface RepoLinksOptions {
    /** URL prefix for repository files the site does not render itself. */
    repoBlobBase: string;
    /** Repository-relative Markdown files the site renders → root-relative site routes. */
    routes: Record<string, string>;
}

const splitHash = (href: string): [string, string] => {
    const index = href.indexOf("#");
    return index === -1
        ? [href, ""]
        : [href.slice(0, index), href.slice(index)];
};

/**
 * Rewrite a relative repository path. Returns undefined for every other kind of
 * href (root-relative, external, mailto, anchor) so the caller leaves it alone —
 * root-relative routes are satteri-base-urls' job, which runs after this plugin.
 */
export const rewriteRepoHref = (
    href: string,
    options: RepoLinksOptions,
): string | undefined => {
    if (classifyHref(href) !== "relative") return undefined;
    const [path, hash] = splitHash(href);
    const route = options.routes[path];
    return route === undefined
        ? `${options.repoBlobBase}${path}${hash}`
        : `${route}${hash}`;
};

/**
 * The legal pages render the repository's own Markdown in place, so a link
 * written for GitHub (`[Privacy Policy](PRIVACY.md)`) must land on the page
 * that renders that file, and a link to any other repository file must open it
 * on GitHub rather than 404 under the site.
 */
export default function satteriRepoLinks(
    options: RepoLinksOptions,
): HastPluginDefinition {
    return {
        name: "femto:repo-links",
        element: {
            filter: ["a"],
            visit(node, ctx) {
                const href = node.properties?.href;
                if (typeof href !== "string") return;
                const rewritten = rewriteRepoHref(href, options);
                if (rewritten !== undefined)
                    ctx.setProperty(node, "href", rewritten);
            },
        },
    };
}
