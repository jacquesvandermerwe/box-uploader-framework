package com.boxuploadperf.metrics;

import com.boxuploadperf.config.AppConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RunSummarizer {

    private RunSummarizer() {}

    public static RunSummary compute(Connection conn, AppConfig config, int attempted, int succeeded, int failed,
                                     long totalBytes, double runDurationMs) throws Exception {
        String runId = config.runId;
        List<Double> durations;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT upload_duration_ms FROM file_uploads WHERE run_id = ? AND success = 1")) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                durations = new ArrayList<>();
                while (rs.next()) {
                    durations.add(rs.getDouble(1));
                }
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
        double retrySleepTotalMs = 0;
        double retrySleepAvgMs = 0;
        int retrySleepCount = 0;
        Double retryAfterAvgSec = null;
        Integer retryAfterMaxSec = null;
        int count429MissingHeader = 0;
        try (PreparedStatement c = conn.prepareStatement(
                "SELECT COUNT(*) FROM api_calls WHERE run_id = ? AND rate_limited = 1")) {
            c.setString(1, runId);
            try (ResultSet rs = c.executeQuery()) {
                rs.next();
                count429 = rs.getInt(1);
            }
        }
        try (PreparedStatement c = conn.prepareStatement("""
                SELECT COALESCE(SUM(retry_sleep_ms), 0), COALESCE(AVG(retry_sleep_ms), 0), COUNT(retry_sleep_ms)
                FROM api_calls WHERE run_id = ? AND retry_sleep_ms IS NOT NULL
                """)) {
            c.setString(1, runId);
            try (ResultSet rs = c.executeQuery()) {
                if (rs.next()) {
                    retrySleepTotalMs = rs.getDouble(1);
                    retrySleepAvgMs = rs.getDouble(2);
                    retrySleepCount = rs.getInt(3);
                }
            }
        }
        try (PreparedStatement c = conn.prepareStatement("""
                SELECT AVG(retry_after_seconds), MAX(retry_after_seconds)
                FROM api_calls WHERE run_id = ? AND rate_limited = 1 AND retry_after_seconds IS NOT NULL
                """)) {
            c.setString(1, runId);
            try (ResultSet rs = c.executeQuery()) {
                if (rs.next()) {
                    double headerAvg = rs.getDouble(1);
                    if (!rs.wasNull()) {
                        retryAfterAvgSec = headerAvg;
                    }
                    int headerMax = rs.getInt(2);
                    if (!rs.wasNull()) {
                        retryAfterMaxSec = headerMax;
                    }
                }
            }
        }
        try (PreparedStatement c = conn.prepareStatement("""
                SELECT COUNT(*) FROM api_calls
                WHERE run_id = ? AND rate_limited = 1 AND retry_after_seconds IS NULL
                """)) {
            c.setString(1, runId);
            try (ResultSet rs = c.executeQuery()) {
                rs.next();
                count429MissingHeader = rs.getInt(1);
            }
        }
        try (PreparedStatement c = conn.prepareStatement(
                "SELECT COUNT(*) FROM api_calls WHERE run_id = ? AND is_ancillary = 1")) {
            c.setString(1, runId);
            try (ResultSet rs = c.executeQuery()) {
                rs.next();
                ancillary = rs.getInt(1);
            }
        }

        double throughputBytes = runDurationMs > 0 ? totalBytes / (runDurationMs / 1000.0) : 0;
        double throughputFiles = runDurationMs > 0 ? succeeded / (runDurationMs / 1000.0) : 0;

        double cpuAvg = 0;
        double cpuMax = 0;
        Double cpuSystemAvg = null;
        double appMbpsAvg = 0;
        double appMbpsPeak = 0;
        double nicTxAvg = 0;
        double nicTxPeak = 0;
        double nicRxAvg = 0;
        double nicRxPeak = 0;
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT
                  AVG(cpu_process_pct), MAX(cpu_process_pct), AVG(cpu_system_pct),
                  AVG(app_upload_mbps), MAX(app_upload_mbps),
                  AVG(nic_tx_mbps), MAX(nic_tx_mbps), AVG(nic_rx_mbps), MAX(nic_rx_mbps)
                FROM resource_samples WHERE run_id = ?
                """)) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    cpuAvg = rs.getDouble(1);
                    cpuMax = rs.getDouble(2);
                    double sys = rs.getDouble(3);
                    if (!rs.wasNull()) {
                        cpuSystemAvg = sys;
                    }
                    appMbpsAvg = rs.getDouble(4);
                    appMbpsPeak = rs.getDouble(5);
                    nicTxAvg = rs.getDouble(6);
                    nicTxPeak = rs.getDouble(7);
                    nicRxAvg = rs.getDouble(8);
                    nicRxPeak = rs.getDouble(9);
                }
            }
        }

        boolean rateLimitDisabled = config.uploadRateLimitDisabled();
        double effectiveRateLimit = config.effectiveUploadRateLimitPerSecond();
        boolean rateLimitExplicit = config.uploadRateLimitExplicit();
        int rateLimitMode = rateLimitDisabled ? -1 : (rateLimitExplicit ? 1 : 0);
        RunSummary.RetryBackoffSummary backoff = new RunSummary.RetryBackoffSummary(
                retrySleepCount, retrySleepTotalMs, retrySleepAvgMs,
                retryAfterAvgSec, retryAfterMaxSec, count429MissingHeader);

        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT OR REPLACE INTO run_summaries (
                  run_id, files_attempted, files_succeeded, files_failed,
                  upload_time_min_ms, upload_time_avg_ms, upload_time_max_ms,
                  upload_time_p95_ms, upload_time_p99_ms,
                  ancillary_request_count, count_429,
                  throughput_bytes_per_sec, throughput_files_per_sec, total_bytes_uploaded, run_duration_ms,
                  cpu_process_avg_pct, cpu_process_max_pct, cpu_system_avg_pct,
                  app_upload_mbps_avg, app_upload_mbps_peak,
                  nic_tx_mbps_avg, nic_tx_mbps_peak, nic_rx_mbps_avg, nic_rx_mbps_peak,
                  configured_rate_limit_per_sec, configured_concurrency, rate_limit_explicit,
                  retry_sleep_total_ms, retry_sleep_avg_ms, retry_sleep_count,
                  retry_after_avg_sec, retry_after_max_sec, retry_429_missing_header_count
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
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
                ps.setNull(i++, Types.REAL);
            }
            ps.setDouble(i++, appMbpsAvg);
            ps.setDouble(i++, appMbpsPeak);
            ps.setDouble(i++, nicTxAvg);
            ps.setDouble(i++, nicTxPeak);
            ps.setDouble(i++, nicRxAvg);
            ps.setDouble(i++, nicRxPeak);
            if (rateLimitDisabled) {
                ps.setNull(i++, Types.REAL);
            } else {
                ps.setDouble(i++, effectiveRateLimit);
            }
            ps.setInt(i++, config.uploadConcurrency);
            ps.setInt(i++, rateLimitMode);
            ps.setDouble(i++, retrySleepTotalMs);
            ps.setDouble(i++, retrySleepAvgMs);
            ps.setInt(i++, retrySleepCount);
            if (retryAfterAvgSec != null) {
                ps.setDouble(i++, retryAfterAvgSec);
            } else {
                ps.setNull(i++, Types.REAL);
            }
            if (retryAfterMaxSec != null) {
                ps.setInt(i++, retryAfterMaxSec);
            } else {
                ps.setNull(i++, Types.INTEGER);
            }
            ps.setInt(i++, count429MissingHeader);
            ps.executeUpdate();
            conn.commit();
        }

        return new RunSummary(succeeded, failed, throughputFiles, effectiveRateLimit, rateLimitExplicit,
                rateLimitDisabled, config.uploadConcurrency, cpuAvg, cpuMax, cpuSystemAvg, appMbpsAvg, appMbpsPeak,
                backoff);
    }

    public static RunSummary load(Connection conn, String runId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT files_succeeded, files_failed, throughput_files_per_sec,
                       configured_rate_limit_per_sec, configured_concurrency, rate_limit_explicit,
                       cpu_process_avg_pct, cpu_process_max_pct, cpu_system_avg_pct,
                       app_upload_mbps_avg, app_upload_mbps_peak
                FROM run_summaries WHERE run_id = ?
                """)) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Double storedLimit = rs.getDouble(4);
                if (rs.wasNull()) {
                    storedLimit = null;
                }
                Integer rateLimitMode = rs.getInt(6);
                if (rs.wasNull()) {
                    rateLimitMode = null;
                }
                boolean disabled = RunSummary.resolveDisabled(rateLimitMode);
                double effectiveLimit = RunSummary.resolveEffectiveLimit(storedLimit, rateLimitMode);
                boolean explicit = RunSummary.resolveExplicit(rateLimitMode);
                Double cpuSystem = rs.getDouble(9);
                if (rs.wasNull()) {
                    cpuSystem = null;
                }
                return new RunSummary(
                        rs.getInt(1), rs.getInt(2), rs.getDouble(3),
                        effectiveLimit, explicit, disabled, rs.getInt(5),
                        rs.getDouble(7), rs.getDouble(8), cpuSystem,
                        rs.getDouble(10), rs.getDouble(11),
                        RunSummary.RetryBackoffSummary.none());
            }
        }
    }

    private static double percentile(List<Double> sorted, int pct) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int idx = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }
}
