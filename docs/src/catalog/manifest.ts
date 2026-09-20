// The browser-side view of the site manifest: the import script owns the
// shape, so the types are re-exported (type-only — nothing of the script is
// bundled).
export type {
    Axis,
    AxisValue,
    SiteEntry,
    SiteManifest,
} from "../../scripts/import-catalog.ts";
