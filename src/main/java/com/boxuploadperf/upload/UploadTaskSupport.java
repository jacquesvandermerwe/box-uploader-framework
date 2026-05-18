package com.boxuploadperf.upload;

import com.boxuploadperf.box.BoxClient;
import com.boxuploadperf.config.AppConfig;
import com.boxuploadperf.metrics.FileUploadOutcome;
import com.boxuploadperf.metrics.MetricsDatabase;
import com.boxuploadperf.metrics.UploadFailureClassifier;
import com.boxuploadperf.metrics.UploadFailureReason;

/** Persists per-upload outcomes and classifies failures (used by {@link BenchmarkRunner}). */
final class UploadTaskSupport {

    private UploadTaskSupport() {}

    static void recordSuccess(
            MetricsDatabase db,
            AppConfig config,
            BoxClient box,
            String uploadGuid,
            int uploadIndex,
            BoxClient.UploadResult result,
            boolean chunked)
            throws Exception {
        BoxClient.UploadAttemptStats stats = box.uploadAttemptStats();
        boolean had429 = result.had429() || stats.saw429();
        db.recordFileUploadOutcome(FileUploadOutcome.success(
                config.runId,
                uploadGuid,
                uploadIndex,
                result.boxFileId(),
                chunked ? "CHUNKED" : "SINGLE_STREAM",
                chunked ? result.parts() : 0,
                result.uploadDurationMs(),
                result.endToEndMs(),
                result.ancillaryCalls(),
                had429,
                stats.httpAttempts()));
    }

    static void recordFailure(
            MetricsDatabase db,
            AppConfig config,
            BoxClient box,
            String uploadGuid,
            int uploadIndex,
            boolean chunked,
            Throwable error,
            double e2eMs)
            throws Exception {
        BoxClient.UploadAttemptStats stats = box.uploadAttemptStats();
        UploadFailureReason reason = UploadFailureClassifier.classify(error, stats.lastStatusCode(), stats.saw429());
        db.recordFileUploadOutcome(FileUploadOutcome.failure(
                config.runId,
                uploadGuid,
                uploadIndex,
                chunked ? "CHUNKED" : "SINGLE_STREAM",
                reason,
                UploadFailureClassifier.truncateMessage(error),
                stats.lastStatusCode(),
                stats.httpAttempts(),
                e2eMs));
    }

    static void recordInterrupted(
            MetricsDatabase db,
            AppConfig config,
            String uploadGuid,
            int uploadIndex,
            boolean chunked,
            String stage)
            throws Exception {
        db.recordFileUploadOutcome(FileUploadOutcome.failure(
                config.runId,
                uploadGuid,
                uploadIndex,
                chunked ? "CHUNKED" : "SINGLE_STREAM",
                UploadFailureReason.INTERRUPTED,
                "Interrupted while " + stage,
                0,
                0,
                0));
    }
}
