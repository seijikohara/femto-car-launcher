# Play Store listing assets

These are **store-listing** assets uploaded to the Play Console. They are **not**
shipped inside the app (the in-app adaptive launcher icon lives under
`app/src/main/res/mipmap-anydpi/` and is derived via the `update-launcher-icon`
skill).

## `ic_launcher-512` — hi-res app icon (512×512, 32-bit PNG)

Required by Play Console → Store listing → App icon.

- `ic_launcher-512.svg` is the source: the brand mark from the repo-root
  `logo.svg`, but **full-bleed** (square background, no rounded corners) because
  Play applies its own icon mask. Keep it in sync with `logo.svg`.
- `ic_launcher-512.png` is the exported bitmap to upload.

Regenerate the PNG from the SVG after any logo change:

```bash
# macOS (Quick Look, no extra tooling):
qlmanage -t -s 512 -o art/play art/play/ic_launcher-512.svg
mv art/play/ic_launcher-512.svg.png art/play/ic_launcher-512.png

# or with librsvg:
rsvg-convert -w 512 -h 512 art/play/ic_launcher-512.svg -o art/play/ic_launcher-512.png
```

## `feature-graphic` — feature graphic (1024×500, PNG)

Required by Play Console → Store listing → Graphics.

- `feature-graphic.svg` is the source: the `logo.svg` chevron mark plus the
  wordmark on the brand background (`#0E1318` / accent `#3BE0AE`). Keep the colours
  in sync with `logo.svg`.
- `feature-graphic.png` is the exported 1024×500 bitmap to upload.

Regenerate the PNG from the SVG:

```bash
# librsvg renders the 1024x500 SVG directly:
rsvg-convert -w 1024 -h 500 art/play/feature-graphic.svg -o art/play/feature-graphic.png

# macOS Quick Look only emits square thumbnails, so wrap the design in a 1024x1024
# square (full-bleed background, content translated by 262) and crop the centre:
#   qlmanage -t -s 1024 -o /tmp <square-wrapper>.svg
#   sips -c 500 1024 art/play/feature-graphic.png
```

## `screenshots/` — store screenshots (PNG)

Phone screenshots for the listing, captured on the TBox-Mock head-unit geometry
(800×480 landscape, within Play's 320–3840 px / ≤ 2:1 limits). Three surfaces ×
both themes: `dashboard-{dark,light}` (live map, weather, calendar, trip),
`drawer-{dark,light}` (app search + grid), `settings-{dark,light}`.

Capture conditions (keep them reproducible and privacy-clean):

- **Locale `en-US`** via per-app override (`cmd locale set-app-locales <pkg>
  --locales en-US`) so dates/units render in English.
- **Location** set to a recognisable English-locale city (San Francisco) so the
  map tiles and reverse-geocoded address are in English; wait for the live map
  to render before capturing.
- **Calendar** shows neutral sample events from a throwaway local calendar; the
  device's real Google calendars are temporarily set `visible=0` during capture
  and restored afterwards, so no personal events appear (relies on the
  visibility filter in `CalendarRepository`).

Recapture after UI changes.

## Authored in the Play Console (not in the repo)

- Short description, full description, and the content-rating answers.

A raster `mipmap` fallback icon is intentionally omitted: `minSdk` is 33, where
adaptive icons are always supported.
