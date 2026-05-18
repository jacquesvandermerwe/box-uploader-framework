package com.boxuploadperf.metrics;

/** One row in {@code file_uploads} for a terminal upload outcome (success or failure). */
public record FileUploadOutcome(
        String runId,
        String uploadGuid,
        int uploadIndex,
        boolean success,
        String boxFileId,
        String uploadStrategy,
        int chunkCount,
        double uploadDurationMs,
        double endToEndMs,
        int ancillaryCallCount,
        boolean had429,
        UploadFailureReason failureReason,
        String errorMessage,
        int lastStatusCode,
        int httpAttempts) {

    public static FileUploadOutcome success(
            String runId,
            String uploadGuid,
            int uploadIndex,
            String boxFileId,
            String strategy,
            int chunkCount,
            double uploadDurationMs,
            double e2eMs,
            int ancillaryCount,
            boolean had429,
            int httpAttempts) {
        return new FileUploadOutcome(
                runId, uploadGuid, uploadIndex, true, boxFileId, strategy, chunkCount,
                uploadDurationMs, e2eMs, ancillaryCount, had429,
                null, null, 201, httpAttempts);
    }

    public static FileUploadOutcome failure(
            String runId,
            String uploadGuid,
            int uploadIndex,
            String strategy,
            UploadFailureReason reason,
            String errorMessage,
            int lastStatusCode,
            int httpAttempts,
            double e2eMs) {
        return new FileUploadOutcome(
                runId, uploadGuid, uploadIndex, false, null, strategy, 0,
                0, e2eMs, 0, reason == UploadFailureReason.HTTP_429,
                reason, errorMessage, lastStatusCode, httpAttempts);
    }
}
