package com.boxuploadperf.charts;

import com.boxuploadperf.config.AppConfig;
import com.boxuploadperf.config.ThreadMode;
import com.boxuploadperf.metrics.MetricsDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfChartReportTest {

    @TempDir
    Path temp;

    @Test
    void generatesPdfReportFile() throws Exception {
        AppConfig config = new AppConfig();
        config.runId = "pdf-test-run";
        config.runOutputDirectory = temp;
        config.boxClientId = "id";
        config.boxClientSecret = "secret";
        config.boxEnterpriseId = "ent";
        config.boxParentFolderId = "folder";
        config.uploadThreadMode = ThreadMode.VIRTUAL;
        config.uploadFileCount = 1;
        config.reportGeneratePdf = true;
        config.reportPdfFileName = "benchmark-report.pdf";

        try (MetricsDatabase db = new MetricsDatabase(config.sqlitePath())) {
            db.insertRunStart(config, "TEST", "sha", 1024);
            db.endRun(config.runId);
        }

        Path pdf = new PdfChartReport().generate(config);
        assertTrue(Files.isRegularFile(pdf));
        assertTrue(Files.size(pdf) > 2_000, "PDF should contain multiple pages");
    }
}
