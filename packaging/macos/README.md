# macOS app icon

jpackage needs an **`.icns`** file on macOS (not PNG/JPEG directly).

## Quick start

1. **Source artwork** (either):
   - **SVG (preferred):** `packaging/macos/icon-source.svg` — vector Box logo; rasterized automatically on macOS.
   - **PNG:** `packaging/macos/icon-source.png` — square 1024×1024 if you skip SVG.

2. Generate the icon set:

   ```bash
   ./scripts/make-macos-icon.sh
   ```

   If `icon-source.svg` exists, the script uses macOS `qlmanage` to render a sharp PNG, then builds `box-upload-perf.icns`.

   Force re-render from SVG after edits: `BOX_UPLOAD_PERF_FORCE_SVG_RENDER=1 ./scripts/make-macos-icon.sh`

3. Rebuild the package:

   ```bash
   ./scripts/jpackage-macos.sh
   ```

`jpackage-macos.sh` passes `--icon` automatically when `box-upload-perf.icns` exists.

## Manual `.icns` creation

If you already have an `.icns` from a designer, place it at:

`packaging/macos/box-upload-perf.icns`

Or set a custom path when building:

```bash
BOX_UPLOAD_PERF_ICON=/path/to/MyApp.icns ./scripts/jpackage-macos.sh
```

## Design tips

- Use a **square** canvas; macOS applies rounded corners in the UI.
- Keep important content inside the center ~80% (corners are clipped in Dock/Finder).
- Transparent backgrounds are supported in PNG source; the generated `.icns` includes alpha where applicable.
