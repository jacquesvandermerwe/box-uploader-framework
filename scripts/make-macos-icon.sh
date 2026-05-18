#!/usr/bin/env bash
# Build packaging/macos/box-upload-perf.icns from a square PNG (for jpackage --icon).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MACOS_DIR="$ROOT/packaging/macos"
SVG="$MACOS_DIR/icon-source.svg"
PNG="$MACOS_DIR/icon-source.png"
SOURCE="${BOX_UPLOAD_PERF_ICON_SOURCE:-$PNG}"
OUT_ICNS="${BOX_UPLOAD_PERF_ICON:-$MACOS_DIR/box-upload-perf.icns}"
ICON_RASTER_PX="${BOX_UPLOAD_PERF_ICON_RASTER_PX:-1024}"
STAGE="${MACOS_DIR}/.icon-build-$$"
ICONSET="${MACOS_DIR}/box-upload-perf.iconset"

cleanup() { rm -rf "$STAGE" "$ICONSET"; }
trap cleanup EXIT

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "iconutil and sips require macOS." >&2
  exit 1
fi

if ! command -v sips >/dev/null 2>&1 || ! command -v iconutil >/dev/null 2>&1; then
  echo "sips and iconutil are required (included with macOS)." >&2
  exit 1
fi

render_svg_to_png() {
  if [[ ! -f "$SVG" ]]; then
    return 0
  fi
  if [[ -f "$PNG" && "$PNG" -nt "$SVG" && "${BOX_UPLOAD_PERF_FORCE_SVG_RENDER:-0}" != "1" ]]; then
    return 0
  fi
  if ! command -v qlmanage >/dev/null 2>&1; then
    echo "qlmanage required to rasterize $SVG (included with macOS)." >&2
    exit 1
  fi
  local thumb="$MACOS_DIR/.icon-thumb-$$.png"
  echo "==> Rasterizing $SVG to ${ICON_RASTER_PX}x${ICON_RASTER_PX} PNG..."
  qlmanage -t -s "$((ICON_RASTER_PX * 2))" -o "$MACOS_DIR" "$SVG" >/dev/null
  mv "$MACOS_DIR/$(basename "$SVG").png" "$thumb"
  sips -z "$ICON_RASTER_PX" "$ICON_RASTER_PX" "$thumb" --out "$PNG" >/dev/null
  rm -f "$thumb"
  SOURCE="$PNG"
}

render_svg_to_png

if [[ ! -f "$SOURCE" ]]; then
  echo "Source image not found: $SOURCE" >&2
  echo "Add packaging/macos/icon-source.svg or icon-source.png (1024x1024 recommended)." >&2
  exit 1
fi

mkdir -p "$MACOS_DIR" "$STAGE"

make_icon() {
  local size=$1
  local name=$2
  sips -z "$size" "$size" "$SOURCE" --out "$STAGE/$name" >/dev/null
}

make_icon 16 icon_16x16.png
make_icon 32 icon_16x16@2x.png
make_icon 32 icon_32x32.png
make_icon 64 icon_32x32@2x.png
make_icon 128 icon_128x128.png
make_icon 256 icon_128x128@2x.png
make_icon 256 icon_256x256.png
make_icon 512 icon_256x256@2x.png
make_icon 512 icon_512x512.png
make_icon 1024 icon_512x512@2x.png

rm -rf "$ICONSET"
mv "$STAGE" "$ICONSET"
rmdir "$STAGE" 2>/dev/null || true

iconutil -c icns "$ICONSET" -o "$OUT_ICNS"

echo "Wrote $OUT_ICNS"
echo "Rebuild package: ./scripts/jpackage-macos.sh"
