# Box Upload Performance Framework

Local Java 21+ benchmark harness for measuring Box.com upload throughput, latency, and network behavior. See [docs/PRD.md](docs/PRD.md).

## Build

```bash
mvn -q package
java -jar target/box-upload-perf-1.0.0-SNAPSHOT.jar --help
```

## Quick start

### Interactive wizard (first run)

```bash
java -jar target/box-upload-perf-1.0.0-SNAPSHOT.jar run
```

Enter Box CCG credentials, folder ID, and workload settings. Save a named profile for repeat runs.

### Repeatable profile

```bash
java -jar target/box-upload-perf-1.0.0-SNAPSHOT.jar run --profile my-benchmark
```

Profiles are stored in `~/.box-upload-perf/profiles/` (includes `clientId` and `clientSecret`).

### Config file

```bash
cp config/config.example.yaml ~/.box-upload-perf/profiles/staging.yaml
# edit credentials and settings
java -jar target/box-upload-perf-1.0.0-SNAPSHOT.jar run --profile staging
```

## Output

Each run writes to `./results/<runId>/`:

- `metrics.db` — SQLite (api_calls, file_uploads, run_summaries, resource_samples)
- `charts/index.html` — performance charts

## Commands

| Command | Description |
|---------|-------------|
| `run` | Execute benchmark (`--profile` or `--config`, or wizard) |
| `wizard` | Setup wizard only (`--save-only` optional) |
| `profile list` | List saved profiles |
| `profile show <name>` | Show profile (secret redacted) |
| `profile delete <name>` | Delete profile |

## Requirements

- Java 21+
- Box CCG app with upload scope
- Valid parent folder ID on Box
