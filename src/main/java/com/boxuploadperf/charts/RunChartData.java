package com.boxuploadperf.charts;

import com.boxuploadperf.config.AppConfig;
import com.boxuploadperf.metrics.RunReportData;
import com.boxuploadperf.metrics.UploadFailureReport;
import com.boxuploadperf.metrics.UploadRoutingReport;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/** Shared report data for HTML and PDF chart output. */
public record RunChartData(
        RunReportData report,
        UploadRoutingReport.RoutingSummary routing,
        UploadFailureReport.Summary failures,
        List<Integer> uploadIndices,
        List<Double> uploadDurationMs,
        List<Double> resourceElapsedSec,
        List<Double> cpuProcessPct,
        List<Double> appUploadMbps) {

    public static RunChartData load(AppConfig config) throws Exception {
        try (var migrate = new com.boxuploadperf.metrics.MetricsDatabase(config.sqlitePath())) {
            migrate.close();
        }
        List<Integer> uploadIndices = new ArrayList<>();
        List<Double> uploadDurationMs = new ArrayList<>();
        List<Double> resourceElapsedSec = new ArrayList<>();
        List<Double> cpuProcessPct = new ArrayList<>();
        List<Double> appUploadMbps = new ArrayList<>();
        RunReportData report;
        UploadRoutingReport.RoutingSummary routing;
        UploadFailureReport.Summary failures;
        try (Connection conn = DriverManager.getConnection(
                "jdbc:sqlite:" + config.sqlitePath().toAbsolutePath())) {
            report = RunReportData.load(conn, config.runId);
            routing = UploadRoutingReport.load(conn, config.runId);
            int fileCount = report.config() != null ? report.config().fileCount() : 0;
            failures = UploadFailureReport.load(conn, config.runId, fileCount);
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT upload_index, upload_duration_ms FROM file_uploads "
                            + "WHERE run_id = ? AND success = 1 ORDER BY upload_index")) {
                ps.setString(1, config.runId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        uploadIndices.add(rs.getInt(1));
                        uploadDurationMs.add(rs.getDouble(2));
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT elapsed_ms, cpu_process_pct, app_upload_mbps FROM resource_samples "
                            + "WHERE run_id = ? ORDER BY elapsed_ms")) {
                ps.setString(1, config.runId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        resourceElapsedSec.add(rs.getDouble(1) / 1000.0);
                        cpuProcessPct.add(rs.getDouble(2));
                        appUploadMbps.add(rs.getDouble(3));
                    }
                }
            }
        }
        return new RunChartData(report, routing, failures, uploadIndices, uploadDurationMs,
                resourceElapsedSec, cpuProcessPct, appUploadMbps);
    }
}
