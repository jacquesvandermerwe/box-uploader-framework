package com.boxuploadperf.metrics;

import com.boxuploadperf.http.NetworkTiming;

import java.time.Instant;

public record ApiCallRecord(
        String runId,
        String uploadGuid,
        String boxFileId,
        Integer uploadIndex,
        ApiPhase phase,
        Integer chunkIndex,
        Long chunkOffset,
        Integer chunkLength,
        String uploadStrategy,
        boolean ancillary,
        boolean primaryUpload,
        Instant timestamp,
        String httpMethod,
        String urlTemplate,
        int statusCode,
        NetworkTiming timing,
        String threadMode,
        int attempt,
        boolean rateLimited,
        Integer retryAfterSeconds,
        String errorMessage) {
}
