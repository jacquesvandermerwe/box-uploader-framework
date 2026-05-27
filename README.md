# Box Upload Performance Framework

Local Java 21+ benchmark harness for measuring Box.com upload throughput, latency, and network behavior via direct REST (no Box SDK).

**Documentation:** [docs/USER_GUIDE.md](docs/USER_GUIDE.md) — execution flow, profile configuration, example profiles, reports, console warnings.

**Packaging & install:** [docs/PACKAGING.md](docs/PACKAGING.md) — build macOS `.dmg` from source; install and run on a target Mac without Java.

Requirements detail: [docs/PRD.md](docs/PRD.md).

## Build

```bash
mvn -q package
java -jar target/box-upload-perf-1.0.0-SNAPSHOT.jar --help
```

Optional (suppresses SQLite native-access warning on Java 21+):

```bash
java --enable-native-access=ALL-UNNAMED -jar target/box-upload-perf-1.0.0-SNAPSHOT.jar run --profile my-benchmark
```

### macOS package (build)

Build a `.app` and `.dmg` with an embedded JRE (requires JDK 21+ on the **build machine**):

```bash
./scripts/jpackage-macos.sh
```

Artifacts: `target/dist/box-upload-perf.app` and `box-upload-perf-*.dmg`. See [Building a package](docs/PACKAGING.md#building-a-package).

### macOS package (install on target system)

End users who receive the `.dmg` do **not** need Java or Maven. Install by dragging the app to **Applications**, then run from Terminal:

```bash
/Applications/box-upload-perf.app/Contents/MacOS/box-upload-perf wizard
cd ~/box-benchmarks && /Applications/box-upload-perf.app/Contents/MacOS/box-upload-perf run --profile my-benchmark
```

Full steps (Gatekeeper, PATH alias, profiles, results directory, uninstall): [Installing on a target system](docs/PACKAGING.md#installing-on-a-target-system).

## Quick start

### Interactive wizard (first run)

```bash
java -jar target/box-upload-perf-1.0.0-SNAPSHOT.jar run
```

Enter Box CCG credentials, **enterprise ID**, **parent folder ID**, optional **impersonation user ID(s)** (comma-separated for round-robin via `As-User`), and workload settings. Save a named profile for repeat runs.

### Repeatable profile

```bash
# Copy an example, edit credentials, save under ~/.box-upload-perf/profiles/
cp config/examples/low-concurrency.yaml ~/.box-upload-perf/profiles/low-concurrency.yaml

java -jar target/box-upload-perf-1.0.0-SNAPSHOT.jar run --profile low-concurrency
```

Profiles live in `~/.box-upload-perf/profiles/` (gitignored). **Example templates** (no secrets) are in [config/examples/](config/examples/).

### Config file (one-off)

```bash
cp config/config.example.yaml ~/.box-upload-perf/profiles/staging.yaml
# edit credentials and settings
java -jar target/box-upload-perf-1.0.0-SNAPSHOT.jar run --profile staging
# or
java -jar target/box-upload-perf-1.0.0-SNAPSHOT.jar run --config config/examples/smoke-test-cleanup.yaml
```

## Output

Each run writes to `./results/<runId>/`:

| Artifact | Description |
|----------|-------------|
| `metrics.db` | SQLite — `api_calls`, `file_uploads`, `resource_samples`, `run_summaries` |
| `routing.json` | Upload zone and URL summary for routing checks |
| `charts/index.html` | Configuration, aggregate metrics, routing, charts |

See [docs/USER_GUIDE.md](docs/USER_GUIDE.md#run-outputs-and-reports) for field-by-field explanations and [docs/examples/](docs/examples/) for sample output.

## Run options

```bash
java -jar target/box-upload-perf-1.0.0-SNAPSHOT.jar run --profile my-benchmark \
  --concurrency 8 --file-count 20 --enforce-rate-limit
```

Console output uses `[box-upload-perf]` progress lines during the run. See [docs/USER_GUIDE.md](docs/USER_GUIDE.md).

## Commands

| Command | Description |
|---------|-------------|
| `run` | Execute benchmark (`--profile` or `--config`, or wizard); optional CLI overrides |
| `wizard` | Setup wizard only (`--save-only` optional) |
| `profile list` | List saved profiles |
| `profile show <name>` | Show profile (secret redacted) |
| `profile delete <name>` | Delete profile |
| `failures` | List failed uploads for a past run (`--run-id`, optional `--results-dir`, `--limit`, `--verbose`) |

## Requirements

- Java 21+
- Box CCG app with upload scope
- Valid parent folder ID on Box
