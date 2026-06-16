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

## Still to prepare (cannot be generated from the repo)

- Feature graphic (1024×500).
- Screenshots: landscape (head-unit geometry — the `verify-on-emulator` skill can
  capture these) and portrait (phone).

A raster `mipmap` fallback icon is intentionally omitted: `minSdk` is 33, where
adaptive icons are always supported.
