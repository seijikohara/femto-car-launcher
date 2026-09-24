import {
    useCallback,
    useEffect,
    useMemo,
    useRef,
    useState,
    // Aliased: the bare name would shadow the DOM's own global
    // `KeyboardEvent` type, which isFormControl's neighbours don't use today
    // but a future addition easily could by mistake.
    type KeyboardEvent as ReactKeyboardEvent,
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

export interface Channel {
    /** Stable identifier; also the `?channel=` URL value for a non-default channel. */
    id: string;
    /** Toggle-item label, e.g. "Stable" / "Nightly". */
    label: string;
    /** Base-prefixed URL of that channel's manifest.json. */
    manifestUrl: string;
    /** Base-prefixed URL prefix the manifest's `full`/`thumb` paths hang off. */
    assetBase: string;
}

interface Props {
    /** Non-empty; `channels[0]` is the default — selected when the URL names
     * no channel, an unrecognised one, or one whose probe (see the mount
     * effect below) fails. */
    channels: readonly Channel[];
}

type Status =
    | { kind: "loading" }
    | { kind: "missing" }
    | { kind: "error"; message: string }
    // channelId names which channel `manifest` was actually fetched from —
    // not necessarily the current selection. A switch only moves `state`
    // (below) to this shape once its own fetch resolves, so the label and
    // asset paths stay paired with whatever is genuinely on screen instead
    // of flashing the new channel's name over the previous one's data.
    | { kind: "ready"; manifest: SiteManifest; channelId: string };

// The fetch effect's own resolved shape: the "ready" branch carries the
// parsed initial ViewState alongside the manifest, so a shape-invalid
// manifest (parseState/buildMatrix throwing) is caught here and turned into
// an error status — never left as status "ready" with state stuck at null,
// which would render a silently blank island.
type FetchOutcome =
    | { kind: "missing" }
    | { kind: "error"; message: string }
    | {
          kind: "ready";
          manifest: SiteManifest;
          initialState: ViewState;
          channelId: string;
      };

// Which channel is showing: "pending" while a `?channel=` deep link to a
// non-default channel waits on that channel's own probe (see the mount
// effect) to know whether to honour it or fall back to the default. The
// default channel is never "pending" — it needs no probe.
type Resolution =
    { kind: "resolved"; id: string } | { kind: "pending"; requestedId: string };

const probe = async (manifestUrl: string): Promise<boolean> => {
    try {
        const response = await fetch(manifestUrl, { method: "HEAD" });
        return response.ok;
    } catch {
        return false;
    }
};

const resolveInitialChannel = (
    channels: readonly Channel[],
    search: string,
): Resolution => {
    const defaultId = channels[0].id;
    const requestedId = new URLSearchParams(search).get("channel");
    if (requestedId === null || requestedId === defaultId)
        return { kind: "resolved", id: defaultId };
    // An id that names no configured channel is never a valid deep link
    // (nothing to probe), so it resolves to the default immediately instead
    // of waiting on the mount effect.
    return channels.some((channel) => channel.id === requestedId)
        ? { kind: "pending", requestedId }
        : { kind: "resolved", id: defaultId };
};

// Whether the initial `?channel=` param (if any) should be dropped from the
// URL: true for a redundant `?channel=<default>` or an id naming no
// configured channel — resolveInitialChannel above resolves both of those to
// the default synchronously, so only the address bar is left stale. A valid
// non-default id is left alone here; if its probe later fails, the mount
// effect below cleans that one up itself, through the same withChannelParam.
const needsInitialCleanup = (
    channels: readonly Channel[],
    search: string,
): boolean => {
    const requestedId = new URLSearchParams(search).get("channel");
    if (requestedId === null) return false;
    return (
        requestedId === channels[0].id ||
        !channels.some((channel) => channel.id === requestedId)
    );
};

// Sets or clears only the `channel` key of a query string, leaving the
// matrix params (rows/cols/axis values/open) exactly as they are — the fetch
// effect re-derives those against whichever manifest ends up loaded via
// parseState, so this never needs the manifest itself.
const withChannelParam = (
    search: string,
    channelId: string,
    defaultId: string,
): string => {
    const params = new URLSearchParams(search);
    if (channelId === defaultId) params.delete("channel");
    else params.set("channel", channelId);
    const query = params.toString();
    return query ? `?${query}` : "";
};

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
    const items = useMemo(
        () => Object.fromEntries(axes.map((axis) => [axis.id, axis.label])),
        [axes],
    );

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

/**
 * The Rows/Columns selects and the fixed-axis toggle groups. Returns bare
 * fieldsets (no wrapping row) so the caller can lay them out in the same
 * flex toolbar as the build-channel switch.
 */
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
        <>
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
        </>
    );
}

/** The build-channel switch (Stable/Nightly/…); rendered only when more than
 * one channel actually probed as available (see the mount effect below). */
function BuildSwitch({
    channels,
    value,
    onChange,
}: {
    channels: readonly Channel[];
    value: string;
    onChange: (channelId: string) => void;
}) {
    return (
        <fieldset className="grid gap-1.5">
            <legend className="text-sm font-medium">Build</legend>
            <ToggleGroup
                variant="outline"
                value={[value]}
                onValueChange={(next) => {
                    // Same single-select guard as the fixed-axis groups above:
                    // ignore the un-press case so a build is always selected.
                    const [chosen] = next;
                    if (chosen) onChange(chosen);
                }}
                aria-label="Build"
            >
                {channels.map((channel) => (
                    <ToggleGroupItem key={channel.id} value={channel.id}>
                        {channel.label}
                    </ToggleGroupItem>
                ))}
            </ToggleGroup>
        </fieldset>
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

    // Base UI keeps the popup mounted for its close animation, but `entry`
    // already went null the same instant `state.open` did — without this,
    // the figure unmounts mid-fade and the dialog visibly collapses around
    // the vanished image instead of just fading out. `shownEntry` retains
    // the last non-null entry across that window, following React's
    // documented "adjust state during render" pattern (react.dev, storing
    // information from previous renders) rather than a ref: a ref may not
    // be read or written during render (React's own rule — a render can be
    // discarded or replayed), and this needs to be read here, in render, to
    // produce this render's output.
    const [shownEntry, setShownEntry] = useState(entry);
    if (entry !== null && entry !== shownEntry) setShownEntry(entry);

    // Base UI's default initialFocus is the first tabbable descendant — the
    // "open the file" link below, where Enter would navigate away from the
    // page — so Close is pointed to explicitly, the same outcome the
    // previous native <dialog>'s autofocus-attribute hack produced. Typed to
    // the concrete element (not HTMLElement, which DialogContent's own
    // `initialFocus` accepts): the shadcn Button's inferred ref type is
    // Ref<HTMLButtonElement>, and a HTMLButtonElement ref widens cleanly to
    // initialFocus's HTMLElement one, but not the other way around.
    const closeRef = useRef<HTMLButtonElement | null>(null);

    const close = () => {
        if (state.open !== null) update(manifest, { ...state, open: null });
    };

    const step = (direction: Direction) => {
        const next = neighbourId(state, manifest, direction);
        if (next !== null) update(manifest, { ...state, open: next });
    };

    const onKeyDown = (event: ReactKeyboardEvent) => {
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
                // Two independent width bugs in the generated defaults, both
                // needing an override:
                // 1. sm:max-w-sm (24rem) from 640px up: cn()'s conflict
                //    resolution only drops a class within the same variant
                //    scope, so the bare max-w-[...] below overrides the base
                //    max-w-[calc(100%-2rem)] but leaves sm:max-w-sm in the
                //    cascade, where it wins over an unprefixed rule at
                //    ≥640px — repeating the same value under sm: is what
                //    actually replaces it.
                // 2. left-1/2 + -translate-x-1/2 (the generated centring
                //    technique) plus width:auto: per CSS2.1 10.3.7, shrink-
                //    to-fit width for a `position: fixed` box computes
                //    against only the space to ONE side when just `left` is
                //    constrained — here, half the viewport — so a wide
                //    render was clamped to ~half its natural width even
                //    after fix 1. inset-x-0 + mx-auto anchors both edges
                //    (giving the correct centred layout via auto margins
                //    instead of the transform trick) and w-fit (fit-content,
                //    not auto) sizes correctly within that; translate-x-0
                //    cancels only the now-unwanted horizontal half of the
                //    base transform, leaving -translate-y-1/2 to keep doing
                //    its job. Verified empirically at 1366px: the 853×512
                //    render now measures 853×512 (was ~326×196 in the sm:
                //    max-w-sm regression); a height-capped portrait render
                //    is unaffected either way, since it never reached the
                //    width cap.
                className="inset-x-0 mx-auto max-h-[94vh] w-fit max-w-[min(96vw,1400px)] translate-x-0 overflow-hidden p-4 supports-[height:1dvh]:max-h-[94dvh] sm:max-w-[min(96vw,1400px)] sm:p-6"
                onKeyDown={onKeyDown}
                initialFocus={closeRef}
                finalFocus={returnFocusTo}
                showCloseButton={false}
            >
                {shownEntry && (
                    <figure className="flex min-h-0 flex-col">
                        <img
                            src={assetBase + shownEntry.full}
                            alt={caption(manifest, shownEntry)}
                            width={shownEntry.widthPx}
                            height={shownEntry.heightPx}
                            className="block h-auto max-h-[calc(94vh-9rem)] w-auto max-w-full object-contain supports-[height:1dvh]:max-h-[calc(94dvh-9rem)]"
                        />
                        <figcaption className="mt-3 text-sm text-muted-foreground">
                            <DialogTitle className="inline text-sm font-normal">
                                {caption(manifest, shownEntry)} ·{" "}
                                {shownEntry.widthDp}×{shownEntry.heightDp} dp
                            </DialogTitle>
                            {" · "}
                            <a
                                href={assetBase + shownEntry.full}
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
                                // utility below gives it the same dimmed look
                                // buttonVariants' disabled: class gives a truly
                                // disabled button (pointer-events:none already
                                // covers the cursor, on top of Base UI's own
                                // click-suppression once disabled is true).
                                disabled={target === null}
                                focusableWhenDisabled
                                className="aria-disabled:pointer-events-none aria-disabled:opacity-50"
                                onClick={() => step(direction)}
                            >
                                <span aria-hidden="true">
                                    {DIRECTIONS[direction].glyph}
                                </span>
                            </Button>
                        );
                    })}
                    <Button variant="secondary" onClick={close} ref={closeRef}>
                        Close
                    </Button>
                </div>
            </DialogContent>
        </Dialog>
    );
}

export default function CatalogViewer({ channels }: Props) {
    const defaultChannel = channels[0];
    const [status, setStatus] = useState<Status>({ kind: "loading" });
    const [state, setState] = useState<ViewState | null>(null);
    // Astro server-renders this island's initial HTML (client:load still
    // starts from a server pass) before any client hydrates it, and `status`
    // stays "loading" through that whole pass regardless of `resolution` — so
    // reading the real URL only on the client, once `window` exists, changes
    // nothing about that shared first paint and avoids a ReferenceError
    // server-side (same guard vitest.setup.ts uses for the jsdom/node split).
    const [resolution, setResolution] = useState<Resolution>(() =>
        typeof window === "undefined"
            ? { kind: "resolved", id: defaultChannel.id }
            : resolveInitialChannel(channels, window.location.search),
    );
    // Seeded with just the default: the switch stays hidden (it renders only
    // above one available channel) until the probe below proves a non-default
    // channel is actually reachable in this deploy.
    const [availableIds, setAvailableIds] = useState<ReadonlySet<string>>(
        () => new Set([defaultChannel.id]),
    );
    // The cell button that opened the lightbox: Lightbox's finalFocus
    // returns focus here on close, instead of Base UI's own default (the
    // trigger) — there is no Dialog.Trigger here, the cells open it via
    // plain state, so this ref is what tells Base UI where "back" is.
    const lastCell = useRef<HTMLElement | null>(null);

    const channelId = resolution.kind === "resolved" ? resolution.id : null;
    const loadedChannelId = status.kind === "ready" ? status.channelId : null;

    // Cleans a redundant `?channel=<default>` or an unrecognised `?channel=<id>`
    // off the URL on mount: resolveInitialChannel above already resolves both
    // to the default synchronously (this never changes what renders), so this
    // only keeps the address bar — and anything copied from it — from naming
    // a channel that was never really in play.
    useEffect(() => {
        if (!needsInitialCleanup(channels, window.location.search)) return;
        window.history.replaceState(
            window.history.state,
            "",
            `${window.location.pathname}${withChannelParam(window.location.search, defaultChannel.id, defaultChannel.id)}`,
        );
    }, [channels, defaultChannel.id]);

    // Probes every non-default channel once on mount: a HEAD 200 makes it
    // selectable, anything else (a failed nightly render, or a PR build that
    // imported no catalog at all) hides it. The default channel is never
    // probed — it is always offered, and its own fetch below reports its
    // "missing"/"error" status the normal way. A pending `?channel=` deep
    // link resolves here too: to the requested channel if it probed
    // reachable, else silently back to the default (URL included, so a
    // stale deep link does not keep pointing at content that no longer shows).
    useEffect(() => {
        let cancelled = false;
        Promise.all(
            channels
                .slice(1)
                .map(
                    async (channel) =>
                        [channel.id, await probe(channel.manifestUrl)] as const,
                ),
        ).then((results) => {
            if (cancelled) return;
            const available = new Set<string>([defaultChannel.id]);
            for (const [id, ok] of results) if (ok) available.add(id);
            setAvailableIds(available);
            setResolution((previous) => {
                if (previous.kind !== "pending") return previous;
                if (available.has(previous.requestedId))
                    return { kind: "resolved", id: previous.requestedId };
                window.history.replaceState(
                    window.history.state,
                    "",
                    `${window.location.pathname}${withChannelParam(window.location.search, defaultChannel.id, defaultChannel.id)}`,
                );
                return { kind: "resolved", id: defaultChannel.id };
            });
        });
        return () => {
            cancelled = true;
        };
    }, [channels, defaultChannel.id]);

    // Fetches the resolved channel's manifest: on first resolution, and again
    // on every explicit switch (channelId changing is what drives a refetch).
    useEffect(() => {
        if (channelId === null) return; // still waiting on the probe above
        const channel = channels.find(
            (candidate) => candidate.id === channelId,
        );
        if (channel === undefined) return; // defensive; channelId always names a real channel
        let cancelled = false;
        fetch(channel.manifestUrl)
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
                return {
                    kind: "ready",
                    manifest,
                    initialState,
                    channelId: channel.id,
                };
            })
            .catch((error: unknown): FetchOutcome => ({
                kind: "error",
                message: error instanceof Error ? error.message : String(error),
            }))
            .then((outcome) => {
                if (cancelled) return;
                if (outcome.kind === "ready") {
                    setState(outcome.initialState);
                    setStatus({
                        kind: "ready",
                        manifest: outcome.manifest,
                        channelId: outcome.channelId,
                    });
                } else {
                    setStatus(outcome);
                }
            });
        return () => {
            cancelled = true;
        };
    }, [channelId, channels]);

    // The URL is the shareable form of the view: every state change rewrites
    // the query in place (no history entries — the back button leaves the page).
    const update = useCallback(
        (manifest: SiteManifest, next: ViewState) => {
            setState(next);
            // The matrix params are serialised against `manifest` (always
            // status.manifest, the one on screen), but the channel param
            // carries the selection (channelId), not the loaded channel: a
            // control touched after a switch but before its fetch lands
            // would otherwise drop the pending channel from the URL, and the
            // page would then show that channel's content under a URL that
            // names the previous one. When the selected manifest arrives,
            // the fetch effect re-validates these params against it through
            // parseState. channelId is never null here — the matrix only
            // renders once a resolved channel has loaded.
            const query = withChannelParam(
                serializeState(next, manifest),
                channelId ?? defaultChannel.id,
                defaultChannel.id,
            );
            // Astro's ClientRouter keeps {index, scrollX, scrollY} in
            // history.state and ignores popstate when that state is null, so
            // passing it through here (instead of null) keeps the Back button
            // working site-wide after any viewer interaction.
            window.history.replaceState(
                window.history.state,
                "",
                `${window.location.pathname}${query}`,
            );
        },
        [channelId, defaultChannel.id],
    );

    // Switches the displayed channel: the URL's non-channel params (rows,
    // cols, axis values, open) are left exactly as they are, so the fetch
    // effect's parseState(window.location.search, newManifest) call — the
    // same one every load already runs — re-validates the visitor's current
    // matrix selection against the new manifest per field, falling back only
    // where it no longer applies. Only offered for a channel already known
    // available (BuildSwitch renders one item per `availableIds`), so this
    // never itself needs to probe.
    const switchChannel = (nextId: string) => {
        if (nextId === channelId) return;
        setResolution({ kind: "resolved", id: nextId });
        window.history.replaceState(
            window.history.state,
            "",
            `${window.location.pathname}${withChannelParam(window.location.search, nextId, defaultChannel.id)}`,
        );
    };

    const matrix = useMemo(
        () =>
            status.kind === "ready" && state !== null
                ? buildMatrix(status.manifest, state)
                : null,
        [status, state],
    );

    if (status.kind === "loading")
        return (
            <output className="block text-muted-foreground">
                Loading the catalog…
            </output>
        );
    if (status.kind === "missing")
        return (
            <output className="block text-muted-foreground">
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
    // Tied to loadedChannelId (== status.channelId here — status.kind is
    // "ready"), not the raw `channelId` selection: a switch can be selected
    // before its fetch resolves, and `manifest`/`state` above are still the
    // previous channel's the whole time, so the label and asset paths below
    // must stay paired with the channel that actually produced them, not
    // jump ahead of the data and claim a channel whose content isn't shown
    // yet. loadedChannelId always names one of `channels` by construction,
    // the same way channelId does (see switchChannel below).
    const currentChannel =
        channels.find((channel) => channel.id === loadedChannelId) ??
        defaultChannel;
    const availableChannels = channels.filter((channel) =>
        availableIds.has(channel.id),
    );

    return (
        <div>
            <p className="mb-6 text-muted-foreground">
                {currentChannel.label} build, rendered from commit{" "}
                <code>{manifest.gitSha.slice(0, 7)}</code> on{" "}
                <time dateTime={manifest.generatedAt}>
                    {manifest.generatedAt.slice(0, 10)}
                </time>{" "}
                · {manifest.entries.length} renders
            </p>

            <div className="mb-8 flex flex-wrap items-end gap-6">
                {availableChannels.length > 1 && (
                    <BuildSwitch
                        channels={availableChannels}
                        // The switch itself follows the selection
                        // (channelId), not currentChannel — it presses the
                        // just-clicked item immediately, the same instant the
                        // URL updates, rather than waiting on that channel's
                        // fetch the way the label/images below deliberately do.
                        value={channelId ?? currentChannel.id}
                        onChange={switchChannel}
                    />
                )}
                <Controls manifest={manifest} state={state} update={update} />
            </div>

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
                                    className="text-left text-sm font-semibold whitespace-nowrap text-muted-foreground"
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
                                    className="text-left text-sm font-semibold whitespace-nowrap text-muted-foreground"
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
                                                <span className="grid aspect-[5/3] w-60 max-w-[60vw] place-items-center rounded-md border border-dashed border-border text-muted-foreground">
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
                                                    className="block cursor-zoom-in rounded-md border border-border bg-card focus-visible:outline-2 focus-visible:outline-ring"
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
                                                        src={`${currentChannel.assetBase}${entry.thumb}`}
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
                assetBase={currentChannel.assetBase}
                returnFocusTo={lastCell}
            />
        </div>
    );
}
