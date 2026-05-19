package com.boxuploadperf.config;

import com.boxuploadperf.box.ImpersonationUsers;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public final class AppConfig {

    /**
     * Box upload API limit per user: 240 uploads/minute.
     * @see <a href="https://developer.box.com/guides/api-calls/permissions-and-errors/rate-limits">Box rate limits</a>
     */
    public static final double BOX_UPLOAD_RATE_LIMIT_PER_MINUTE = 240.0;
    public static final double BOX_DEFAULT_UPLOAD_RATE_LIMIT_PER_SECOND =
            BOX_UPLOAD_RATE_LIMIT_PER_MINUTE / 60.0;

    public String profileName;
    public String profileDescription;

    public String boxClientId;
    public String boxClientSecret;
    public String boxEnterpriseId;
    /** Comma-separated Box user IDs for {@code As-User} impersonation; empty for enterprise-only mode. */
    public String boxUserId;
    public String boxParentFolderId;
    private List<String> impersonationUserIds = List.of();
    public String boxRunFolderName;

    public int uploadFileCount = 100;
    public int uploadConcurrency = 32;
    public ThreadMode uploadThreadMode;
    public Integer uploadPlatformThreadPoolSize;
  /**
   * Profile override (uploads/s). {@code 0} = Box default (240/min).
   * Negative = no rate-limit baseline for this run.
   */
    public double uploadRateLimitPerSecond;
    /** When true, throttle upload starts to {@link #effectiveUploadRateLimitPerSecond()}. */
    public boolean uploadEnforceRateLimit;
    public long uploadChunkedUploadThresholdBytes = 52_428_800L;
    public long uploadChunkSizeBytes = 52_428_800L;

    public int retryMaxAttempts = 3;
    public long retryBackoffMs = 500L;

    public Path workParentDirectory = Path.of("./work");
    public String workPayloadFileName = "payload.pdf";
    /** Skip PDF generation when an existing payload file matches target size. */
    public boolean workReusePayload;

    public long pdfTargetSizeBytes = 1_048_576L;

    public boolean cleanupDeleteBoxRunFolderAfterRun;
    public boolean cleanupDeleteLocalPayloadAfterRun;

    /** Write {@link #reportPdfFileName} under the run directory after each run. */
    public boolean reportGeneratePdf = true;
    /** Upload the PDF report into the Box run folder (requires {@link #reportGeneratePdf}). */
    public boolean reportUploadPdfToBox;
    public String reportPdfFileName = "benchmark-report.pdf";

    public String runId = UUID.randomUUID().toString();

    /** New run identity for SQLite metrics and the Box run folder (not persisted in profiles). */
    public void assignFreshRunIdentity() {
        runId = UUID.randomUUID().toString();
        boxRunFolderName = runId;
    }
    public Path runOutputDirectory = Path.of("./results");
    public String metricsSqliteFileName = "metrics.db";
    public long metricsSampleIntervalMs = 500L;
    public String metricsNetworkInterfaceName;

    public Path profilesDirectory = Path.of(System.getProperty("user.home"), ".box-upload-perf", "profiles");

    public void validate() {
        requireJava21();
        if (isBlank(boxClientId) || isBlank(boxClientSecret)) {
            throw new IllegalArgumentException("box.clientId and box.clientSecret are required (profile or wizard)");
        }
        impersonationUserIds = ImpersonationUsers.parse(boxUserId);
        if (usesImpersonation() && isBlank(boxEnterpriseId)) {
            throw new IllegalArgumentException(
                    "box.enterpriseId is required when box.userId is set (enterprise token + As-User header)");
        }
        if (!usesImpersonation() && isBlank(boxEnterpriseId)) {
            throw new IllegalArgumentException(
                    "box.enterpriseId is required when box.userId is not set (enterprise CCG)");
        }
        if (!isBlank(boxEnterpriseId) && boxEnterpriseId.equals("0")) {
            throw new IllegalArgumentException("box.enterpriseId cannot be 0 (use your Box enterprise numeric ID)");
        }
        if (isBlank(boxParentFolderId)) {
            throw new IllegalArgumentException("box.parentFolderId is required");
        }
        // Box folder name always follows runId so saved profiles cannot reuse a prior folder (409).
        boxRunFolderName = runId;
        if (uploadThreadMode == null) {
            throw new IllegalArgumentException("upload.threadMode is required (VIRTUAL or PLATFORM)");
        }
        if (uploadFileCount < 1) {
            throw new IllegalArgumentException("upload.fileCount must be >= 1");
        }
        if (uploadConcurrency < 1) {
            throw new IllegalArgumentException("upload.concurrency must be >= 1");
        }
        if (pdfTargetSizeBytes < 1) {
            throw new IllegalArgumentException("pdf.targetSizeBytes must be >= 1");
        }
        if (uploadChunkSizeBytes < 1 || uploadChunkedUploadThresholdBytes < 1) {
            throw new IllegalArgumentException("chunk size and threshold must be >= 1");
        }
        if (pdfTargetSizeBytes >= uploadChunkedUploadThresholdBytes && uploadChunkSizeBytes > pdfTargetSizeBytes) {
            throw new IllegalArgumentException("upload.chunkSizeBytes must be <= pdf.targetSizeBytes when using chunked uploads");
        }
        if (retryMaxAttempts < 1) {
            throw new IllegalArgumentException("retry.maxAttempts must be >= 1");
        }
        if (retryBackoffMs < 0) {
            throw new IllegalArgumentException("retry.backoffMs must be >= 0");
        }
        if (uploadEnforceRateLimit && uploadRateLimitDisabled()) {
            throw new IllegalArgumentException(
                    "upload.enforceRateLimit cannot be used with rateLimitPerSecond < 0");
        }
    }

    public boolean usesImpersonation() {
        return !impersonationUserIds.isEmpty();
    }

    public List<String> impersonationUserIds() {
        return impersonationUserIds;
    }

    public boolean shouldEnforceRateLimit() {
        return uploadEnforceRateLimit && !uploadRateLimitDisabled();
    }

    public int platformPoolSize() {
        return uploadPlatformThreadPoolSize != null ? uploadPlatformThreadPoolSize : uploadConcurrency;
    }

    public boolean useChunkedUpload() {
        return pdfTargetSizeBytes >= uploadChunkedUploadThresholdBytes;
    }

    /** {@code true} when {@code upload.rateLimitPerSecond} &lt; 0 (no comparison baseline). */
    public boolean uploadRateLimitDisabled() {
        return uploadRateLimitPerSecond < 0;
    }

    /** {@code true} when {@code upload.rateLimitPerSecond} is a positive profile override. */
    public boolean uploadRateLimitExplicit() {
        return uploadRateLimitPerSecond > 0;
    }

    /** Limit used for throughput comparison: profile value or Box default (240/min). */
    public double effectiveUploadRateLimitPerSecond() {
        if (uploadRateLimitDisabled()) {
            return 0;
        }
        return uploadRateLimitExplicit()
                ? uploadRateLimitPerSecond
                : BOX_DEFAULT_UPLOAD_RATE_LIMIT_PER_SECOND;
    }

    public Path payloadPath() {
        return workParentDirectory.resolve(workPayloadFileName);
    }

    public Path runDirectory() {
        return runOutputDirectory.resolve(runId);
    }

    public Path sqlitePath() {
        return runDirectory().resolve(metricsSqliteFileName);
    }

    public Path reportPdfPath() {
        return runDirectory().resolve(reportPdfFileName);
    }

    public static void requireJava21() {
        int feature = Runtime.version().feature();
        if (feature < 21) {
            throw new IllegalStateException("Java 21 or higher is required (current feature version: " + feature + ")");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
