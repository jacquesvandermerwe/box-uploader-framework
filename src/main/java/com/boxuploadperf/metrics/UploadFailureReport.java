package com.boxuploadperf.metrics;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Failed and interrupted uploads for a run (from {@code file_uploads}). */
public final class UploadFailureReport {

    public record FailedUpload(
            int uploadIndex,
            UploadFailureReason failureReason,
            String errorMessage,
            int lastStatusCode,
            int httpAttempts) {}

    public record Summary(
            int filesAttempted,
            int filesSucceeded,
            int filesFailed,
            int filesNotStarted,
            Map<UploadFailureReason, Integer> failuresByReason,
            List<FailedUpload> failures) {}

    private UploadFailureReport() {}

    public static Summary load(Connection conn, String runId, int filesAttempted) throws Exception {
        List<FailedUpload> failures = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT upload_index, failure_reason, error_message, last_status_code, http_attempts
                FROM file_uploads
                WHERE run_id = ? AND success = 0
                ORDER BY upload_index
                """)) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    failures.add(new FailedUpload(
                            rs.getInt(1),
                            UploadFailureReason.parse(rs.getString(2)),
                            rs.getString(3),
                            rs.getInt(4),
                            rs.getInt(5)));
                }
            }
        }
        int recorded;
        int succeeded;
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT COUNT(*),
                       COALESCE(SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END), 0)
                FROM file_uploads WHERE run_id = ?
                """)) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                recorded = rs.getInt(1);
                succeeded = rs.getInt(2);
            }
        }
        Map<UploadFailureReason, Integer> byReason = new LinkedHashMap<>();
        for (FailedUpload f : failures) {
            byReason.merge(f.failureReason(), 1, Integer::sum);
        }
        int failed = failures.size();
        int notStarted = Math.max(0, filesAttempted - recorded);
        return new Summary(filesAttempted, succeeded, failed, notStarted, byReason, failures);
    }

    public static void printToConsole(Summary summary) {
        if (summary == null) {
            return;
        }
        System.out.println();
        System.out.println("Upload outcomes:");
        System.out.printf("  attempted=%d succeeded=%d failed=%d not_started=%d%n",
                summary.filesAttempted(), summary.filesSucceeded(), summary.filesFailed(),
                summary.filesNotStarted());
        if (summary.filesNotStarted() > 0) {
            System.out.println("  WARNING: some upload tasks never recorded an outcome (interrupted run or executor issue).");
        }
        if (!summary.failuresByReason().isEmpty()) {
            System.out.println("  failures by reason:");
            for (var e : summary.failuresByReason().entrySet()) {
                System.out.printf("    %s: %d%n", e.getKey(), e.getValue());
            }
        }
        int show = Math.min(25, summary.failures().size());
        if (show > 0) {
            System.out.println("  sample failed indices (up to 25):");
            for (int i = 0; i < show; i++) {
                FailedUpload f = summary.failures().get(i);
                System.out.printf("    #%d %s status=%d attempts=%d %s%n",
                        f.uploadIndex(), f.failureReason(), f.lastStatusCode(), f.httpAttempts(),
                        f.errorMessage() == null ? "" : f.errorMessage());
            }
            if (summary.failures().size() > show) {
                System.out.printf("    ... and %d more (use: box-upload-perf failures --run-id ...)%n",
                        summary.failures().size() - show);
            }
        }
    }
}
