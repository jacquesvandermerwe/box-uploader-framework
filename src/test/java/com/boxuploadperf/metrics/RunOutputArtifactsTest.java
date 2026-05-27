package com.boxuploadperf.metrics;

import com.boxuploadperf.charts.HtmlChartReport;
import com.boxuploadperf.charts.RunChartData;
import com.boxuploadperf.config.AppConfig;
import com.boxuploadperf.http.NetworkTiming;
import com.boxuploadperf.config.ThreadMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies a minimal run produces the same on-disk artifacts as {@code box-upload-perf run}.
 */
class RunOutputArtifactsTest {

    @TempDir
    Path temp;

    @Test
    void producesMetricsDbRoutingJsonAndHtmlCharts() throws Exception {
        AppConfig config = new AppConfig();
        config.runId = "run-output-artifacts-test";
        config.runOutputDirectory = temp;
        config.boxClientId = "id";
        config.boxClientSecret = "secret";
        config.boxEnterpriseId = "ent";
        config.boxParentFolderId = "folder";
        config.uploadThreadMode = ThreadMode.VIRTUAL;
        config.uploadFileCount = 1;

        try (MetricsDatabase db = new MetricsDatabase(config.sqlitePath())) {
            db.insertRunStart(config, "TEST", "sha256fixture", 1024);
            db.updateRunUploadZone(config.runId, "upload.box.com",
                    "https://upload.box.com/api/2.0", "https://upload.box.com/2.0/files/content");
            NetworkTiming timing = new NetworkTiming();
            timing.durationMs = 50;
            timing.dnsLookupMs = 2;
            db.insertApiCall(new ApiCallRecord(
                    config.runId, "guid-0", "file-0", 0, ApiPhase.UPLOAD_SIMPLE, null, null, null,
                    "SINGLE_STREAM", false, true, Instant.now(), "POST",
                    "https://upload.box.com/api/2.0/files/content", 201, timing, "VIRTUAL",
                    1, false, null, null, null, null, null));
            db.recordFileUploadOutcome(FileUploadOutcome.success(
                    config.runId, "guid-0", 0, "file-0", "SINGLE_STREAM", 0, 100, 110, 0, false, 1));
            db.insertResourceSample(config.runId, 1000, 10, 1.0, 0, 0, 0, 0, 1000, 0.5, 1);
            db.endRun(config.runId);
        }

        assertTrue(Files.isRegularFile(config.sqlitePath()));

        Path routing = UploadRoutingReport.write(config);
        assertTrue(Files.isRegularFile(routing));
        String routingJson = Files.readString(routing);
        assertTrue(routingJson.contains("upload.box.com"));

        RunChartData chartData = RunChartData.load(config);
        assertNotNull(chartData.report());
        assertTrue(chartData.uploadIndices().contains(0));

        new HtmlChartReport().generate(config);
        Path indexHtml = config.runDirectory().resolve("charts/index.html");
        assertTrue(Files.isRegularFile(indexHtml));
        assertTrue(Files.readString(indexHtml).contains(config.runId));
    }
}
