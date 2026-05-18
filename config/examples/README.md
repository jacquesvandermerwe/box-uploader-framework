# Example profiles

These YAML files are **templates only** (placeholder credentials). They are safe to commit.

1. Copy a file to `~/.box-upload-perf/profiles/<name>.yaml`
2. Replace `clientId`, `clientSecret`, `enterpriseId`, and `parentFolderId`
3. Run: `java -jar target/box-upload-perf-1.0.0-SNAPSHOT.jar run --profile <name>`

| File | Highlights |
|------|------------|
| `low-concurrency.yaml` | 10 files, concurrency 2 |
| `high-concurrency-virtual.yaml` | 50 files, concurrency 30, `VIRTUAL` |
| `high-concurrency-platform.yaml` | 50 files, `PLATFORM` pool |
| `rate-limit-box-default.yaml` | `rateLimitPerSecond: 0` → 240/min baseline |
| `rate-limit-custom.yaml` | `rateLimitPerSecond: 2.0` |
| `rate-limit-disabled.yaml` | `rateLimitPerSecond: -1` |
| `smoke-test-cleanup.yaml` | 2 small files, delete Box folder after run |
| `chunked-large-payload.yaml` | ~50 MiB chunked uploads |

Full profile reference and report guide: [docs/USER_GUIDE.md](../../docs/USER_GUIDE.md).
