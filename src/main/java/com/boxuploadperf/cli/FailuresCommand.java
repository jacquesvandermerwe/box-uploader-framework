package com.boxuploadperf.cli;

import com.boxuploadperf.config.AppConfig;
import com.boxuploadperf.metrics.UploadFailureReport;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Locale;

@Command(name = "failures", description = "List failed uploads and reasons for a run (from metrics.db)")
public class FailuresCommand implements Runnable {

    @Option(names = "--run-id", required = true, description = "Run UUID (results/<run-id>/ directory)")
    String runId;

    @Option(names = "--results-dir", defaultValue = "./results", description = "Base results directory")
    Path resultsDir;

    @Option(names = "--limit", defaultValue = "100", description = "Max failed rows to print (0 = all)")
    int limit;

    @Option(names = "--verbose", description = "Print every failed upload index")
    boolean verbose;

    @Override
    public void run() {
        try {
            AppConfig.requireJava21();
            Path runDir = resultsDir.resolve(runId.trim());
            Path dbPath = runDir.resolve("metrics.db");
            if (!Files.isRegularFile(dbPath)) {
                System.err.println("No metrics.db at: " + dbPath.toAbsolutePath());
                System.exit(1);
                return;
            }
            int fileCount = readFileCount(dbPath, runId.trim());
            try (var conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath())) {
                UploadFailureReport.Summary summary = UploadFailureReport.load(conn, runId.trim(), fileCount);
                printReport(summary);
            }
        } catch (Exception e) {
            System.err.println("Failures report failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static int readFileCount(Path dbPath, String runId) throws Exception {
        try (var conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
                var ps = conn.prepareStatement("SELECT file_count FROM runs WHERE run_id = ?")) {
            ps.setString(1, runId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    private void printReport(UploadFailureReport.Summary summary) {
        System.out.printf(Locale.US, "Run outcomes: attempted=%d succeeded=%d failed=%d not_started=%d%n",
                summary.filesAttempted(), summary.filesSucceeded(), summary.filesFailed(),
                summary.filesNotStarted());
        if (summary.filesNotStarted() > 0) {
            System.out.println("WARNING: not_started > 0 means some upload tasks never wrote an outcome row.");
        }
        if (summary.failuresByReason().isEmpty()) {
            System.out.println("No failed uploads recorded in file_uploads.");
            return;
        }
        System.out.println();
        System.out.println("Failures by reason:");
        for (var e : summary.failuresByReason().entrySet()) {
            System.out.printf("  %-12s %d%n", e.getKey(), e.getValue());
        }
        if (!verbose && limit > 0 && summary.failures().size() > limit) {
            System.out.printf("%n(Showing first %d of %d; use --verbose or --limit 0 for full list)%n",
                    limit, summary.failures().size());
        }
        int max = verbose || limit <= 0 ? summary.failures().size() : Math.min(limit, summary.failures().size());
        if (max > 0) {
            System.out.println();
            System.out.println("Index  Reason       Status  Attempts  Message");
            for (int i = 0; i < max; i++) {
                var f = summary.failures().get(i);
                System.out.printf(Locale.US, "%5d  %-12s %6d  %8d  %s%n",
                        f.uploadIndex(), f.failureReason(), f.lastStatusCode(), f.httpAttempts(),
                        f.errorMessage() == null ? "" : f.errorMessage());
            }
        }
    }
}
