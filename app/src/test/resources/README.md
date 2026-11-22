# Test resources

## `map-backdrop-osm{,-dark}.png` — still maps for the dashboard goldens

The dashboard goldens render the real UI, but not the real map: the map is a WebView, and Robolectric's WebView is a shadow with no Chromium behind it. A golden could therefore only ever show an empty map region — which looks nothing like the running app — and one that fetched live tiles would stop being deterministic, so it could not serve as a regression reference.

`DashboardScreenshotTest` substitutes this still through `MapPanel`'s `mapSurface` slot (null in production, where the live `WebMapView` renders). Everything else in the golden — cards, dock, overlays, map controls — is drawn by the current code, so a UI change shows up without touching this file. The self-marker chevron is a special case: the live page draws it into the WebView DOM, so the fixture (`MapBackdrop` / `SelfMarker` in `testfixtures/`) reproduces it on top of the still from the same placement inputs, mirroring `webmap/src/style.ts` (see `SelfMarkerAnchor`).

**Provenance.** Renders of this app's own map page (the built `webmap/` page with the bundled Standard styles and 3D buildings, in headless Chromium) drawing OpenFreeMap vector tiles (OpenStreetMap data under ODbL) over San Francisco, with every overlay hidden. Two of them, because the app swaps the map style with the theme: the Standard scheme's light style and its dark style. The dashboard fixtures place the fix and the reverse-geocoded address in the same city so the two agree.

Recapture only when the map's own appearance changes (a style or backend default). Draw the app's map page in the theme you are recapturing, either on a device (`adb shell cmd uimode night yes|no`, then a screenshot cropped clear of every overlay) or in a headless browser with the overlays hidden, at a large landscape-ish size. The backdrop image is scaled with `ContentScale.Crop`, so it must stay big enough for the widest golden (currently 2000 px) and square-ish enough for the portrait ones.

## `offlinemap/` — vector tiles for the offline-map converter tests

Seven tiles in two forms each, named `<region>-<z>-<x>-<y>` in XYZ numbering:

- `*.sb.mvt.gz` is a tile exactly as a Geofabrik Shortbread MBTiles extract stores it (a gzip-compressed Mapbox Vector Tile in the Shortbread 1.0 schema).
- `*.omt.mvt.gz` is the expected conversion of that tile into the OpenMapTiles layers the bundled map styles read, gzip-compressed for size.

The seven were picked so that together they exercise nearly every branch of the conversion: Japanese and Greek names beside Latin ones, place classes from country to hamlet, every road class, rail and tram, aeroways, water, land cover, land use, boundaries, piers, ferries and buildings.

**Provenance.** Tiles from the Cyprus and Shikoku extracts on `download.geofabrik.de` (`*-shortbread-1.0.mbtiles`, fetched 2026-11-17), unmodified. © OpenStreetMap contributors, available under the Open Database License. The expected conversions came from the prototype converter that the Kotlin one was ported from; `ShortbreadConverterGoldenTest` pins them.

Regenerate the expected files only when a conversion rule changes on purpose: run `ShortbreadConverterGoldenTest` with the environment variable `FEMTO_UPDATE_GOLDENS=1`. The test rewrites the `*.omt.mvt.gz` files and then fails, so a regenerated file never passes unnoticed.

## `offlinemap/regions/` — the region list for the saved-regions tests

- `region-index-sample.json` has the shape of Geofabrik's region index (`https://download.geofabrik.de/index-v1.json`): a GeoJSON `FeatureCollection` whose features carry `id`, `parent`, `name` and `urls`, with a `MultiPolygon` each. It holds 22 of the 554 regions, picked for the cases the catalogue has to handle: roots, countries that exist only as sub-regions (Japan, Germany, Poland), a region with a file whose sub-regions have none (Bayern, California), ids with a slash whose `name` repeats the id and whose `parent` skips a level (`us/california`, `us/nevada`), a name with markup (`dolnoslaskie`), a region made of several polygons (Kantō), and a subtree with no file at all (Canada).
- `shortbread-regions-sample.json` has the shape of the committed snapshot `app/src/main/assets/map/offline/shortbread-regions.json`: the ids among those 22 that have a Shortbread file, with the file's size in bytes.

**Provenance.** The `properties` are copied unchanged from the index as fetched on 2026-11-19. The geometry is not Geofabrik's: every ring is replaced by its bounding box, which keeps the nesting (polygons, rings, `[longitude, latitude]` positions) and makes containment answers easy to read in a test. The sizes are the ones `scripts/update-shortbread-regions.mjs` recorded on the same day.

Regenerate only when the index changes shape. The tests pin names, parents and sizes from these files, so a regenerated fixture needs its expectations revisited.
