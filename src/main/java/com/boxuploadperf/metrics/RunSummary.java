package com.boxuploadperf.metrics;

import com.boxuploadperf.config.AppConfig;

/** Aggregated run outcome for console and HTML reports. */
public record RunSummary(
        int filesSucceeded,
        int filesFailed,
        double throughputFilesPerSec,
        double effectiveRateLimitPerSec,
        boolean rateLimitFromProfile,
        boolean rateLimitComparisonDisabled,
        int configuredConcurrency,
        double cpuProcessAvgPct,
        double cpuProcessMaxPct,
        Double cpuSystemAvgPct,
        double appUploadMbpsAvg,
        double appUploadMbpsPeak) {

    public String rateLimitDescription() {
        if (rateLimitComparisonDisabled) {
            return String.format(
                    "Effective %.3f uploads/s (no rate limit baseline; concurrency=%d)",
                    throughputFilesPerSec, configuredConcurrency);
        }
        double pct = effectiveRateLimitPerSec > 0
                ? (throughputFilesPerSec / effectiveRateLimitPerSec) * 100.0
                : 0;
        if (rateLimitFromProfile) {
            return String.format(
                    "Effective %.3f uploads/s vs configured %.3f uploads/s (%.1f%% of cap)",
                    throughputFilesPerSec, effectiveRateLimitPerSec, pct);
        }
        return String.format(
                "Effective %.3f uploads/s vs Box upload limit %.3f uploads/s (240/min, %.1f%% of cap)",
                throughputFilesPerSec, effectiveRateLimitPerSec, pct);
    }

    /** {@code rateLimitExplicit}: -1 disabled, 0 Box default, 1 profile override. */
    public static double resolveEffectiveLimit(Double storedLimit, Integer rateLimitExplicit) {
        if (rateLimitExplicit != null && rateLimitExplicit == -1) {
            return 0;
        }
        if (rateLimitExplicit != null && rateLimitExplicit == 1 && storedLimit != null && storedLimit > 0) {
            return storedLimit;
        }
        if (storedLimit != null && storedLimit > 0) {
            return storedLimit;
        }
        return AppConfig.BOX_DEFAULT_UPLOAD_RATE_LIMIT_PER_SECOND;
    }

    public static boolean resolveExplicit(Integer rateLimitExplicit) {
        return rateLimitExplicit != null && rateLimitExplicit == 1;
    }

    public static boolean resolveDisabled(Integer rateLimitExplicit) {
        return rateLimitExplicit != null && rateLimitExplicit == -1;
    }
}
