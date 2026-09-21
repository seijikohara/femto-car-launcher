// Polyfills for jsdom gaps Base UI's components rely on (portal positioning,
// pointer capture during drag/press interactions, scroll-into-view for
// highlighted list items, and the dark-mode media query). This file is a
// global setupFile, so it runs before every test file regardless of that
// file's own environment — most of this project's tests run under the
// default "node" environment (see vitest.config.ts) and only opt into jsdom
// per-file via `// @vitest-environment jsdom`. Guarding on `typeof window`
// keeps the node-environment files from throwing on a missing global.
if (typeof window !== "undefined") {
    // `Reflect.has` (not the `in` operator): lib.dom.d.ts declares `ResizeObserver`
    // as an always-present global, so `"ResizeObserver" in window` is statically
    // "impossible" to be false and TypeScript narrows that branch to `never` —
    // even though jsdom genuinely lacks it at runtime. Reflect.has is a plain
    // function call, so it performs the same check without tripping that narrowing.
    if (!Reflect.has(window, "ResizeObserver")) {
        window.ResizeObserver = class {
            observe() {}
            unobserve() {}
            disconnect() {}
        } as unknown as typeof ResizeObserver;
    }

    if (!Element.prototype.hasPointerCapture) {
        Element.prototype.hasPointerCapture = () => false;
        Element.prototype.setPointerCapture = () => {};
        Element.prototype.releasePointerCapture = () => {};
    }

    if (!Element.prototype.scrollIntoView) {
        Element.prototype.scrollIntoView = () => {};
    }

    if (!window.matchMedia) {
        window.matchMedia = (query: string) =>
            ({
                matches: false,
                media: query,
                onchange: null,
                addListener() {},
                removeListener() {},
                addEventListener() {},
                removeEventListener() {},
                dispatchEvent: () => false,
            }) as MediaQueryList;
    }
}
