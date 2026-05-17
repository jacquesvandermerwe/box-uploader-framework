package com.boxuploadperf.metrics;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Collections;
import java.util.List;

public final class RunSummarizer {

    private RunSummarizer() {}

    public static void compute(Connection conn, String runId, int attempted, int succeeded, int failed,
                               long totalBytes, double runDurationMs,
                               double cpuAvg, double cpuMax, Double cpuSystemAvg,
                               double appMbpsAvg, double appMbpsPeak,
                               double nicTxAvg, double nicTxPeak, double nicRxAvg, double nicRxPeak) throws Exception {
        List<Double> durations;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT upload_duration_ms FROM file_uploads WHERE run_id = ? AND success = 1")) {
            ps.setString(1, runId);
            var rs = ps.executeQuery();
            durations = new java.util.ArrayList<>();
            while (rs.next()) {
                durations.add(rs.getDouble(1));
            }
        }
        Collections.sort(durations);
        double min = durations.isEmpty() ? 0 : durations.getFirst();
        double max = durations.isEmpty() ? 0 : durations.getLast();
        double avg = durations.stream().mapToDouble(d -> d).average().orElse(0);
        double p95 = percentile(durations, 95);
        double p99 = percentile(durations, 99);

        int count429;
        int ancillary;
        try (PreparedStatement c = conn.prepareStatement("SELECT COUNT(*) FROM api_calls WHERE run_id = ? AND rate_limited = 1")) {
            c.setString(1, runId);
            var rs = c.executeQuery();
            rs.next();
            count429 = rs.getInt(1);
        }
        try (PreparedStatement c = conn.prepareStatement("SELECT COUNT(*) FROM api_calls WHERE run_id = ? AND is_ancillary = 1")) {
            c.setString(1, runId);
            var rs = c.executeQuery();
            rs.next();
            ancillary = rs.getInt(1);
        }

        double throughputBytes = runDurationMs > 0 ? totalBytes / (runDurationMs / 1000.0) : 0;
        double throughputFiles = runDurationMs > 0 ? succeeded / (runDurationMs / 1000.0) : 0;

        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT OR REPLACE INTO run_summaries (
                  run_id, files_attempted, files_succeeded, files_failed,
                  upload_time_min_ms, upload_time_avg_ms, upload_time_max_ms,
                  upload_time_p95_ms, upload_time_p99_ms,
                  ancillary_request_count, count_429,
                  throughput_bytes_per_sec, throughput_files_per_sec, total_bytes_uploaded, run_duration_ms,
                  cpu_process_avg_pct, cpu_process_max_pct, cpu_system_avg_pct,
                  app_upload_mbps_avg, app_upload_mbps_peak,
                  nic_tx_mbps_avg, nic_tx_mbps_peak, nic_rx_mbps_avg, nic_rx_mbps_peak
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            int i = 1;
            ps.setString(i++, runId);
            ps.setInt(i++, attempted);
            ps.setInt(i++, succeeded);
            ps.setInt(i++, failed);
            ps.setDouble(i++, min);
            ps.setDouble(i++, avg);
            ps.setDouble(i++, max);
            ps.setDouble(i++, p95);
            ps.setDouble(i++, p99);
            ps.setInt(i++, ancillary);
            ps.setInt(i++, count429);
            ps.setDouble(i++, throughputBytes);
            ps.setDouble(i++, throughputFiles);
            ps.setLong(i++, totalBytes);
            ps.setDouble(i++, runDurationMs);
            ps.setDouble(i++, cpuAvg);
            ps.setDouble(i++, cpuMax);
            if (cpuSystemAvg != null) {
                ps.setDouble(i++, cpuSystemAvg);
            } else {
                ps.setNull(i++, java.sql.Types.REAL);
            }
            ps.setDouble(i++, appMbpsAvg);
            ps.setDouble(i++, appMbpsPeak);
            ps.setDouble(i++, nicTxAvg);
            ps.setDouble(i++, nicTxPeak);
            ps.setDouble(i++, nicRxAvg);
            ps.setDouble(i++, nicRxPeak);
            ps.executeUpdate();
            conn.commit();
        }
    }

    private static double percentile(List<Double> sorted, int pct) {
        if (sorted.isEmpty()) return 0;
        int idx = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }
}
