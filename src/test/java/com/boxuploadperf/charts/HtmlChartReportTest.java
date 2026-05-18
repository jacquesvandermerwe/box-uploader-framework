package com.boxuploadperf.charts;

import com.boxuploadperf.config.AppConfig;
import com.boxuploadperf.config.ThreadMode;
import com.boxuploadperf.metrics.FileUploadOutcome;
import com.boxuploadperf.metrics.MetricsDatabase;
import com.boxuploadperf.metrics.UploadFailureReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlChartReportTest {

    @TempDir
    Path temp;

    @Test
    void chartLabelsAreJsonArraysNotHtml() throws Exception {
        AppConfig config = new AppConfig();
        config.runId = "html-chart-test";
        config.runOutputDirectory = temp;
        config.boxClientId = "id";
        config.boxClientSecret = "secret";
        config.boxEnterpriseId = "ent";
        config.boxParentFolderId = "folder";
        config.uploadThreadMode = ThreadMode.VIRTUAL;
        config.uploadFileCount = 2;

        try (MetricsDatabase db = new MetricsDatabase(config.sqlitePath())) {
            db.insertRunStart(config, "TEST", "sha", 1024);
            db.recordFileUploadOutcome(FileUploadOutcome.success(
                    config.runId, "g1", 0, "file-1", "SINGLE_STREAM", 0, 100, 110, 0, false, 1));
            db.recordFileUploadOutcome(FileUploadOutcome.failure(
                    config.runId, "g2", 1, "SINGLE_STREAM", UploadFailureReason.HTTP_429,
                    "Upload failed: 429", 429, 2, 50));
            db.insertResourceSample(config.runId, 1000, 10, 1.0, 0, 0, 0, 0, 1000, 0.5, 1);
            db.endRun(config.runId);
        }

        new HtmlChartReport().generate(config);
        String html = Files.readString(config.runDirectory().resolve("charts/index.html"));
        assertTrue(html.contains("data:{labels:[\"0\""), () -> "upload chart labels should be JSON array");
        assertTrue(html.contains("Failed uploads"), () -> "failures panel should appear before charts");
        assertFalse(html.contains("data:{labels:<section"), () -> "HTML must not be injected into chart labels");
    }
}
