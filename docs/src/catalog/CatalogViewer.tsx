import {
    useCallback,
    useEffect,
    useMemo,
    useRef,
    useState,
    type KeyboardEvent,
} from "react";
import "./catalog.css";
import type { SiteEntry, SiteManifest } from "./manifest";
import {
    buildMatrix,
    neighbourId,
    parseState,
    serializeState,
    type Direction,
    type ViewState,
} from "./matrix";

interface Props {
    /** Base-prefixed URL of public/catalog/manifest.json. */
    manifestUrl: string;
    /** Base-prefixed URL prefix the manifest's `full`/`thumb` paths hang off. */
    assetBase: string;
}

type Status =
    | { kind: "loading" }
    | { kind: "missing" }
    | { kind: "error"; message: string }
    | { kind: "ready"; manifest: SiteManifest };

const KEY_DIRECTIONS: Record<string, Direction> = {
    ArrowLeft: "left",
    ArrowRight: "right",
    ArrowUp: "up",
    ArrowDown: "down",
};

const labelOf = (
    manifest: SiteManifest,
    axisId: string,
    valueId: string,
): string =>
    manifest.axes
        .find((axis) => axis.id === axisId)
        ?.values.find((value) => value.id === valueId)?.label ?? valueId;

const caption = (manifest: SiteManifest, entry: SiteEntry): string =>
    manifest.axes
        .map((axis) => labelOf(manifest, axis.id, entry.values[axis.id] ?? ""))
        .join(" · ");

export default function CatalogViewer({ manifestUrl, assetBase }: Props) {
    const [status, setStatus] = useState<Status>({ kind: "loading" });
    const [state, setState] = useState<ViewState | null>(null);
    const closeButton = useRef<HTMLButtonElement>(null);

    useEffect(() => {
        let cancelled = false;
        fetch(manifestUrl)
            .then(async (response) => {
                if (response.status === 404)
                    return { kind: "missing" } as const;
                if (!response.ok)
                    return {
                        kind: "error",
                        message: `HTTP ${response.status}`,
                    } as const;
                const manifest = (await response.json()) as SiteManifest;
                return { kind: "ready", manifest } as const;
            })
            .catch(
                (error: unknown) =>
                    ({
                        kind: "error",
                        message:
                            error instanceof Error
                                ? error.message
                                : String(error),
                    }) as const,
            )
            .then((next) => {
                if (cancelled) return;
                setStatus(next);
                if (next.kind === "ready")
                    setState(parseState(window.location.search, next.manifest));
            });
        return () => {
            cancelled = true;
        };
    }, [manifestUrl]);

    // The URL is the shareable form of the view: every state change rewrites
    // the query in place (no history entries — the back button leaves the page).
    const update = useCallback((manifest: SiteManifest, next: ViewState) => {
        setState(next);
        window.history.replaceState(
            null,
            "",
            `${window.location.pathname}${serializeState(next, manifest)}`,
        );
    }, []);

    const matrix = useMemo(
        () =>
            status.kind === "ready" && state !== null
                ? buildMatrix(status.manifest, state)
                : null,
        [status, state],
    );

    useEffect(() => {
        if (state?.open) closeButton.current?.focus();
    }, [state?.open]);

    if (status.kind === "loading") return <output>Loading the catalog…</output>;
    if (status.kind === "missing")
        return (
            <output>
                This build carries no catalog. The published site renders it on
                every push to main.
            </output>
        );
    if (status.kind === "error")
        return (
            <p role="alert">
                The catalog could not be loaded ({status.message}).
            </p>
        );
    if (state === null || matrix === null) return null;

    const { manifest } = status;
    const openEntry =
        state.open === null
            ? null
            : (manifest.entries.find((entry) => entry.id === state.open) ??
              null);
    const fixedAxes = manifest.axes.filter(
        (axis) => axis.id !== state.rows && axis.id !== state.cols,
    );

    const setAxis = (which: "rows" | "cols", axisId: string) => {
        const other = which === "rows" ? state.cols : state.rows;
        // Picking the other axis swaps them instead of collapsing the matrix.
        const next =
            axisId === other
                ? { rows: state.cols, cols: state.rows }
                : { rows: state.rows, cols: state.cols, [which]: axisId };
        update(
            manifest,
            parseState(
                serializeState({ ...state, ...next, open: null }, manifest),
                manifest,
            ),
        );
    };

    const onLightboxKey = (event: KeyboardEvent<HTMLDivElement>) => {
        if (event.key === "Escape") {
            update(manifest, { ...state, open: null });
            return;
        }
        const direction = KEY_DIRECTIONS[event.key];
        if (direction === undefined) return;
        event.preventDefault();
        const next = neighbourId(state, manifest, direction);
        if (next !== null) update(manifest, { ...state, open: next });
    };

    return (
        <div className="catalog">
            <p className="catalog__meta">
                Rendered from commit <code>{manifest.gitSha.slice(0, 7)}</code>{" "}
                on{" "}
                <time dateTime={manifest.generatedAt}>
                    {manifest.generatedAt.slice(0, 10)}
                </time>{" "}
                · {manifest.entries.length} renders
            </p>

            <form
                className="catalog__controls"
                onSubmit={(event) => event.preventDefault()}
            >
                <label>
                    Rows
                    <select
                        value={state.rows}
                        onChange={(event) =>
                            setAxis("rows", event.target.value)
                        }
                    >
                        {manifest.axes.map((axis) => (
                            <option key={axis.id} value={axis.id}>
                                {axis.label}
                            </option>
                        ))}
                    </select>
                </label>
                <label>
                    Columns
                    <select
                        value={state.cols}
                        onChange={(event) =>
                            setAxis("cols", event.target.value)
                        }
                    >
                        {manifest.axes.map((axis) => (
                            <option key={axis.id} value={axis.id}>
                                {axis.label}
                            </option>
                        ))}
                    </select>
                </label>
                {fixedAxes.map((axis) => (
                    <fieldset key={axis.id} className="catalog__filter">
                        <legend>{axis.label}</legend>
                        {axis.values.map((value) => (
                            <label key={value.id}>
                                <input
                                    type="radio"
                                    name={axis.id}
                                    value={value.id}
                                    checked={state.fixed[axis.id] === value.id}
                                    onChange={() =>
                                        update(manifest, {
                                            ...state,
                                            fixed: {
                                                ...state.fixed,
                                                [axis.id]: value.id,
                                            },
                                            open: null,
                                        })
                                    }
                                />
                                {value.label}
                            </label>
                        ))}
                    </fieldset>
                ))}
            </form>

            <div className="catalog__scroll">
                <table className="catalog__matrix">
                    <caption className="sr-only">
                        {matrix.rowAxis.label} by {matrix.colAxis.label}
                    </caption>
                    <thead>
                        <tr>
                            {/* The corner cell has no data value; its sr-only text names what the row headers below it mean. */}
                            <td>
                                <span className="sr-only">
                                    {matrix.rowAxis.label}
                                </span>
                            </td>
                            {matrix.colAxis.values.map((value) => (
                                <th key={value.id} scope="col">
                                    {value.label}
                                </th>
                            ))}
                        </tr>
                    </thead>
                    <tbody>
                        {matrix.rows.map((row) => (
                            <tr key={row.rowValue.id}>
                                <th scope="row">{row.rowValue.label}</th>
                                {row.cells.map((cell) => {
                                    // A local binding keeps the null-check visible to the closures below.
                                    const entry = cell.entry;
                                    return (
                                        <td key={cell.colValue.id}>
                                            {entry === null ? (
                                                <span
                                                    className="catalog__empty"
                                                    aria-label="not rendered"
                                                >
                                                    —
                                                </span>
                                            ) : (
                                                <button
                                                    type="button"
                                                    className="catalog__cell"
                                                    aria-label={`Open ${caption(manifest, entry)}`}
                                                    onClick={() =>
                                                        update(manifest, {
                                                            ...state,
                                                            open: entry.id,
                                                        })
                                                    }
                                                >
                                                    <img
                                                        src={`${assetBase}${entry.thumb}`}
                                                        alt=""
                                                        width={entry.widthPx}
                                                        height={entry.heightPx}
                                                        loading="lazy"
                                                        decoding="async"
                                                    />
                                                </button>
                                            )}
                                        </td>
                                    );
                                })}
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            {openEntry !== null && (
                // The overlay is deliberately non-semantic (role="presentation"):
                // it only exists to dim the page and catch a click outside the
                // dialog, so the click/keydown handlers live here rather than on
                // the <dialog> below — jsx-a11y's no-noninteractive-element-
                // interactions rule flags interaction handlers on an element
                // whose role carries real (non-interactive) semantics.
                <div
                    className="catalog__lightbox"
                    role="presentation"
                    onKeyDown={onLightboxKey}
                    onClick={(event) => {
                        if (event.target === event.currentTarget)
                            update(manifest, { ...state, open: null });
                    }}
                >
                    <dialog
                        open
                        aria-modal="true"
                        aria-label={caption(manifest, openEntry)}
                        className="catalog__lightbox-body glass-panel"
                    >
                        <figure>
                            <img
                                src={`${assetBase}${openEntry.full}`}
                                alt={caption(manifest, openEntry)}
                                width={openEntry.widthPx}
                                height={openEntry.heightPx}
                            />
                            <figcaption>
                                {caption(manifest, openEntry)} ·{" "}
                                {openEntry.widthDp}×{openEntry.heightDp} dp ·{" "}
                                <a href={`${assetBase}${openEntry.full}`}>
                                    open the file
                                </a>
                            </figcaption>
                        </figure>
                        <div className="catalog__lightbox-nav">
                            {(["left", "up", "down", "right"] as const).map(
                                (direction) => {
                                    const target = neighbourId(
                                        state,
                                        manifest,
                                        direction,
                                    );
                                    return (
                                        <button
                                            key={direction}
                                            type="button"
                                            className="glass-button"
                                            disabled={target === null}
                                            onClick={() =>
                                                target !== null &&
                                                update(manifest, {
                                                    ...state,
                                                    open: target,
                                                })
                                            }
                                        >
                                            {direction === "left"
                                                ? "←"
                                                : direction === "right"
                                                  ? "→"
                                                  : direction === "up"
                                                    ? "↑"
                                                    : "↓"}
                                        </button>
                                    );
                                },
                            )}
                            <button
                                ref={closeButton}
                                type="button"
                                className="glass-button"
                                onClick={() =>
                                    update(manifest, { ...state, open: null })
                                }
                            >
                                Close
                            </button>
                        </div>
                    </dialog>
                </div>
            )}
        </div>
    );
}
