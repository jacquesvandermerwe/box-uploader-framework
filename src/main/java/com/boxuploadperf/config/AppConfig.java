package com.boxuploadperf.config;

import java.nio.file.Path;
import java.util.UUID;

public final class AppConfig {

    public String profileName;
    public String profileDescription;

    public String boxClientId;
    public String boxClientSecret;
    public String boxEnterpriseId;
    public String boxUserId;
    public String boxParentFolderId;
    public String boxRunFolderName;

    public int uploadFileCount = 100;
    public int uploadConcurrency = 32;
    public ThreadMode uploadThreadMode;
    public Integer uploadPlatformThreadPoolSize;
    public double uploadRateLimitPerSecond;
    public int uploadBucketSize;
    public long uploadBucketDelayMs;
    public long uploadChunkedUploadThresholdBytes = 52_428_800L;
    public long uploadChunkSizeBytes = 52_428_800L;

    public Path workParentDirectory = Path.of("./work");
    public String workPayloadFileName = "payload.pdf";

    public long pdfTargetSizeBytes = 1_048_576L;

    public boolean cleanupDeleteBoxRunFolderAfterRun;
    public boolean cleanupDeleteLocalPayloadAfterRun;

    public String runId = UUID.randomUUID().toString();
    public Path runOutputDirectory = Path.of("./results");
    public String metricsSqliteFileName = "metrics.db";
    public long metricsSampleIntervalMs = 500L;
    public String metricsNetworkInterfaceName;

    public int retryMaxAttempts = 3;
    public long retryBackoffMs = 500L;

    public Path profilesDirectory = Path.of(System.getProperty("user.home"), ".box-upload-perf", "profiles");

    public void validate() {
        requireJava21();
        if (isBlank(boxClientId) || isBlank(boxClientSecret) || isBlank(boxEnterpriseId)) {
            throw new IllegalArgumentException("box.clientId, box.clientSecret, and box.enterpriseId are required in profile or wizard");
        }
        if (isBlank(boxParentFolderId)) {
            throw new IllegalArgumentException("box.parentFolderId is required");
        }
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
    }

    public int platformPoolSize() {
        return uploadPlatformThreadPoolSize != null ? uploadPlatformThreadPoolSize : uploadConcurrency;
    }

    public boolean useChunkedUpload() {
        return pdfTargetSizeBytes >= uploadChunkedUploadThresholdBytes;
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
