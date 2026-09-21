import { describe, expect, it } from "vitest";
import type { SiteManifest } from "./manifest";
import {
    buildMatrix,
    defaultState,
    entryIdFor,
    neighbourId,
    parseState,
    serializeState,
} from "./matrix";

const value = (id: string) => ({ id, label: id });
const entry = (geometry: string, scale: string, theme: string) => ({
    id: `${geometry}__${scale}__${theme}`,
    widthDp: 800,
    heightDp: 480,
    values: { geometry, scale, theme },
    full: `full/${geometry}__${scale}__${theme}.webp`,
    thumb: `thumb/${geometry}__${scale}__${theme}.webp`,
    widthPx: 800,
    heightPx: 480,
});

const manifest: SiteManifest = {
    schemaVersion: 1,
    generatedAt: "2026-09-20T12:00:00Z",
    gitSha: "abc1234",
    thumbWidth: 480,
    axes: [
        {
            id: "geometry",
            label: "Geometry",
            values: [value("floor"), value("head-unit")],
        },
        {
            id: "scale",
            label: "Display size",
            values: [value("small"), value("medium"), value("large")],
        },
        {
            id: "theme",
            label: "Theme",
            values: [value("light"), value("dark")],
        },
    ],
    // A filtered subset: head-unit has no "large" light render.
    entries: [
        entry("floor", "small", "light"),
        entry("floor", "medium", "light"),
        entry("floor", "large", "light"),
        entry("head-unit", "small", "light"),
        entry("head-unit", "medium", "light"),
        entry("floor", "small", "dark"),
    ],
};

describe("defaultState", () => {
    it("uses the first two axes as rows and columns and the first value of every other axis", () => {
        expect(defaultState(manifest)).toEqual({
            rows: "geometry",
            cols: "scale",
            fixed: { theme: "light" },
            open: null,
        });
    });
});

describe("parseState", () => {
    it("reads rows, cols, fixed axes and open from the query", () => {
        expect(
            parseState(
                "?rows=scale&cols=theme&geometry=head-unit&open=head-unit__small__light",
                manifest,
            ),
        ).toEqual({
            rows: "scale",
            cols: "theme",
            fixed: { geometry: "head-unit" },
            open: "head-unit__small__light",
        });
    });

    it("falls back field by field on unknown values", () => {
        // cols=theme is deliberately non-default (the default cols is
        // "scale"), so this only passes if cols was preserved rather than
        // paired-reset alongside the invalid rows.
        expect(
            parseState(
                "?rows=nope&cols=theme&scale=nope&open=missing",
                manifest,
            ),
        ).toEqual({
            rows: "geometry", // invalid rows param -> default
            cols: "theme", // valid, non-default cols param -> preserved
            fixed: { scale: "small" }, // invalid fixed value -> default
            open: null, // invalid open param -> null
        });
    });

    it("keeps a valid, non-default cols when only rows is unknown", () => {
        expect(parseState("?rows=nope&cols=theme", manifest)).toEqual({
            rows: "geometry",
            cols: "theme",
            fixed: { scale: "small" },
            open: null,
        });
    });

    it("keeps a valid, non-default rows when only cols is unknown", () => {
        expect(parseState("?rows=theme&cols=nope", manifest)).toEqual({
            rows: "theme",
            cols: "scale",
            fixed: { geometry: "floor" },
            open: null,
        });
    });

    it("resets both rows and cols when the per-field fallback collides", () => {
        // rows=scale survives on its own; cols=nope falls back to the
        // default cols, which is also "scale" — the collision forces both
        // back to their defaults rather than leaving rows === cols.
        expect(parseState("?rows=scale&cols=nope", manifest)).toEqual(
            defaultState(manifest),
        );
    });

    it("rejects rows equal to cols", () => {
        expect(parseState("?rows=scale&cols=scale", manifest)).toEqual(
            defaultState(manifest),
        );
    });

    it("recomputes the fixed axes when rows/cols change", () => {
        expect(parseState("?rows=theme&cols=geometry", manifest).fixed).toEqual(
            { scale: "small" },
        );
    });

    it("derives the fixed axes from a valid `open` entry instead of the URL's per-axis params", () => {
        // floor__small__dark is the fixture's only dark entry, and the URL
        // carries no theme param at all: without deriving `fixed` from the
        // entry, theme would fall back to the axis's first value ("light"),
        // landing the matrix on a slice that doesn't contain this entry.
        expect(parseState("?open=floor__small__dark", manifest)).toEqual({
            rows: "geometry",
            cols: "scale",
            fixed: { theme: "dark" },
            open: "floor__small__dark",
        });
    });

    it("lets a valid `open` entry override a contradicting fixed-axis param", () => {
        expect(
            parseState("?theme=light&open=floor__small__dark", manifest),
        ).toEqual({
            rows: "geometry",
            cols: "scale",
            fixed: { theme: "dark" },
            open: "floor__small__dark",
        });
    });

    it("falls back to the URL's per-axis params when `open` is unknown", () => {
        expect(parseState("?theme=dark&open=missing", manifest)).toEqual({
            rows: "geometry",
            cols: "scale",
            fixed: { theme: "dark" },
            open: null,
        });
    });
});

describe("serializeState", () => {
    it("round-trips through parseState", () => {
        const state = {
            rows: "scale",
            cols: "theme",
            fixed: { geometry: "head-unit" },
            open: "head-unit__small__light",
        };
        expect(serializeState(state, manifest)).toBe(
            "?rows=scale&cols=theme&geometry=head-unit&open=head-unit__small__light",
        );
        expect(parseState(serializeState(state, manifest), manifest)).toEqual(
            state,
        );
    });

    it("omits open when nothing is open", () => {
        expect(serializeState(defaultState(manifest), manifest)).toBe(
            "?rows=geometry&cols=scale&theme=light",
        );
    });

    it("orders multiple remaining-axis params by axis order and round-trips", () => {
        // The 3-axis fixture above always leaves exactly one remaining axis,
        // which can never expose an ordering bug in the per-remaining-axis
        // loop; this local 4-axis manifest leaves two (theme, dock).
        const fourAxisManifest: SiteManifest = {
            schemaVersion: 1,
            generatedAt: "2026-09-20T12:00:00Z",
            gitSha: "abc1234",
            thumbWidth: 480,
            axes: [
                {
                    id: "geometry",
                    label: "Geometry",
                    values: [value("floor"), value("head-unit")],
                },
                {
                    id: "scale",
                    label: "Display size",
                    values: [value("small"), value("large")],
                },
                {
                    id: "theme",
                    label: "Theme",
                    values: [value("light"), value("dark")],
                },
                {
                    id: "dock",
                    label: "Dock",
                    values: [value("left"), value("right")],
                },
            ],
            entries: [
                {
                    id: "floor__small__dark__left",
                    widthDp: 800,
                    heightDp: 480,
                    values: {
                        geometry: "floor",
                        scale: "small",
                        theme: "dark",
                        dock: "left",
                    },
                    full: "full/floor__small__dark__left.webp",
                    thumb: "thumb/floor__small__dark__left.webp",
                    widthPx: 800,
                    heightPx: 480,
                },
                {
                    id: "floor__small__light__right",
                    widthDp: 800,
                    heightDp: 480,
                    values: {
                        geometry: "floor",
                        scale: "small",
                        theme: "light",
                        dock: "right",
                    },
                    full: "full/floor__small__light__right.webp",
                    thumb: "thumb/floor__small__light__right.webp",
                    widthPx: 800,
                    heightPx: 480,
                },
            ],
        };
        const state = parseState("?theme=dark&dock=left", fourAxisManifest);
        expect(serializeState(state, fourAxisManifest)).toBe(
            "?rows=geometry&cols=scale&theme=dark&dock=left",
        );
        expect(
            parseState(
                serializeState(state, fourAxisManifest),
                fourAxisManifest,
            ),
        ).toEqual(state);
    });
});

describe("buildMatrix", () => {
    it("lays out rows × cols with null cells where no entry was rendered", () => {
        const matrix = buildMatrix(manifest, defaultState(manifest));
        expect(matrix.rowAxis.id).toBe("geometry");
        expect(matrix.colAxis.id).toBe("scale");
        expect(matrix.rows.map((row) => row.rowValue.id)).toEqual([
            "floor",
            "head-unit",
        ]);
        expect(
            matrix.rows[1]!.cells.map((cell) => cell.entry?.id ?? null),
        ).toEqual([
            "head-unit__small__light",
            "head-unit__medium__light",
            null,
        ]);
    });

    it("applies the fixed axes", () => {
        const matrix = buildMatrix(manifest, {
            ...defaultState(manifest),
            fixed: { theme: "dark" },
        });
        expect(
            matrix.rows[0]!.cells.map((cell) => cell.entry?.id ?? null),
        ).toEqual(["floor__small__dark", null, null]);
    });
});

describe("entryIdFor", () => {
    it("joins the values in axis order", () => {
        expect(
            entryIdFor(
                { theme: "dark", scale: "small", geometry: "floor" },
                manifest,
            ),
        ).toBe("floor__small__dark");
    });
});

describe("neighbourId", () => {
    const open = (id: string) => ({ ...defaultState(manifest), open: id });

    it("moves along the row and column in all four directions", () => {
        expect(
            neighbourId(open("floor__small__light"), manifest, "right"),
        ).toBe("floor__medium__light");
        expect(neighbourId(open("floor__small__light"), manifest, "down")).toBe(
            "head-unit__small__light",
        );
        expect(
            neighbourId(open("floor__medium__light"), manifest, "left"),
        ).toBe("floor__small__light");
        expect(
            neighbourId(open("head-unit__small__light"), manifest, "up"),
        ).toBe("floor__small__light");
    });

    it("skips empty cells and stops at the edges", () => {
        expect(
            neighbourId(open("head-unit__medium__light"), manifest, "right"),
        ).toBeNull();
        expect(
            neighbourId(open("floor__large__light"), manifest, "down"),
        ).toBeNull();
        expect(
            neighbourId(open("floor__small__light"), manifest, "left"),
        ).toBeNull();
    });

    it("returns null when nothing is open", () => {
        expect(
            neighbourId(defaultState(manifest), manifest, "right"),
        ).toBeNull();
    });

    it("skips through an empty cell in the middle of a row to reach a further filled cell", () => {
        // Unlike the edge-stop cases above, the gap here sits between two
        // filled cells (medium is missing, small and large are not), so this
        // only passes if the loop keeps stepping past the null cell instead
        // of stopping at the first one it meets.
        const gapManifest: SiteManifest = {
            ...manifest,
            entries: [
                entry("floor", "small", "light"),
                entry("floor", "large", "light"),
            ],
        };
        expect(
            neighbourId(
                { ...defaultState(gapManifest), open: "floor__small__light" },
                gapManifest,
                "right",
            ),
        ).toBe("floor__large__light");
    });
});
