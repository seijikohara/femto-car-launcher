/// <reference types="vitest/config" />
import { getViteConfig } from "astro/config";

// getViteConfig applies the Astro project config (aliases, integrations) to
// Vitest, so tests import project modules the same way the site does.
export default getViteConfig({
    test: {
        environment: "node",
        include: ["src/**/*.test.{ts,tsx}", "scripts/**/*.test.ts"],
        passWithNoTests: true,
    },
});
