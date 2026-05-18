package com.boxuploadperf.cli;

import com.boxuploadperf.config.AppConfig;
import com.boxuploadperf.config.ThreadMode;

/** Applies non-secret CLI flag overrides onto a loaded {@link AppConfig}. */
public final class ConfigOverrides {

    private ConfigOverrides() {}

    public static void apply(
            AppConfig config,
            Integer fileCount,
            Integer concurrency,
            String threadMode,
            Double rateLimitPerSecond,
            Boolean enforceRateLimit,
            Long payloadBytes,
            String runId) {
        if (fileCount != null) {
            config.uploadFileCount = fileCount;
        }
        if (concurrency != null) {
            config.uploadConcurrency = concurrency;
        }
        if (threadMode != null && !threadMode.isBlank()) {
            config.uploadThreadMode = ThreadMode.parse(threadMode);
        }
        if (rateLimitPerSecond != null) {
            config.uploadRateLimitPerSecond = rateLimitPerSecond;
        }
        if (enforceRateLimit != null) {
            config.uploadEnforceRateLimit = enforceRateLimit;
        }
        if (payloadBytes != null) {
            config.pdfTargetSizeBytes = payloadBytes;
        }
        if (runId != null && !runId.isBlank()) {
            config.runId = runId.trim();
            config.boxRunFolderName = config.runId;
        }
    }
}
