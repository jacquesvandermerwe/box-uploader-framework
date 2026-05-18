# Box Upload Performance Framework — User Guide

This guide explains how the application runs, how to configure profiles, what the outputs mean, and how to interpret common console messages.

For product requirements and schema detail, see [PRD.md](PRD.md).

---

## Table of contents

1. [How a run executes](#how-a-run-executes)
2. [Startup delay before uploads](#startup-delay-before-uploads)
3. [Console output and JVM warnings](#console-output-and-jvm-warnings)
4. [Installing from a macOS package](#installing-from-a-macos-package)
5. [Configuring a profile](#configuring-a-profile)
6. [Example profiles](#example-profiles)
7. [Running with a profile](#running-with-a-profile)
8. [Run outputs and reports](#run-outputs-and-reports)
9. [Example report excerpts](#example-report-excerpts)

---

## How a run executes

Each `run` command loads configuration (saved profile, `--config` file, or interactive wizard), then executes setup, uploads, aggregation, and reporting.

### Sequence diagram

```mermaid
sequenceDiagram
    autonumber
    participant CLI as CLI (run)
    participant BR as BenchmarkRunner
    participant PDF as PdfPayloadGenerator
    participant DB as MetricsDatabase (SQLite)
    participant RS as ResourceSampler
    participant Box as BoxClient
    participant Pool as Upload executor

    CLI->>BR: execute(config)
    BR->>BR: validate config
    BR->>PDF: generate payload.pdf
    Note over PDF: Silent; often slowest step
    BR->>BR: SHA-256 payload
    BR->>DB: open DB, insertRunStart
    BR->>RS: start sampler thread (daemon)
    BR->>Box: authenticate (CCG token)
    BR->>Box: createRunFolder
    BR->>Box: resolveUploadZone (preflight)
    BR->>DB: store zone host / base URL
    BR->>RS: startUploadPhase()
    Note over BR: Upload-phase timer starts here
  loop For each file (1..N)
        BR->>Pool: submit upload task
        Pool->>Box: uploadFile (simple or chunked)
        Box->>DB: record api_calls, file_uploads
        RS->>DB: resource_samples (periodic)
    end
    Pool-->>BR: all tasks complete
    BR->>RS: stop sampler
    BR->>DB: endRun, RunSummarizer.compute
    opt cleanup.deleteBoxRunFolderAfterRun
        BR->>Box: deleteFolder
    end
    BR->>DB: UploadRoutingReport.write (routing.json)
    BR->>BR: HtmlChartReport.generate
    BR->>CLI: printSummary (console)
```

### Phases in plain language

| Phase | What happens | Included in `run_duration_ms`? |
|-------|----------------|--------------------------------|
| **Payload** | Build `work/payload.pdf` to `pdf.targetSizeBytes`, then hash it | No |
| **Metrics DB** | Create/open `results/<runId>/metrics.db`, record run metadata | No |
| **Auth** | Obtain Box CCG access token | No |
| **Folder** | Create a subfolder under `box.parentFolderId` for this run | No |
| **Zone preflight** | `OPTIONS` preflight to discover regional upload host (zone-aware routing) | No |
| **Upload phase** | Concurrent uploads with semaphore-limited concurrency | **Yes** |
| **Post-run** | Summaries, optional Box folder delete, `routing.json`, HTML charts | No |

The **upload phase timer** starts only after preflight completes (`sampler.startUploadPhase()` in `BenchmarkRunner`). Wall-clock time before the first byte hits Box is therefore **longer** than `run_duration_ms` in reports.

---

## Startup delay before uploads

If the process appears idle for a while, that is usually expected: most setup work has **no console progress lines** today.

### Typical contributors (largest first)

1. **PDF generation** — The harness builds a multi-page PDF until it reaches `pdf.targetSizeBytes`. The generator estimates size by serializing the whole document after each page, which is slow for ~1 MiB payloads and scales poorly for larger targets.
2. **Payload hashing** — Full-file SHA-256 read after generation.
3. **SQLite initialization** — Schema and indexes on first write.
4. **Box API setup** — CCG token, create folder, preflight (usually seconds, not minutes).
5. **Resource sampler** — Network interface discovery when the upload phase starts (OSHI).

### What you can do

- Use a **smaller** `pdf.targetSizeBytes` for smoke tests (e.g. 256 KiB).
- Set `work.reusePayload: true` to skip PDF generation when `work/payload.pdf` already matches the target size (±2%).
- Watch **`[box-upload-perf]`** lines on the console for phase and upload progress (see below).

---

## Console output and JVM warnings

### During the run

Lines prefixed with `[box-upload-perf]` report:

- Setup phases (payload, auth, folder, preflight, zone host)
- Upload progress every ~2s: `N/M succeeded`, failures, 429 count, files/s, ETA
- Per-failure messages: `Upload <index> failed: …`
- Post-run steps (summary, charts)

At the **end** you also get paths, throughput, CPU/Mbps, and routing.

### CLI overrides (no profile edit)

```bash
java -jar target/box-upload-perf-1.0.0-SNAPSHOT.jar run --profile my-benchmark \
  --concurrency 16 --file-count 20 --thread-mode VIRTUAL \
  --rate-limit 2.0 --enforce-rate-limit --payload-bytes 524288
```

### End-of-run example

See [docs/examples/sample-console-output.txt](examples/sample-console-output.txt).

### JVM / library messages

| Message | Severity | Meaning |
|---------|----------|---------|
| `SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder"` | Low | SLF4J has no binding at runtime in the fat JAR; dependent libraries (PDFBox, OSHI) log to a no-op logger. **Does not affect uploads or metrics.** |
| `WARNING: A restricted method in java.lang.System has been called` / `System::load` (SQLite) | Low | SQLite JDBC loads a native library. On Java 21+ this is a forward-looking warning. **SQLite still works.** |

Optional: suppress the SQLite warning when launching the JAR:

```bash
java --enable-native-access=ALL-UNNAMED -jar target/box-upload-perf-1.0.0-SNAPSHOT.jar run --profile my-benchmark
```

These messages are **not** upload failures and do not indicate incorrect routing by themselves.

For the **packaged macOS app**, `--enable-native-access=ALL-UNNAMED` is already set in the bundle launcher; you do not need to pass it manually.

---

## Installing from a macOS package

If you received `box-upload-perf-*.dmg` instead of a JAR, you do **not** need Java installed on the machine.

| Topic | Where to read |
|-------|----------------|
| Install from DMG, Gatekeeper, verify, uninstall | [PACKAGING.md — Installing on a target system](PACKAGING.md#installing-on-a-target-system) |
| Build `.dmg` from source | [PACKAGING.md — Building a package](PACKAGING.md#building-a-package) |

**Quick reference after install:**

```bash
# CLI inside the bundle (adjust path if not in /Applications)
BOX=/Applications/box-upload-perf.app/Contents/MacOS/box-upload-perf

$BOX --help
$BOX wizard                    # first-time credentials and profile
mkdir -p ~/box-benchmarks && cd ~/box-benchmarks
$BOX run --profile my-benchmark # writes ./results/<runId>/
```

Profiles still live in `~/.box-upload-perf/profiles/`. Configuration, overrides, and reports are identical to the JAR workflow described in the rest of this guide.

---

## Configuring a profile

Profiles are **YAML** files stored by default in:

`~/.box-upload-perf/profiles/<profile-name>.yaml`

Create one by copying an example from [config/examples/](../config/examples/), copying [config/config.example.yaml](../config/config.example.yaml), or using the setup wizard (`java -jar … run` with no `--profile`).

### Security

- Profiles contain **plaintext** `clientSecret` on disk.
- On save, the tool sets directory `0700` and file `0600` (Unix).
- **Never commit** real profiles to git. Example files in `config/examples/` use placeholders only.

### Credential rules

| Scenario | Required `box` fields |
|----------|------------------------|
| Enterprise CCG (app acts as enterprise) | `clientId`, `clientSecret`, `enterpriseId`, `parentFolderId` |
| User impersonation | `clientId`, `clientSecret`, `userId`, `parentFolderId` (`enterpriseId` optional but recommended) |

Credentials are read **only** from the profile or wizard — not from environment variables.

---

## Profile reference (all elements)

Every key the loader understands, with defaults when omitted.

### `profile`

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `name` | string | — | Profile name (filename stem). Required when saving via wizard. |
| `description` | string | — | Human-readable note (metadata only). |

### `box`

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `clientId` | string | — | Box CCG client ID. **Required.** |
| `clientSecret` | string | — | Box CCG client secret. **Required.** |
| `enterpriseId` | string | — | Enterprise numeric ID. **Required** unless `userId` is set. |
| `userId` | string | — | When set, CCG impersonates this user (`box_subject_type=user`). |
| `parentFolderId` | string | — | Folder where each run creates a subfolder and uploads files. **Required.** |
| `runFolderName` | string | `<runId>` | Name of the Box subfolder for this run. Defaults to the run UUID if omitted. |

### `upload`

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `fileCount` | int | `100` | Number of files to upload in the run. |
| `concurrency` | int | `32` | Max simultaneous in-flight uploads (semaphore). |
| `threadMode` | `VIRTUAL` \| `PLATFORM` | — | **Required.** `VIRTUAL` = virtual thread per task; `PLATFORM` = fixed platform thread pool. |
| `platformThreadPoolSize` | int | same as `concurrency` | Pool size when `threadMode` is `PLATFORM`. |
| `rateLimitPerSecond` | double | `0` | Comparison baseline; see [Rate limit settings](#rate-limit-settings). |
| `enforceRateLimit` | bool | `false` | When `true`, throttle upload **starts** to the effective limit (Box 4/s when `rateLimitPerSecond` is `0`). |
| `chunkedUploadThresholdBytes` | long | `52428800` (50 MiB) | Payload size at or above this uses chunked upload API. |
| `chunkSizeBytes` | long | `52428800` | Part size for chunked uploads. Must be ≤ payload size when chunked. |

### `pdf`

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `targetSizeBytes` | long | `1048576` (1 MiB) | Generated PDF size under `work/`. |

### `work`

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `parentDirectory` | path | `./work` | Directory for generated payload. |
| `payloadFileName` | string | `payload.pdf` | Payload filename under `parentDirectory`. |
| `reusePayload` | bool | `false` | Skip PDF generation if existing file size is within ±2% of target. |

### `retry`

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `maxAttempts` | int | `3` | HTTP retries per call on 429 or 5xx (each attempt is a row in `api_calls`). |
| `backoffMs` | long | `500` | **Fallback only** when Box does not send `Retry-After` on 429, or on 5xx (exponential: base × 2<sup>attempt−1</sup>). On 429, [Box’s `Retry-After` header](https://developer.box.com/guides/api-calls/permissions-and-errors/rate-limits) (seconds) is honored instead. |

Per-run summaries include **retry wait total/avg**, **Retry-After header avg/max**, and count of 429s missing the header. Each `api_calls` row stores `retry_after_seconds`, `retry_sleep_ms`, and `retry_delay_source` (`RETRY_AFTER`, `EXPONENTIAL_429`, `EXPONENTIAL_5XX`).

### `run`

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `runId` | string | random UUID | Run identifier; output folder name. |
| `outputDirectory` | path | `./results` | Parent directory for `results/<runId>/`. |

### `metrics`

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `sqliteFileName` | string | `metrics.db` | SQLite file inside the run directory. |
| `sampleIntervalMs` | long | `500` | Resource sampler interval during upload phase. |
| `networkInterfaceName` | string | auto | NIC name for throughput sampling (OSHI). If unset, first suitable interface is used. |

### `cleanup`

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `deleteBoxRunFolderAfterRun` | bool | `false` | Delete the Box run subfolder when the run completes. |
| `deleteLocalPayloadAfterRun` | bool | `false` | Delete `work/payload.pdf` after the run. |

### `profiles` (optional, usually in ad-hoc config files)

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `directory` | path | `~/.box-upload-perf/profiles` | Override where named profiles are stored. |

### Rate limit settings

`upload.rateLimitPerSecond` controls **comparison text** in summaries and HTML — the harness does **not** throttle uploads to this rate.

| Value | Meaning in reports |
|-------|---------------------|
| `0` (default) | Compare throughput to Box’s documented upload API limit: **240 uploads/min (4/s)**. |
| `> 0` | Compare to your custom cap (e.g. `2.0` = 2 uploads/s). |
| `< 0` (e.g. `-1`) | **No** rate-limit baseline; summary shows effective throughput and concurrency only. |

Set `upload.enforceRateLimit: true` (or `--enforce-rate-limit`) to **throttle** upload starts to that same effective limit. Cannot be combined with `rateLimitPerSecond: -1`.

Reference: [Box rate limits](https://developer.box.com/guides/api-calls/permissions-and-errors/rate-limits).

### Upload strategy (derived)

| Condition | Strategy |
|-----------|----------|
| `pdf.targetSizeBytes` < `upload.chunkedUploadThresholdBytes` | **SINGLE_STREAM** — `POST` to zone upload URL |
| `pdf.targetSizeBytes` ≥ threshold | **CHUNKED** — session, parts, commit (uses `session_endpoints` from Box) |

---

## Example profiles

Copy any file below to `~/.box-upload-perf/profiles/<name>.yaml`, replace Box credentials and folder IDs, then:

```bash
java -jar target/box-upload-perf-1.0.0-SNAPSHOT.jar run --profile <name>
```

| File | Purpose |
|------|---------|
| [config/examples/low-concurrency.yaml](../config/examples/low-concurrency.yaml) | 10 files, concurrency **2**, virtual threads, Box default rate baseline |
| [config/examples/high-concurrency-virtual.yaml](../config/examples/high-concurrency-virtual.yaml) | 50 files, concurrency **30**, `VIRTUAL` threads |
| [config/examples/high-concurrency-platform.yaml](../config/examples/high-concurrency-platform.yaml) | 50 files, concurrency **16**, `PLATFORM` thread pool |
| [config/examples/rate-limit-box-default.yaml](../config/examples/rate-limit-box-default.yaml) | Explicit `rateLimitPerSecond: 0` → 240/min comparison |
| [config/examples/rate-limit-custom.yaml](../config/examples/rate-limit-custom.yaml) | Custom cap **2 uploads/s** for comparison |
| [config/examples/rate-limit-disabled.yaml](../config/examples/rate-limit-disabled.yaml) | `rateLimitPerSecond: -1` — no baseline |
| [config/examples/smoke-test-cleanup.yaml](../config/examples/smoke-test-cleanup.yaml) | Small run, deletes Box run folder after completion |
| [config/examples/chunked-large-payload.yaml](../config/examples/chunked-large-payload.yaml) | Payload ≥ 50 MiB → chunked upload path (fewer files) |
| [config/config.example.yaml](../config/config.example.yaml) | General template with comments |

---

## Running with a profile

```bash
# Build once
mvn -q package

# List saved profiles
java -jar target/box-upload-perf-1.0.0-SNAPSHOT.jar profile list

# Show redacted YAML
java -jar target/box-upload-perf-1.0.0-SNAPSHOT.jar profile show my-benchmark

# Run
java -jar target/box-upload-perf-1.0.0-SNAPSHOT.jar run --profile my-benchmark

# One-off config file (no save)
java -jar target/box-upload-perf-1.0.0-SNAPSHOT.jar run --config ./config/examples/low-concurrency.yaml
```

Interactive wizard (no `--profile` / `--config`):

```bash
java -jar target/box-upload-perf-1.0.0-SNAPSHOT.jar run
```

Wizard-only save:

```bash
java -jar target/box-upload-perf-1.0.0-SNAPSHOT.jar wizard
```

---

## Run outputs and reports

Each run writes to **`results/<runId>/`** (or `run.outputDirectory/<runId>`).

```
results/<runId>/
├── metrics.db              # SQLite — full instrumentation
├── routing.json            # Upload hosts/URLs summary
├── benchmark-report.pdf    # PDF report (default; see report.* config)
└── charts/
    └── index.html          # Configuration, metrics, routing, charts
```

### `metrics.db` (SQLite)

| Table | Contents |
|-------|----------|
| `runs` | Run metadata, config snapshot, upload zone from preflight |
| `api_calls` | Every HTTP call: phase, URL, status, timings, 429 flag, connection reuse |
| `file_uploads` | Per-file outcome, duration, chunk count, `had_429` |
| `resource_samples` | CPU, NIC, in-flight uploads, app Mbps over time |
| `run_summaries` | Aggregated stats for charts and console |

Use any SQLite client to query; indexes exist for per-run aggregation.

### `routing.json`

Machine-readable routing audit: zone host from preflight, cached upload base URL, hosts seen on the wire, URLs grouped by API phase (`PREFLIGHT_CHECK`, `UPLOAD_SIMPLE`, `UPLOAD_PART`, etc.), and **warnings** (e.g. multiple upload hosts, host mismatch vs zone).

See [docs/examples/sample-routing.json](examples/sample-routing.json).

### `charts/index.html`

Single-page report with:

1. **Configuration** — Profile, thread mode, concurrency, payload size, upload strategy, rate-limit mode, Box folder IDs, upload zone.
2. **Aggregate metrics** — Success/fail counts, 429 count, latency min/avg/max/P95/P99, throughput, CPU, app/NIC Mbps.
3. **Upload routing** — Same information as `routing.json` in table form.
4. **Charts**
   - Upload duration vs upload index
   - CPU (process %) vs elapsed time (upload phase)
   - App upload Mbps vs elapsed time

Open in a browser: `results/<runId>/charts/index.html`.

### `benchmark-report.pdf` (PDF report)

Generated locally with **PDFBox** (no browser or Chart.js CDN). Uses the same data as the HTML report: panel-style tables, monospace values, and line charts with the same colors as Chart.js (blue / green / red). Suitable for archiving or sharing when you cannot open HTML.

| Profile key | Default | Description |
|-------------|---------|-------------|
| `report.generatePdf` | `true` | Write `report.pdfFileName` under the run directory |
| `report.uploadPdfToBox` | `false` | Upload that PDF into the Box run folder after the run |
| `report.pdfFileName` | `benchmark-report.pdf` | Local filename and Box file name |

Example: [config/examples/report-upload-to-box.yaml](../config/examples/report-upload-to-box.yaml).

Set `cleanup.deleteBoxRunFolderAfterRun: false` if you need the report to remain on Box after upload (upload happens **before** folder delete).

### Console summary fields

| Field | Meaning |
|-------|---------|
| **Effective X uploads/s** | `files_succeeded / run_duration` during upload phase |
| **vs Box upload limit / vs configured** | Percentage of the baseline from `rateLimitPerSecond` (not enforced) |
| **CPU avg/max** | Process CPU % from sampler during upload phase |
| **App upload avg/peak Mbps** | Bytes counted on successful uploads / elapsed time |
| **Upload routing** | Zone and URLs; warnings highlight possible misrouting |

---

## Example report excerpts

### Throughput line (Box default baseline)

```
Effective 0.714 uploads/s vs Box upload limit 4.000 uploads/s (240/min, 17.9% of cap)
```

Interpretation: The run averaged **0.71 files/s** during the upload phase. Box documents **4 files/s** as the upload API limit for comparison; this run reached about **18%** of that reference — useful when tuning concurrency, not proof you are “under the limit” (the tool does not enforce it).

### Throughput line (no baseline)

```
Effective 0.714 uploads/s (no rate limit baseline; concurrency=30)
```

Set when `rateLimitPerSecond: -1`.

### Routing warning

```
WARNING: Host upload.app.box.com differs from preflight zone host fupload-euc1.app.box.com
```

Investigate zone preflight vs actual upload URLs; mixed hosts can indicate routing or session endpoint issues.

Full sample files:

- [docs/examples/sample-console-output.txt](examples/sample-console-output.txt)
- [docs/examples/sample-routing.json](examples/sample-routing.json)

---

## Related documentation

- [README.md](../README.md) — Build and quick start
- [PRD.md](PRD.md) — Requirements and data model
- [config/config.example.yaml](../config/config.example.yaml) — Annotated template
