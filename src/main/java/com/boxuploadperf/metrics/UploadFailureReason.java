package com.boxuploadperf.metrics;

/** Terminal outcome for a benchmark file upload (persisted on {@code file_uploads}). */
public enum UploadFailureReason {
    HTTP_429,
    HTTP_4XX,
    HTTP_5XX,
    NETWORK,
    INTERRUPTED,
    LOCAL_IO,
    METRICS_DB,
    UNKNOWN;

    public static UploadFailureReason parse(String stored) {
        if (stored == null || stored.isBlank()) {
            return UNKNOWN;
        }
        try {
            return UploadFailureReason.valueOf(stored);
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
