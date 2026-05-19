package com.boxuploadperf.metrics;

import com.boxuploadperf.config.AppConfig;
import com.boxuploadperf.config.ThreadMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsDatabaseOutcomeTest {

    @TempDir
    Path temp;

    @Test
    void recordsFailedOutcomeAndCommitsApiCallIndependently() throws Exception {
        AppConfig config = new AppConfig();
        config.runId = "outcome-test";
        config.runOutputDirectory = temp;
        config.boxClientId = "id";
        config.boxClientSecret = "secret";
        config.boxEnterpriseId = "ent";
        config.boxParentFolderId = "folder";
        config.uploadThreadMode = ThreadMode.VIRTUAL;

        try (MetricsDatabase db = new MetricsDatabase(config.sqlitePath())) {
            db.insertRunStart(config, "TEST", "sha", 100);
            db.insertApiCall(new ApiCallRecord(
                    config.runId, "guid-1", null, 3, ApiPhase.UPLOAD_SIMPLE, null, null, null,
                    "SINGLE_STREAM", false, true, java.time.Instant.now(), "POST", "https://upload.example/files",
                    0, new com.boxuploadperf.http.NetworkTiming(), "VIRTUAL", 1, false, null,
                    "IOException: timeout", null, null, null));
            db.recordFileUploadOutcome(FileUploadOutcome.failure(
                    config.runId, "guid-1", 3, "SINGLE_STREAM",
                    UploadFailureReason.NETWORK, "IOException: timeout", 0, 1, 12.5));
        }

        try (var conn = DriverManager.getConnection("jdbc:sqlite:" + config.sqlitePath().toAbsolutePath())) {
            try (var ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM api_calls WHERE run_id = ? AND upload_index = 3")) {
                ps.setString(1, config.runId);
                try (var rs = ps.executeQuery()) {
                    rs.next();
                    assertEquals(1, rs.getInt(1));
                }
            }
            try (var ps = conn.prepareStatement(
                    "SELECT success, failure_reason FROM file_uploads WHERE upload_index = 3")) {
                try (var rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt(1));
                    assertEquals("NETWORK", rs.getString(2));
                }
            }
        }
    }
}
