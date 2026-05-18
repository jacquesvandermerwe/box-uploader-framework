#!/usr/bin/env bash
# Build a macOS .app and .dmg for Box Upload Performance (no separate JRE install required).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "This script must run on macOS (jpackage produces platform-specific installers)." >&2
  exit 1
fi

if ! command -v jpackage >/dev/null 2>&1; then
  echo "jpackage not found. Use JDK 21+ (jpackage is included with the JDK)." >&2
  exit 1
fi

APP_NAME="box-upload-perf"
MAIN_CLASS="com.boxuploadperf.BoxUploadPerfApp"
DIST_DIR="$ROOT/target/dist"
JAR="$ROOT/target/box-upload-perf-1.0.0-SNAPSHOT.jar"

echo "==> Building fat JAR..."
mvn -q package -DskipTests

if [[ ! -f "$JAR" ]]; then
  echo "Missing $JAR after package." >&2
  exit 1
fi

VERSION="$(mvn -q help:evaluate -Dexpression=project.version -DforceStdout | sed 's/-SNAPSHOT//')"
echo "==> Packaging version $VERSION for macOS..."

rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR"

JPACKAGE_COMMON=(
  --input "$ROOT/target"
  --name "$APP_NAME"
  --main-jar "$(basename "$JAR")"
  --main-class "$MAIN_CLASS"
  --app-version "$VERSION"
  --vendor "Box Upload Performance Framework"
  --description "Box.com upload throughput and latency benchmark"
  --copyright "Box Upload Performance Framework"
  --dest "$DIST_DIR"
  --java-options "--enable-native-access=ALL-UNNAMED"
)

echo "==> Creating application image (.app)..."
jpackage "${JPACKAGE_COMMON[@]}" --type app-image

echo "==> Creating disk image (.dmg)..."
jpackage "${JPACKAGE_COMMON[@]}" --type dmg

APP="$DIST_DIR/$APP_NAME.app"
DMG="$(ls -1 "$DIST_DIR"/*.dmg 2>/dev/null | head -1)"

echo ""
echo "Done."
echo "  Application: $APP"
echo "  CLI:         $APP/Contents/MacOS/$APP_NAME"
if [[ -n "$DMG" ]]; then
  echo "  Installer:   $DMG"
fi
echo ""
echo "Run from Terminal:"
echo "  \"$APP/Contents/MacOS/$APP_NAME\" run --help"
echo ""
echo "Profiles: ~/.box-upload-perf/profiles/ (create via wizard or copy config/examples/)"
