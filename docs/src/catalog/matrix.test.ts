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
        expect(
            parseState(
                "?rows=nope&cols=scale&theme=sepia&open=missing",
                manifest,
            ),
        ).toEqual(defaultState(manifest));
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

    it("moves along the row and down the column", () => {
        expect(
            neighbourId(open("floor__small__light"), manifest, "right"),
        ).toBe("floor__medium__light");
        expect(neighbourId(open("floor__small__light"), manifest, "down")).toBe(
            "head-unit__small__light",
        );
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
});
