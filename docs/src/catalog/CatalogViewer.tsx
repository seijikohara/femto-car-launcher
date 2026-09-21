import {
    useCallback,
    useEffect,
    useMemo,
    useRef,
    useState,
    type KeyboardEvent,
    type RefObject,
} from "react";
import { Button } from "@/components/ui/button";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogTitle,
} from "@/components/ui/dialog";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import type { Axis, SiteEntry, SiteManifest } from "./manifest";
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

function AxisSelect({
    id,
    label,
    value,
    axes,
    onChange,
}: {
    id: string;
    label: string;
    value: string;
    axes: Axis[];
    onChange: (axisId: string) => void;
}) {
    // Select.Value only resolves a value to its label through the `items`
    // map (or `itemToStringLabel`) — merely rendering SelectItem children
    // feeds the popup's own list and selection, not the trigger's label
    // lookup, so without this the trigger displays the raw axis id.
    const items = Object.fromEntries(axes.map((axis) => [axis.id, axis.label]));

    return (
        <div className="grid gap-1.5">
            <label htmlFor={id} className="text-sm font-medium">
                {label}
            </label>
            <Select
                value={value}
                onValueChange={(next) => next && onChange(next)}
                items={items}
            >
                <SelectTrigger id={id} className="w-44" aria-label={label}>
                    <SelectValue />
                </SelectTrigger>
                <SelectContent>
                    {axes.map((axis) => (
                        <SelectItem key={axis.id} value={axis.id}>
                            {axis.label}
                        </SelectItem>
                    ))}
                </SelectContent>
            </Select>
        </div>
    );
}

/** The Rows/Columns selects and the fixed-axis toggle groups. */
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
        <div className="mb-8 flex flex-wrap items-end gap-6">
            <AxisSelect
                id="catalog-rows"
                label="Rows"
                value={state.rows}
                axes={manifest.axes}
                onChange={(id) => setAxis("rows", id)}
            />
            <AxisSelect
                id="catalog-cols"
                label="Columns"
                value={state.cols}
                axes={manifest.axes}
                onChange={(id) => setAxis("cols", id)}
            />
            {fixedAxes.map((axis) => (
                <fieldset key={axis.id} className="grid gap-1.5">
                    <legend className="text-sm font-medium">
                        {axis.label}
                    </legend>
                    <ToggleGroup
                        variant="outline"
                        value={[state.fixed[axis.id] ?? axis.values[0].id]}
                        onValueChange={(next) => {
                            // A single-select ToggleGroup lets the user un-press
                            // the active item (`next` becomes `[]`) — ignore that
                            // so one value stays selected at all times.
                            const [chosen] = next;
                            if (chosen)
                                update(manifest, {
                                    ...state,
                                    fixed: {
                                        ...state.fixed,
                                        [axis.id]: chosen,
                                    },
                                    open: null,
                                });
                        }}
                        aria-label={axis.label}
                    >
                        {axis.values.map((value) => (
                            <ToggleGroupItem key={value.id} value={value.id}>
                                {value.label}
                            </ToggleGroupItem>
                        ))}
                    </ToggleGroup>
                </fieldset>
            ))}
        </div>
    );
}

interface LightboxProps extends StateProps {
    assetBase: string;
}

/**
 * A controlled shadcn Dialog: Base UI supplies the top layer, the portal,
 * the focus trap, and Escape/outside-press dismissal (reasons `escape-key`
 * / `outside-press` reported through `onOpenChange`) — this component only
 * adds arrow-key navigation and the caption-as-title accessible name.
 */
function Lightbox({
    manifest,
    state,
    update,
    assetBase,
    returnFocusTo,
}: LightboxProps & { returnFocusTo: RefObject<HTMLElement | null> }) {
    const entry =
        state.open === null
            ? null
            : (manifest.entries.find(
                  (candidate) => candidate.id === state.open,
              ) ?? null);

    const close = () => {
        if (state.open !== null) update(manifest, { ...state, open: null });
    };

    const step = (direction: Direction) => {
        const next = neighbourId(state, manifest, direction);
        if (next !== null) update(manifest, { ...state, open: next });
    };

    const onKeyDown = (event: KeyboardEvent) => {
        if (isFormControl(event.target)) return;
        const direction = KEY_DIRECTIONS[event.key];
        if (direction === undefined) return;
        event.preventDefault();
        step(direction);
    };

    return (
        <Dialog
            open={entry !== null}
            onOpenChange={(open) => {
                if (!open) close();
            }}
        >
            <DialogContent
                className="max-h-[94vh] w-auto max-w-[min(96vw,1400px)] overflow-hidden p-4 supports-[height:1dvh]:max-h-[94dvh] sm:p-6"
                onKeyDown={onKeyDown}
                finalFocus={returnFocusTo}
                showCloseButton={false}
            >
                {entry && (
                    <figure className="flex min-h-0 flex-col">
                        <img
                            src={assetBase + entry.full}
                            alt={caption(manifest, entry)}
                            width={entry.widthPx}
                            height={entry.heightPx}
                            className="block h-auto max-h-[calc(94vh-9rem)] w-auto max-w-full object-contain supports-[height:1dvh]:max-h-[calc(94dvh-9rem)]"
                        />
                        <figcaption className="text-muted-foreground mt-3 text-sm">
                            <DialogTitle className="inline text-sm font-normal">
                                {caption(manifest, entry)} · {entry.widthDp}×
                                {entry.heightDp} dp
                            </DialogTitle>
                            {" · "}
                            <a
                                href={assetBase + entry.full}
                                className="text-primary underline-offset-4 hover:underline"
                            >
                                open the file
                            </a>
                        </figcaption>
                        <DialogDescription className="sr-only">
                            Use the arrow keys to move to a neighbouring render.
                        </DialogDescription>
                    </figure>
                )}
                <div className="mt-4 flex flex-wrap gap-2">
                    {DIRECTION_ORDER.map((direction) => {
                        const target = neighbourId(state, manifest, direction);
                        return (
                            <Button
                                key={direction}
                                variant="outline"
                                size="icon"
                                aria-label={DIRECTIONS[direction].label}
                                // Not the native `disabled` attribute: a focused
                                // arrow button that disables itself on activation
                                // would drop focus out of the dialog, so the
                                // arrow/Escape keydown handling above then misses
                                // keys until the user tabs back in.
                                // focusableWhenDisabled keeps it in the tab order
                                // (Base UI reports it via aria-disabled instead of
                                // the disabled attribute); the aria-disabled:
                                // utilities below give it the same dimmed look
                                // buttonVariants' disabled: classes give a truly
                                // disabled button.
                                disabled={target === null}
                                focusableWhenDisabled
                                className="aria-disabled:pointer-events-none aria-disabled:cursor-not-allowed aria-disabled:opacity-50"
                                onClick={() => step(direction)}
                            >
                                <span aria-hidden="true">
                                    {DIRECTIONS[direction].glyph}
                                </span>
                            </Button>
                        );
                    })}
                    <Button variant="secondary" onClick={close}>
                        Close
                    </Button>
                </div>
            </DialogContent>
        </Dialog>
    );
}

export default function CatalogViewer({ manifestUrl, assetBase }: Props) {
    const [status, setStatus] = useState<Status>({ kind: "loading" });
    const [state, setState] = useState<ViewState | null>(null);
    // The cell button that opened the lightbox: Lightbox's finalFocus
    // returns focus here on close, instead of Base UI's own default (the
    // trigger) — there is no Dialog.Trigger here, the cells open it via
    // plain state, so this ref is what tells Base UI where "back" is.
    const lastCell = useRef<HTMLElement | null>(null);

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
            <output className="text-muted-foreground block">
                Loading the catalog…
            </output>
        );
    if (status.kind === "missing")
        return (
            <output className="text-muted-foreground block">
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
        <div>
            <p className="text-muted-foreground mb-6">
                Rendered from commit <code>{manifest.gitSha.slice(0, 7)}</code>{" "}
                on{" "}
                <time dateTime={manifest.generatedAt}>
                    {manifest.generatedAt.slice(0, 10)}
                </time>{" "}
                · {manifest.entries.length} renders
            </p>

            <Controls manifest={manifest} state={state} update={update} />

            <div className="relative overflow-x-auto">
                <table className="border-separate border-spacing-2">
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
                                <th
                                    key={value.id}
                                    scope="col"
                                    className="text-muted-foreground text-left text-sm font-semibold whitespace-nowrap"
                                >
                                    {value.label}
                                </th>
                            ))}
                        </tr>
                    </thead>
                    <tbody>
                        {matrix.rows.map((row) => (
                            <tr key={row.rowValue.id}>
                                <th
                                    scope="row"
                                    className="text-muted-foreground text-left text-sm font-semibold whitespace-nowrap"
                                >
                                    {row.rowValue.label}
                                </th>
                                {row.cells.map((cell) => {
                                    // A local binding keeps the null-check visible to the closures below.
                                    const entry = cell.entry;
                                    return (
                                        <td
                                            key={cell.colValue.id}
                                            className="align-top"
                                        >
                                            {entry === null ? (
                                                <span className="border-border text-muted-foreground grid aspect-[5/3] w-60 max-w-[60vw] place-items-center rounded-md border border-dashed">
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
                                                    className="border-border bg-card focus-visible:outline-ring block cursor-zoom-in rounded-md border focus-visible:outline-2"
                                                    aria-label={`Open ${caption(manifest, entry)}`}
                                                    onClick={(event) => {
                                                        lastCell.current =
                                                            event.currentTarget;
                                                        update(manifest, {
                                                            ...state,
                                                            open: entry.id,
                                                        });
                                                    }}
                                                >
                                                    <img
                                                        src={`${assetBase}${entry.thumb}`}
                                                        alt=""
                                                        width={entry.widthPx}
                                                        height={entry.heightPx}
                                                        loading="lazy"
                                                        decoding="async"
                                                        className="block w-60 max-w-[60vw] rounded-md"
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
                returnFocusTo={lastCell}
            />
        </div>
    );
}
