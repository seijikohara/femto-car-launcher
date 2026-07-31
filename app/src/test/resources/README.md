# Screenshot-test resources

## `map-backdrop-osm{,-dark}.png` — still maps for the dashboard goldens

The dashboard goldens render the real UI, but not the real map: the map is a
WebView, and Robolectric's WebView is a shadow with no Chromium behind it. A
golden could therefore only ever show an empty map region — which looks nothing
like the running app — and one that fetched live tiles would stop being
deterministic, so it could not serve as a regression reference.

`DashboardScreenshotTest` substitutes this still through `MapPanel`'s
`mapSurface` slot (null in production, where the live `WebMapView` renders).
Everything else in the golden — cards, dock, overlays, self-marker, map controls
— is drawn by the current code, so a UI change shows up without touching this
file.

**Provenance.** Device captures of this app rendering the OSM backend
(OpenFreeMap vector tiles, OpenStreetMap data under ODbL) over San Francisco,
cropped clear of every UI overlay. Two of them, because the app swaps the map
style with the theme: Positron for light, Dark Matter for dark. The dashboard
fixtures place the fix and the reverse-geocoded address in the same city so the
two agree.

Recapture only when the map's own appearance changes (a style or backend
default): run the app on the OSM backend in the theme you are recapturing
(`adb shell cmd uimode night yes|no`), screenshot a frame with no overlay in the
crop area, and crop to a large landscape-ish rectangle. It is scaled with
`ContentScale.Crop`, so it must stay big enough for the widest golden
(currently 2000 px) and square-ish enough for the portrait ones.
