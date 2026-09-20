import { defineCollection, z } from "astro:content";
import { glob } from "astro/loaders";

// Long-form feature pages: the site is the SSOT for this text (README keeps
// only the short introduction).
const features = defineCollection({
    loader: glob({ pattern: "*.mdx", base: "./src/content/features" }),
    schema: z.object({
        title: z.string(),
        summary: z.string(),
        order: z.number().int().positive(),
    }),
});

// The legal pages ARE the repository's PRIVACY.md and TERMS.md, read from the
// repo root so the site and the app can never disagree on their text.
const legal = defineCollection({
    loader: glob({
        pattern: "{PRIVACY,TERMS}.md",
        base: "..",
        generateId: ({ entry }) => entry.replace(/\.md$/, "").toLowerCase(),
    }),
});

export const collections = { features, legal };
