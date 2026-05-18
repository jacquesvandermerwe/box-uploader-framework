# Packaging and installation

This document has two audiences:

| Section | Audience |
|---------|----------|
| [Building a package](#building-a-package) | Developers who build `.app` / `.dmg` from source |
| [Installing on a target system](#installing-on-a-target-system) | Operators who receive a built installer and run benchmarks |

The packaged app **does not require a separate Java install** — jpackage embeds a private JRE inside the bundle.

For day-to-day usage after install (profiles, reports, CLI flags), see [USER_GUIDE.md](USER_GUIDE.md).

---

## Building a package

### Prerequisites

- **macOS** (to build macOS `.app` / `.dmg`; build on Windows or Linux for those platforms)
- **JDK 21+** with `jpackage` on `PATH` (`java -version` and `jpackage --version`)
- **Maven 3.9+**
- Network access for Maven dependencies on first build

### macOS build (recommended)

From the repository root:

```bash
chmod +x scripts/jpackage-macos.sh
./scripts/jpackage-macos.sh
```

The script runs `mvn package`, then invokes `jpackage` twice: **app-image** (`.app`) and **dmg**.

**Outputs** under `target/dist/` (gitignored; copy elsewhere to distribute):

| Artifact | Description |
|----------|-------------|
| `box-upload-perf.app` | Application bundle (~190 MB, includes JRE) |
| `box-upload-perf-*.dmg` | Disk image for distribution (~170 MB) |

**Verify the build:**

```bash
target/dist/box-upload-perf.app/Contents/MacOS/box-upload-perf --help
```

### Manual jpackage (any platform)

```bash
mvn -q package -DskipTests

jpackage --input target \
  --name box-upload-perf \
  --main-jar box-upload-perf-1.0.0-SNAPSHOT.jar \
  --main-class com.boxuploadperf.BoxUploadPerfApp \
  --type app-image \
  --dest target/dist \
  --app-version 1.0.0 \
  --vendor "Box Upload Performance Framework" \
  --description "Box.com upload throughput and latency benchmark" \
  --java-options "--enable-native-access=ALL-UNNAMED"
```

On macOS, run again with `--type dmg` (or use `scripts/jpackage-macos.sh`). Add `--icon packaging/macos/box-upload-perf.icns` when you have a custom icon (see below).

### Custom application icon (macOS)

Without `--icon`, jpackage uses the default Java coffee-cup icon. macOS requires **`.icns`** (not PNG).

**Recommended workflow:**

1. Edit `packaging/macos/icon-source.svg` (vector, preferred) or replace `icon-source.png` (square 1024×1024).
2. Generate the icon set:

   ```bash
   chmod +x scripts/make-macos-icon.sh
   ./scripts/make-macos-icon.sh
   ```

   Output: `packaging/macos/box-upload-perf.icns`

3. Rebuild: `./scripts/jpackage-macos.sh` (picks up the `.icns` automatically).

**Already have an `.icns`?** Copy it to `packaging/macos/box-upload-perf.icns`, or:

```bash
BOX_UPLOAD_PERF_ICON=/path/to/YourIcon.icns ./scripts/jpackage-macos.sh
```

**Manual `jpackage` flag:**

```bash
jpackage ... --icon packaging/macos/box-upload-perf.icns
```

| Platform | Icon format |
|----------|-------------|
| macOS | `.icns` |
| Windows | `.ico` |
| Linux | `.png` |

More detail: [packaging/macos/README.md](../packaging/macos/README.md).

### Build notes

- **Platform-specific**: A macOS `.dmg` must be built on macOS. Same for Windows `.msi` / Linux `.deb` on their respective OSes.
- **SQLite**: `--enable-native-access=ALL-UNNAMED` is set in the packaged launcher (Java 21+).
- **Optional**: custom `.icns` (see above); Apple code signing and notarization for production distribution. Unsigned builds may trigger Gatekeeper on first open (see [Gatekeeper and unsigned builds](#gatekeeper-and-unsigned-builds)).
- **HTML charts**: `charts/index.html` loads Chart.js from a CDN; viewing charts in a browser needs network access.

### Distributing what you built

Copy `box-upload-perf-*.dmg` (or the `.app` folder) to the target machine via your usual channel (file share, MDM, email attachment if size allows). Record the version from the filename or `box-upload-perf --version` after install.

---

## Installing on a target system

These steps apply to **macOS end users** who received `box-upload-perf-*.dmg`. No JDK or Maven is required on the target machine.

### System requirements

| Requirement | Detail |
|-------------|--------|
| **OS** | macOS 11+ (Big Sur or later recommended; match the OS used at build time when possible) |
| **Architecture** | Apple Silicon or Intel — use a build made for the target CPU (or a universal build if you produce one) |
| **Disk space** | ~250 MB free for the app; additional space for `results/` and `work/` during runs |
| **Network** | Outbound HTTPS to Box APIs and upload hosts |
| **Box** | CCG app with upload scope, enterprise ID, parent folder ID (see [USER_GUIDE.md](USER_GUIDE.md#configuring-a-profile)) |

### Install from the disk image

1. **Open the DMG**  
   Double-click `box-upload-perf-1.0.0.dmg` (version in the filename may differ).

2. **Install the application**  
   Drag **box-upload-perf** into **Applications** (or another location your org allows).

3. **Eject the disk image**  
   Right-click the mounted volume → Eject.

4. **Optional: add CLI to your shell**  
   The executable inside the bundle is not on `PATH` by default. Either use the full path each time, or add an alias (see [Running from Terminal](#running-from-terminal)).

### Gatekeeper and unsigned builds

Development builds are often **not signed or notarized**. macOS may block the first launch.

**If macOS says the app cannot be opened because it is from an unidentified developer:**

1. Open **System Settings → Privacy & Security**.
2. Under **Security**, find the message about `box-upload-perf` being blocked and click **Open Anyway** (after attempting to open the app once).
3. Alternatively, right-click the app in Finder → **Open** → confirm **Open** in the dialog.

For production rollout, sign and notarize the app with an Apple Developer ID (not covered in this repo).

### Verify installation

Open **Terminal** and run:

```bash
/Applications/box-upload-perf.app/Contents/MacOS/box-upload-perf --version
/Applications/box-upload-perf.app/Contents/MacOS/box-upload-perf --help
```

You should see version information and the PicoCLI help text. No `java` command is needed.

### Running from Terminal

**Full path (always works):**

```bash
/Applications/box-upload-perf.app/Contents/MacOS/box-upload-perf <command> [options]
```

**Optional: shell alias** (add to `~/.zshrc` or `~/.bash_profile`):

```bash
alias box-upload-perf='/Applications/box-upload-perf.app/Contents/MacOS/box-upload-perf'
```

Then:

```bash
box-upload-perf run --help
```

**Working directory matters for results.** The app writes `./results/<runId>/` relative to the **current directory** when you run a benchmark. `cd` to a folder where you want reports before `run`:

```bash
mkdir -p ~/box-benchmarks && cd ~/box-benchmarks
box-upload-perf run --profile my-benchmark
# → ~/box-benchmarks/results/<runId>/
```

Opening the `.app` from Finder without Terminal uses Finder’s working directory; **use Terminal for predictable `results/` paths and full console logs.**

### First-time setup on the target machine

The installer **does not include** Box credentials or profiles.

**Option A — Interactive wizard (recommended first run):**

```bash
box-upload-perf wizard
# or
box-upload-perf run
```

Follow prompts for CCG credentials, enterprise ID, parent folder ID, and workload. Save a named profile when asked.

**Option B — Copy a profile from templates:**

If you have example YAML from your team (no secrets in git templates):

```bash
mkdir -p ~/.box-upload-perf/profiles
cp /path/from/admin/smoke-test.yaml ~/.box-upload-perf/profiles/my-run.yaml
# Edit clientId, clientSecret, enterpriseId, parentFolderId, etc.
box-upload-perf run --profile my-run
```

Profiles are stored in:

```text
~/.box-upload-perf/profiles/
```

Same layout as the JAR workflow documented in [USER_GUIDE.md](USER_GUIDE.md#configuring-a-profile).

### Run a benchmark

```bash
cd ~/box-benchmarks   # or any directory where you want results/

# First run or wizard
box-upload-perf run

# Repeatable profile
box-upload-perf run --profile my-benchmark

# Overrides (same as JAR)
box-upload-perf run --profile my-benchmark \
  --concurrency 8 --file-count 20 --enforce-rate-limit
```

During the run, watch for `[box-upload-perf]` progress lines in the terminal. When finished:

| Output | Location |
|--------|----------|
| SQLite metrics | `./results/<runId>/metrics.db` |
| Routing summary | `./results/<runId>/routing.json` |
| HTML report | `./results/<runId>/charts/index.html` |

Open the HTML file in a browser (Chart.js loads from the internet).

### Commands available in the package

Same CLI as the shaded JAR:

| Command | Description |
|---------|-------------|
| `run` | Execute benchmark (`--profile` or `--config`, or wizard) |
| `wizard` | Setup wizard only |
| `profile list` | List saved profiles |
| `profile show <name>` | Show profile (secrets redacted) |
| `profile delete <name>` | Delete profile |

See [USER_GUIDE.md](USER_GUIDE.md) for configuration keys, example profiles in `config/examples/`, and report interpretation.

### Updating or removing

**Update:** Install a newer `.dmg` over the previous app in **Applications** (replace the bundle). Profiles in `~/.box-upload-perf/profiles/` are preserved.

**Uninstall:**

```bash
rm -rf /Applications/box-upload-perf.app
# Optional: remove local data
rm -rf ~/.box-upload-perf
```

Keep `~/box-benchmarks/results/` (or wherever you ran tests) if you still need historical reports.

### Troubleshooting on the target system

| Symptom | What to try |
|---------|-------------|
| “App is damaged” or won’t open | Gatekeeper; use **Open Anyway** or right-click → **Open**. For org Macs, IT may need to allow the bundle. |
| `command not found: box-upload-perf` | Use the full path under `.app/Contents/MacOS/` or set the [alias](#running-from-terminal). |
| No `results/` where expected | Run from Terminal after `cd` to the desired directory. |
| SQLite / native access warnings | Packaged build should include `--enable-native-access=ALL-UNNAMED`; reinstall from a current build. |
| Upload/auth failures | Check profile credentials and Box app scopes; see [USER_GUIDE.md](USER_GUIDE.md). |
| Blank charts | Open `charts/index.html` in a browser with internet access (CDN). |

### JAR workflow on the same machine

Developers can still use the JAR alongside the app:

```bash
java --enable-native-access=ALL-UNNAMED \
  -jar box-upload-perf-1.0.0-SNAPSHOT.jar run --profile my-benchmark
```

Profiles and `~/.box-upload-perf/` are **shared** between JAR and packaged app.
