# Packaging (jpackage)

Distribute the app **without requiring users to install Java**. jpackage bundles a private runtime with the shaded JAR.

## Prerequisites

- **macOS** (to build macOS `.app` / `.dmg`; use Windows or Linux to build for those platforms)
- **JDK 21+** with `jpackage` on `PATH` (`java -version` and `jpackage --version`)
- **Maven 3.9+**

## macOS build

From the repository root:

```bash
chmod +x scripts/jpackage-macos.sh
./scripts/jpackage-macos.sh
```

Outputs under `target/dist/`:

| Artifact | Description |
|----------|-------------|
| `box-upload-perf.app` | Application bundle (~190 MB, includes JRE) |
| `box-upload-perf-*.dmg` | Disk image for distribution |

### Run the packaged app

```bash
target/dist/box-upload-perf.app/Contents/MacOS/box-upload-perf run --help
```

Or open the `.app` from Finder (Terminal/wizard still work; use Terminal for full logs).

### First-time setup

The bundle does **not** include Box credentials. Either:

1. Run the wizard: `box-upload-perf wizard`
2. Copy an example profile:  
   `cp config/examples/smoke-test-cleanup.yaml ~/.box-upload-perf/profiles/my-run.yaml`  
   (edit credentials, then `box-upload-perf run --profile my-run`)

Profiles and results use the same paths as the JAR workflow (`~/.box-upload-perf/profiles/`, `./results/` relative to the current working directory).

## Manual jpackage (any platform)

```bash
mvn -q package -DskipTests

jpackage --input target \
  --name box-upload-perf \
  --main-jar box-upload-perf-1.0.0-SNAPSHOT.jar \
  --main-class com.boxuploadperf.BoxUploadPerfApp \
  --type app-image \
  --dest target/dist \
  --app-version 1.0.0 \
  --java-options "--enable-native-access=ALL-UNNAMED"
```

On macOS, add `--type dmg` in a second invocation (or use the script).

## Notes

- **Platform-specific**: Build on the OS you target (macOS → `.app`/`.dmg`, Windows → `.exe`/`.msi`, Linux → `.deb`/`.rpm` or app-image).
- **SQLite native access**: `--enable-native-access=ALL-UNNAMED` is set in the packaged launcher (Java 21+).
- **HTML charts**: `charts/index.html` loads Chart.js from a CDN; viewing charts needs network access in the browser.
- **Optional polish not included**: custom app icon (`.icns`), code signing, notarization — add for production distribution.
