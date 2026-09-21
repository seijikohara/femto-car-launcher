// docs/src/catalog/schema.ts
// The catalog manifest's schema version, shared by the Node import script
// (scripts/import-catalog.ts) and the browser island (CatalogViewer.tsx).
// It lives in its own module rather than manifest.ts (type-only re-exports)
// or import-catalog.ts itself, which imports `sharp` and must never reach
// the browser bundle.
export const CATALOG_SCHEMA_VERSION = 1;
