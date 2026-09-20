import type { Axis, AxisValue, SiteEntry, SiteManifest } from "./manifest";

export interface ViewState {
    rows: string;
    cols: string;
    fixed: Record<string, string>;
    open: string | null;
}

export interface MatrixCell {
    colValue: AxisValue;
    entry: SiteEntry | null;
}

export interface MatrixRow {
    rowValue: AxisValue;
    cells: MatrixCell[];
}

export interface Matrix {
    rowAxis: Axis;
    colAxis: Axis;
    rows: MatrixRow[];
}

export type Direction = "left" | "right" | "up" | "down";

const axisById = (manifest: SiteManifest, id: string): Axis | undefined =>
    manifest.axes.find((axis) => axis.id === id);

const firstValueId = (axis: Axis): string => axis.values[0]?.id ?? "";

const fixedFor = (
    manifest: SiteManifest,
    rows: string,
    cols: string,
    wanted: Record<string, string>,
): Record<string, string> =>
    Object.fromEntries(
        manifest.axes
            .filter((axis) => axis.id !== rows && axis.id !== cols)
            .map((axis) => {
                const candidate = wanted[axis.id];
                const valid =
                    candidate !== undefined &&
                    axis.values.some((value) => value.id === candidate);
                return [axis.id, valid ? candidate : firstValueId(axis)];
            }),
    );

export const defaultState = (manifest: SiteManifest): ViewState => {
    const [rows = "", cols = ""] = manifest.axes.map((axis) => axis.id);
    return {
        rows,
        cols,
        fixed: fixedFor(manifest, rows, cols, {}),
        open: null,
    };
};

/** `id` in axis order — the same rule the generator uses to name entries. */
export const entryIdFor = (
    values: Record<string, string>,
    manifest: SiteManifest,
): string => manifest.axes.map((axis) => values[axis.id] ?? "").join("__");

export const parseState = (
    search: string,
    manifest: SiteManifest,
): ViewState => {
    const params = new URLSearchParams(search);
    const defaults = defaultState(manifest);
    const rowsParam = params.get("rows") ?? "";
    const colsParam = params.get("cols") ?? "";
    const axesValid =
        axisById(manifest, rowsParam) !== undefined &&
        axisById(manifest, colsParam) !== undefined &&
        rowsParam !== colsParam;
    const rows = axesValid ? rowsParam : defaults.rows;
    const cols = axesValid ? colsParam : defaults.cols;
    const wanted = Object.fromEntries(
        manifest.axes.map((axis) => [axis.id, params.get(axis.id) ?? ""]),
    );
    const openParam = params.get("open");
    const open =
        openParam !== null &&
        manifest.entries.some((entry) => entry.id === openParam)
            ? openParam
            : null;
    return { rows, cols, fixed: fixedFor(manifest, rows, cols, wanted), open };
};

export const serializeState = (
    state: ViewState,
    manifest: SiteManifest,
): string => {
    const params = new URLSearchParams({ rows: state.rows, cols: state.cols });
    for (const axis of manifest.axes) {
        const value = state.fixed[axis.id];
        if (value !== undefined) params.set(axis.id, value);
    }
    if (state.open !== null) params.set("open", state.open);
    return `?${params.toString()}`;
};

export const buildMatrix = (
    manifest: SiteManifest,
    state: ViewState,
): Matrix => {
    const rowAxis = axisById(manifest, state.rows);
    const colAxis = axisById(manifest, state.cols);
    if (rowAxis === undefined || colAxis === undefined)
        throw new Error(
            `Unknown axis in view state: ${state.rows} × ${state.cols}`,
        );
    const byId = new Map(manifest.entries.map((entry) => [entry.id, entry]));
    return {
        rowAxis,
        colAxis,
        rows: rowAxis.values.map((rowValue) => ({
            rowValue,
            cells: colAxis.values.map((colValue) => ({
                colValue,
                entry:
                    byId.get(
                        entryIdFor(
                            {
                                ...state.fixed,
                                [rowAxis.id]: rowValue.id,
                                [colAxis.id]: colValue.id,
                            },
                            manifest,
                        ),
                    ) ?? null,
            })),
        })),
    };
};

export const neighbourId = (
    state: ViewState,
    manifest: SiteManifest,
    direction: Direction,
): string | null => {
    if (state.open === null) return null;
    const matrix = buildMatrix(manifest, state);
    const rowIndex = matrix.rows.findIndex((row) =>
        row.cells.some((cell) => cell.entry?.id === state.open),
    );
    if (rowIndex === -1) return null;
    const colIndex = matrix.rows[rowIndex]!.cells.findIndex(
        (cell) => cell.entry?.id === state.open,
    );
    const step = direction === "left" || direction === "up" ? -1 : 1;
    const horizontal = direction === "left" || direction === "right";
    for (
        let r = rowIndex + (horizontal ? 0 : step),
            c = colIndex + (horizontal ? step : 0);
        r >= 0 &&
        r < matrix.rows.length &&
        c >= 0 &&
        c < matrix.rows[0]!.cells.length;
        r += horizontal ? 0 : step, c += horizontal ? step : 0
    ) {
        const candidate = matrix.rows[r]!.cells[c]!.entry;
        if (candidate !== null) return candidate.id;
    }
    return null;
};
