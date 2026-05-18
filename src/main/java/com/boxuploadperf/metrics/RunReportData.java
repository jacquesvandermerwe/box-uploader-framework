package com.boxuploadperf.metrics;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Configuration and aggregate metrics for HTML / console reports (no per-file detail). */
public record RunReportData(Config config, Metrics metrics) {

    public record Config(
            String runId,
            String profileName,
            String configSource,
            String startedAt,
            String endedAt,
            String threadMode,
            int concurrency,
            int fileCount,
            long payloadBytes,
            long chunkedThresholdBytes,
            long chunkSizeBytes,
            String uploadStrategy,
            String boxParentFolderId,
            String boxRunFolderId,
            String uploadZoneHost,
            String uploadBaseUrl,
            String enterpriseId,
            String impersonationUserId,
            double effectiveRateLimitPerSec,
            boolean rateLimitFromProfile,
            boolean rateLimitDisabled,
            String jvmVersion) {}

    public record Metrics(
            int filesAttempted,
            int filesSucceeded,
            int filesFailed,
            int count429,
            int ancillaryRequests,
            double uploadTimeMinMs,
            double uploadTimeAvgMs,
            double uploadTimeMaxMs,
            double uploadTimeP95Ms,
            double uploadTimeP99Ms,
            double throughputBytesPerSec,
            double throughputFilesPerSec,
            long totalBytesUploaded,
            double runDurationMs,
            double cpuProcessAvgPct,
            double cpuProcessMaxPct,
            Double cpuSystemAvgPct,
            double appUploadMbpsAvg,
            double appUploadMbpsPeak,
            double nicTxMbpsAvg,
            double nicTxMbpsPeak,
            double nicRxMbpsAvg,
            double nicRxMbpsPeak) {}

    public static RunReportData load(Connection conn, String runId) throws Exception {
        Config config = loadConfig(conn, runId);
        Metrics metrics = loadMetrics(conn, runId);
        return new RunReportData(config, metrics);
    }

    private static Config loadConfig(Connection conn, String runId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT r.profile_name, r.config_source, r.started_at, r.ended_at,
                       r.thread_mode, r.concurrency, r.file_count, r.payload_bytes,
                       r.chunked_upload_threshold_bytes, r.chunk_size_bytes,
                       r.box_parent_folder_id, r.box_run_folder_id,
                       r.upload_zone_host, r.upload_base_url, r.jvm_version, r.config_json,
                       s.configured_rate_limit_per_sec, s.rate_limit_explicit
                FROM runs r
                LEFT JOIN run_summaries s ON s.run_id = r.run_id
                WHERE r.run_id = ?
                """)) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                long payload = rs.getLong(8);
                long threshold = rs.getLong(9);
                String strategy = payload >= threshold ? "CHUNKED" : "SINGLE_STREAM";
                String configJson = rs.getString(16);
                Double storedLimit = rs.getDouble(17);
                if (rs.wasNull()) {
                    storedLimit = null;
                }
                Integer rateLimitMode = rs.getInt(18);
                if (rs.wasNull()) {
                    rateLimitMode = null;
                }
                Double profileOverride = parseDoubleField(configJson, "rateLimitPerSecond");
                if (rateLimitMode == null && profileOverride != null && profileOverride < 0) {
                    rateLimitMode = -1;
                }
                boolean disabled = RunSummary.resolveDisabled(rateLimitMode);
                boolean explicit = RunSummary.resolveExplicit(rateLimitMode);
                double effectiveLimit = RunSummary.resolveEffectiveLimit(storedLimit, rateLimitMode);
                return new Config(
                        runId,
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getString(5),
                        rs.getInt(6),
                        rs.getInt(7),
                        payload,
                        threshold,
                        rs.getLong(10),
                        strategy,
                        rs.getString(11),
                        rs.getString(12),
                        rs.getString(13),
                        rs.getString(14),
                        parseField(configJson, "enterpriseId"),
                        parseField(configJson, "userId"),
                        effectiveLimit,
                        explicit,
                        disabled,
                        rs.getString(15));
            }
        }
    }

    private static Metrics loadMetrics(Connection conn, String runId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT files_attempted, files_succeeded, files_failed,
                       count_429, ancillary_request_count,
                       upload_time_min_ms, upload_time_avg_ms, upload_time_max_ms,
                       upload_time_p95_ms, upload_time_p99_ms,
                       throughput_bytes_per_sec, throughput_files_per_sec,
                       total_bytes_uploaded, run_duration_ms,
                       cpu_process_avg_pct, cpu_process_max_pct, cpu_system_avg_pct,
                       app_upload_mbps_avg, app_upload_mbps_peak,
                       nic_tx_mbps_avg, nic_tx_mbps_peak, nic_rx_mbps_avg, nic_rx_mbps_peak
                FROM run_summaries WHERE run_id = ?
                """)) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Double cpuSystem = rs.getDouble(17);
                if (rs.wasNull()) {
                    cpuSystem = null;
                }
                return new Metrics(
                        rs.getInt(1), rs.getInt(2), rs.getInt(3),
                        rs.getInt(4), rs.getInt(5),
                        rs.getDouble(6), rs.getDouble(7), rs.getDouble(8),
                        rs.getDouble(9), rs.getDouble(10),
                        rs.getDouble(11), rs.getDouble(12),
                        rs.getLong(13), rs.getDouble(14),
                        rs.getDouble(15), rs.getDouble(16), cpuSystem,
                        rs.getDouble(18), rs.getDouble(19),
                        rs.getDouble(20), rs.getDouble(21),
                        rs.getDouble(22), rs.getDouble(23));
            }
        }
    }

    private static String parseField(String yaml, String key) {
        if (yaml == null || yaml.isBlank()) {
            return null;
        }
        Matcher m = Pattern.compile("(?m)^\\s*" + key + ":\\s*['\"]?([^'\"\\n]+)['\"]?\\s*$").matcher(yaml);
        if (m.find()) {
            String v = m.group(1).trim();
            return v.isEmpty() ? null : v;
        }
        return null;
    }

    private static Double parseDoubleField(String yaml, String key) {
        String v = parseField(yaml, key);
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            double d = Double.parseDouble(v);
            return d > 0 ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String rateLimitDescription() {
        if (metrics == null || config == null) {
            return "—";
        }
        return new RunSummary(
                metrics.filesSucceeded(),
                metrics.filesFailed(),
                metrics.throughputFilesPerSec(),
                config.effectiveRateLimitPerSec(),
                config.rateLimitFromProfile(),
                config.rateLimitDisabled(),
                config.concurrency(),
                metrics.cpuProcessAvgPct(),
                metrics.cpuProcessMaxPct(),
                metrics.cpuSystemAvgPct(),
                metrics.appUploadMbpsAvg(),
                metrics.appUploadMbpsPeak()
        ).rateLimitDescription();
    }
}
