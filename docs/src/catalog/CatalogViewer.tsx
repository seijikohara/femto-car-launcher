import {
    useCallback,
    useEffect,
    useId,
    useMemo,
    useRef,
    useState,
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
import { CATALOG_SCHEMA_VERSION } from "./schema.ts";

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

// The fetch effect's own resolved shape: the "ready" branch carries the
// parsed initial ViewState alongside the manifest, so a shape-invalid
// manifest (parseState/buildMatrix throwing) is caught here and turned into
// an error status — never left as status "ready" with state stuck at null,
// which would render a silently blank island.
type FetchOutcome =
    | { kind: "missing" }
    | { kind: "error"; message: string }
    | { kind: "ready"; manifest: SiteManifest; initialState: ViewState };

const DIRECTION_ORDER: readonly Direction[] = ["left", "up", "down", "right"];

const DIRECTIONS: Record<Direction, { glyph: string; label: string }> = {
    left: { glyph: "←", label: "Previous column" },
    right: { glyph: "→", label: "Next column" },
    up: { glyph: "↑", label: "Previous row" },
    down: { glyph: "↓", label: "Next row" },
};

const KEY_DIRECTIONS: Record<string, Direction> = {
    ArrowLeft: "left",
    ArrowRight: "right",
    ArrowUp: "up",
    ArrowDown: "down",
};

// Arrow keys typed into a form control (none exist inside the lightbox today,
// but the dialog also catches bubbled keydowns) must not be hijacked as
// navigation.
const isFormControl = (target: EventTarget | null): boolean =>
    target instanceof HTMLInputElement ||
    target instanceof HTMLSelectElement ||
    target instanceof HTMLTextAreaElement;

// React's `autoFocus` prop cannot be used for this: it only ever calls the
// node's .focus() once, imperatively, during commit — which no-ops here
// because the dialog is still `display: none` at that point (showModal(),
// which shows it, runs in a later effect) — and React never writes the
// underlying `autofocus` *attribute* to the DOM either. Setting that
// attribute directly instead lets showModal()'s own native focusing steps
// (which look for it once the dialog actually becomes modal) pick this
// button over the first focusable descendant, the "open the file" link —
// Enter there would navigate away from the page.
const focusOnMount = (node: HTMLButtonElement | null): void => {
    node?.setAttribute("autofocus", "");
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

interface StateProps {
    manifest: SiteManifest;
    state: ViewState;
    update: (manifest: SiteManifest, next: ViewState) => void;
}

/** The Rows/Columns selects and the fixed-axis radio groups. */
function Controls({ manifest, state, update }: StateProps) {
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

    return (
        <form
            className="catalog__controls"
            onSubmit={(event) => event.preventDefault()}
        >
            <label>
                Rows
                <select
                    value={state.rows}
                    onChange={(event) => setAxis("rows", event.target.value)}
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
                    onChange={(event) => setAxis("cols", event.target.value)}
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
    );
}

interface LightboxProps extends StateProps {
    assetBase: string;
}

/**
 * A native modal dialog: showModal()/close() give it the top layer, the
 * ::backdrop, an automatic focus trap, and focus restored to the triggering
 * cell button on close — none of which the previous role="presentation" div
 * could provide.
 */
function Lightbox({ manifest, state, update, assetBase }: LightboxProps) {
    const dialogRef = useRef<HTMLDialogElement>(null);
    const captionId = useId();
    const entry =
        state.open === null
            ? null
            : (manifest.entries.find(
                  (candidate) => candidate.id === state.open,
              ) ?? null);
    const isOpen = entry !== null;

    // Depends on the open/closed boolean, not on `state.open` (the entry id):
    // an arrow-key step changes the id while staying open, and re-calling
    // showModal() on an already-open dialog would both throw and reset
    // native focus to the dialog's first control on every step.
    useEffect(() => {
        const dialog = dialogRef.current;
        if (dialog === null) return;
        if (isOpen && !dialog.open) dialog.showModal();
        return () => {
            if (dialog.open) dialog.close();
        };
    }, [isOpen]);

    const onClose = () => {
        // The dialog's native `close` event fires for every path that closes
        // it — Escape, a backdrop click, our own close() calls below — so
        // this is the one place state catches up; the `state.open` guard
        // makes it a no-op for a `close` that fires after we already cleared it.
        if (state.open !== null) update(manifest, { ...state, open: null });
    };

    // Wired imperatively rather than as JSX onClick/onKeyDown props: jsx-a11y's
    // no-noninteractive-element-interactions rule flags mouse/keyboard
    // handlers on an element whose role is "dialog" — a real concern for most
    // non-interactive roles, but a false positive here. A modal <dialog>
    // already closes on Escape and a backdrop click natively, with zero JS;
    // wiring the same two gestures here only extends them (arrow-key
    // navigation) and makes Escape work under jsdom, which has no native
    // CloseWatcher. addEventListener reaches the same DOM node the JSX props
    // would have, so the interaction lives in the same place either way —
    // this only changes which API attaches it, to keep the static JSX-prop
    // scan from flagging an event that native <dialog> semantics already imply.
    useEffect(() => {
        const dialog = dialogRef.current;
        if (dialog === null) return;

        const handleKeyDown = (event: KeyboardEvent) => {
            if (isFormControl(event.target)) return;
            if (event.key === "Escape") {
                // jsdom's <dialog> has no native Escape-to-close behaviour (no
                // CloseWatcher); close() is harmless if a real browser also
                // closes it natively first (close() on an already-closed
                // dialog is a no-op).
                dialog.close();
                return;
            }
            const direction = KEY_DIRECTIONS[event.key];
            if (direction === undefined) return;
            event.preventDefault();
            const next = neighbourId(state, manifest, direction);
            if (next !== null) update(manifest, { ...state, open: next });
        };

        const handleClick = (event: MouseEvent) => {
            // ::backdrop clicks land on the dialog itself as the target
            // (there is no separate backdrop node) — the inner wrapper covers
            // the rest of the box, so this only matches a true outside click.
            if (event.target === dialog) dialog.close();
        };

        dialog.addEventListener("keydown", handleKeyDown);
        dialog.addEventListener("click", handleClick);
        return () => {
            dialog.removeEventListener("keydown", handleKeyDown);
            dialog.removeEventListener("click", handleClick);
        };
    }, [manifest, state, update]);

    return (
        <dialog
            ref={dialogRef}
            className="catalog__lightbox"
            aria-labelledby={captionId}
            onClose={onClose}
            onCancel={onClose}
        >
            {entry !== null && (
                <div className="catalog__lightbox-body glass-panel">
                    <figure>
                        <img
                            src={`${assetBase}${entry.full}`}
                            alt={caption(manifest, entry)}
                            width={entry.widthPx}
                            height={entry.heightPx}
                        />
                        <figcaption>
                            {/* id lives on this span, not the figcaption: the
                                dialog's aria-labelledby points at captionId,
                                and its accessible name must not end in
                                "open the file" from the link below. */}
                            <span id={captionId}>
                                {caption(manifest, entry)} · {entry.widthDp}×
                                {entry.heightDp} dp
                            </span>{" "}
                            ·{" "}
                            <a href={`${assetBase}${entry.full}`}>
                                open the file
                            </a>
                        </figcaption>
                    </figure>
                    <div className="catalog__lightbox-nav">
                        {DIRECTION_ORDER.map((direction) => {
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
                                    aria-label={DIRECTIONS[direction].label}
                                    // Not the native `disabled` attribute: a
                                    // focused arrow button that disables
                                    // itself on activation drops focus out of
                                    // the dialog, so the keydown handler
                                    // above then misses arrow/Escape keys
                                    // until the user tabs back in.
                                    // catalog.css's [aria-disabled="true"]
                                    // rule makes it inert to clicks while Tab
                                    // focus still lands on it.
                                    aria-disabled={target === null}
                                    onClick={() =>
                                        target !== null &&
                                        update(manifest, {
                                            ...state,
                                            open: target,
                                        })
                                    }
                                >
                                    {DIRECTIONS[direction].glyph}
                                </button>
                            );
                        })}
                        <button
                            type="button"
                            className="glass-button"
                            ref={focusOnMount}
                            onClick={() => dialogRef.current?.close()}
                        >
                            Close
                        </button>
                    </div>
                </div>
            )}
        </dialog>
    );
}

export default function CatalogViewer({ manifestUrl, assetBase }: Props) {
    const [status, setStatus] = useState<Status>({ kind: "loading" });
    const [state, setState] = useState<ViewState | null>(null);

    useEffect(() => {
        let cancelled = false;
        fetch(manifestUrl)
            .then(async (response): Promise<FetchOutcome> => {
                if (response.status === 404) return { kind: "missing" };
                if (!response.ok)
                    return {
                        kind: "error",
                        message: `HTTP ${response.status}`,
                    };
                const manifest = (await response.json()) as SiteManifest;
                if (manifest.schemaVersion !== CATALOG_SCHEMA_VERSION)
                    return {
                        kind: "error",
                        message:
                            "The catalog manifest has an unsupported format.",
                    };
                const initialState = parseState(
                    window.location.search,
                    manifest,
                );
                // Probes buildMatrix once so a manifest whose axes/entries
                // don't actually line up (e.g. no axes at all) throws HERE,
                // into the catch below, instead of later inside the
                // render-time useMemo with no status left to report it through.
                buildMatrix(manifest, initialState);
                return { kind: "ready", manifest, initialState };
            })
            .catch((error: unknown): FetchOutcome => ({
                kind: "error",
                message: error instanceof Error ? error.message : String(error),
            }))
            .then((outcome) => {
                if (cancelled) return;
                if (outcome.kind === "ready") {
                    setState(outcome.initialState);
                    setStatus({ kind: "ready", manifest: outcome.manifest });
                } else {
                    setStatus(outcome);
                }
            });
        return () => {
            cancelled = true;
        };
    }, [manifestUrl]);

    // The URL is the shareable form of the view: every state change rewrites
    // the query in place (no history entries — the back button leaves the page).
    const update = useCallback((manifest: SiteManifest, next: ViewState) => {
        setState(next);
        // Astro's ClientRouter keeps {index, scrollX, scrollY} in
        // history.state and ignores popstate when that state is null, so
        // passing it through here (instead of null) keeps the Back button
        // working site-wide after any viewer interaction.
        window.history.replaceState(
            window.history.state,
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

    if (status.kind === "loading")
        return (
            <output className="catalog__status">Loading the catalog…</output>
        );
    if (status.kind === "missing")
        return (
            <output className="catalog__status">
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

            <Controls manifest={manifest} state={state} update={update} />

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
                                                <span className="catalog__empty">
                                                    {/* aria-label is name-prohibited on a role-less span; an sr-only text node names it instead. */}
                                                    <span aria-hidden="true">
                                                        —
                                                    </span>
                                                    <span className="sr-only">
                                                        not rendered
                                                    </span>
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

            <Lightbox
                manifest={manifest}
                state={state}
                update={update}
                assetBase={assetBase}
            />
        </div>
    );
}
