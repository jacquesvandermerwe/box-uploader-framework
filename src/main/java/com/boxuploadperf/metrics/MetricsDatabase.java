package com.boxuploadperf.metrics;

import com.boxuploadperf.config.AppConfig;
import com.boxuploadperf.http.NetworkTiming;
import com.boxuploadperf.config.ProfileStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

public final class MetricsDatabase implements AutoCloseable {

    private final Object writeLock = new Object();
    private final Connection connection;
    private final PreparedStatement insertApiCall;
    public MetricsDatabase(Path sqlitePath) throws SQLException {
        if (sqlitePath.getParent() != null) {
            try {
                Files.createDirectories(sqlitePath.getParent());
            } catch (IOException e) {
                throw new SQLException("Could not create metrics directory", e);
            }
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + sqlitePath.toAbsolutePath());
        connection.setAutoCommit(false);
        initSchema();
        insertApiCall = connection.prepareStatement("""
                INSERT INTO api_calls (
                  run_id, upload_guid, box_file_id, upload_index, phase, chunk_index, chunk_offset, chunk_length,
                  upload_strategy, is_ancillary, is_primary_upload, timestamp, http_method, url_template,
                  status_code, duration_ms, request_bytes, response_bytes, upload_mbps, thread_mode, attempt,
                  rate_limited, retry_after_seconds, error_message,
                  retry_sleep_ms, retry_delay_source,
                  dns_lookup_ms, tcp_connect_ms, tls_handshake_ms, time_to_first_byte_ms, transfer_ms,
                  connection_reused, total_network_ms
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """);
    }

    private void initSchema() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS runs (
                      run_id TEXT PRIMARY KEY,
                      profile_name TEXT,
                      config_source TEXT,
                      started_at TEXT,
                      ended_at TEXT,
                      box_parent_folder_id TEXT,
                      box_run_folder_id TEXT,
                      box_run_folder_name TEXT,
                      thread_mode TEXT,
                      concurrency INTEGER,
                      file_count INTEGER,
                      payload_bytes INTEGER,
                      payload_sha256 TEXT,
                      chunked_upload_threshold_bytes INTEGER,
                      chunk_size_bytes INTEGER,
                      jvm_version TEXT,
                      config_json TEXT
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS api_calls (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      run_id TEXT,
                      upload_guid TEXT,
                      box_file_id TEXT,
                      upload_index INTEGER,
                      phase TEXT,
                      chunk_index INTEGER,
                      chunk_offset INTEGER,
                      chunk_length INTEGER,
                      upload_strategy TEXT,
                      is_ancillary INTEGER,
                      is_primary_upload INTEGER,
                      timestamp TEXT,
                      http_method TEXT,
                      url_template TEXT,
                      status_code INTEGER,
                      duration_ms REAL,
                      request_bytes INTEGER,
                      response_bytes INTEGER,
                      upload_mbps REAL,
                      thread_mode TEXT,
                      attempt INTEGER,
                      rate_limited INTEGER,
                      retry_after_seconds INTEGER,
                      error_message TEXT,
                      dns_lookup_ms REAL,
                      tcp_connect_ms REAL,
                      tls_handshake_ms REAL,
                      time_to_first_byte_ms REAL,
                      transfer_ms REAL,
                      connection_reused INTEGER,
                      total_network_ms REAL
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS file_uploads (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      run_id TEXT,
                      upload_guid TEXT UNIQUE,
                      upload_index INTEGER,
                      box_file_id TEXT,
                      upload_strategy TEXT,
                      chunk_count INTEGER,
                      success INTEGER,
                      upload_duration_ms REAL,
                      end_to_end_duration_ms REAL,
                      ancillary_call_count INTEGER,
                      had_429 INTEGER
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS run_summaries (
                      run_id TEXT PRIMARY KEY,
                      files_attempted INTEGER,
                      files_succeeded INTEGER,
                      files_failed INTEGER,
                      upload_time_min_ms REAL,
                      upload_time_avg_ms REAL,
                      upload_time_max_ms REAL,
                      upload_time_p95_ms REAL,
                      upload_time_p99_ms REAL,
                      ancillary_request_count INTEGER,
                      count_429 INTEGER,
                      throughput_bytes_per_sec REAL,
                      throughput_files_per_sec REAL,
                      total_bytes_uploaded INTEGER,
                      run_duration_ms REAL,
                      cpu_process_avg_pct REAL,
                      cpu_process_max_pct REAL,
                      cpu_system_avg_pct REAL,
                      app_upload_mbps_avg REAL,
                      app_upload_mbps_peak REAL,
                      nic_tx_mbps_avg REAL,
                      nic_tx_mbps_peak REAL,
                      nic_rx_mbps_avg REAL,
                      nic_rx_mbps_peak REAL
                    )
                    """);
            ensureRunsColumn(st, "upload_zone_host", "TEXT");
            ensureRunsColumn(st, "upload_base_url", "TEXT");
            ensureRunsColumn(st, "preflight_upload_url", "TEXT");
            ensureSummaryColumn(st, "configured_rate_limit_per_sec", "REAL");
            ensureSummaryColumn(st, "configured_concurrency", "INTEGER");
            ensureSummaryColumn(st, "rate_limit_explicit", "INTEGER");
            ensureApiCallsColumn(st, "retry_sleep_ms", "REAL");
            ensureApiCallsColumn(st, "retry_delay_source", "TEXT");
            ensureSummaryColumn(st, "retry_sleep_total_ms", "REAL");
            ensureSummaryColumn(st, "retry_sleep_avg_ms", "REAL");
            ensureSummaryColumn(st, "retry_sleep_count", "INTEGER");
            ensureSummaryColumn(st, "retry_after_avg_sec", "REAL");
            ensureSummaryColumn(st, "retry_after_max_sec", "INTEGER");
            ensureSummaryColumn(st, "retry_429_missing_header_count", "INTEGER");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS resource_samples (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      run_id TEXT,
                      timestamp TEXT,
                      elapsed_ms REAL,
                      cpu_process_pct REAL,
                      cpu_system_pct REAL,
                      nic_bytes_in_delta INTEGER,
                      nic_bytes_out_delta INTEGER,
                      nic_rx_mbps REAL,
                      nic_tx_mbps REAL,
                      app_bytes_uploaded_delta INTEGER,
                      app_upload_mbps REAL,
                      in_flight_uploads INTEGER
                    )
                    """);
            ensureIndexes(st);
        }
        connection.commit();
    }

    /**
     * Indexes for per-run aggregation (summaries, charts, routing report).
     * {@code runs.run_id} and {@code run_summaries.run_id} are PRIMARY KEYs.
     */
    private static void ensureIndexes(Statement st) throws SQLException {
        st.execute("""
                CREATE INDEX IF NOT EXISTS idx_file_uploads_run_success_index
                ON file_uploads (run_id, success, upload_index)
                """);
        st.execute("""
                CREATE INDEX IF NOT EXISTS idx_api_calls_run_rate_limited
                ON api_calls (run_id, rate_limited)
                """);
        st.execute("""
                CREATE INDEX IF NOT EXISTS idx_api_calls_run_ancillary
                ON api_calls (run_id, is_ancillary)
                """);
        st.execute("""
                CREATE INDEX IF NOT EXISTS idx_api_calls_run_phase_id
                ON api_calls (run_id, phase, id)
                """);
        st.execute("""
                CREATE INDEX IF NOT EXISTS idx_resource_samples_run_elapsed
                ON resource_samples (run_id, elapsed_ms)
                """);
    }

    public void insertRunStart(AppConfig config, String configSource, String payloadSha256, long payloadBytes) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO runs (
                  run_id, profile_name, config_source, started_at, box_parent_folder_id, box_run_folder_name,
                  thread_mode, concurrency, file_count, payload_bytes, payload_sha256,
                  chunked_upload_threshold_bytes, chunk_size_bytes, jvm_version, config_json
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            ps.setString(1, config.runId);
            ps.setString(2, config.profileName);
            ps.setString(3, configSource);
            ps.setString(4, Instant.now().toString());
            ps.setString(5, config.boxParentFolderId);
            ps.setString(6, config.boxRunFolderName);
            ps.setString(7, config.uploadThreadMode.name());
            ps.setInt(8, config.uploadConcurrency);
            ps.setInt(9, config.uploadFileCount);
            ps.setLong(10, payloadBytes);
            ps.setString(11, payloadSha256);
            ps.setLong(12, config.uploadChunkedUploadThresholdBytes);
            ps.setLong(13, config.uploadChunkSizeBytes);
            ps.setString(14, Runtime.version().toString());
            ps.setString(15, redactConfig(config));
            ps.executeUpdate();
            connection.commit();
        }
    }

    public void updateRunFolder(String runId, String folderId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE runs SET box_run_folder_id = ? WHERE run_id = ?")) {
            ps.setString(1, folderId);
            ps.setString(2, runId);
            ps.executeUpdate();
            connection.commit();
        }
    }

    public void updateRunUploadZone(String runId, String zoneHost, String baseUrl, String preflightUploadUrl) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE runs SET upload_zone_host = ?, upload_base_url = ?, preflight_upload_url = ? WHERE run_id = ?")) {
            ps.setString(1, zoneHost);
            ps.setString(2, baseUrl);
            ps.setString(3, preflightUploadUrl);
            ps.setString(4, runId);
            ps.executeUpdate();
            connection.commit();
        }
    }

    private static void ensureRunsColumn(Statement st, String column, String sqlType) throws SQLException {
        ensureTableColumn(st, "runs", column, sqlType);
    }

    private static void ensureSummaryColumn(Statement st, String column, String sqlType) throws SQLException {
        ensureTableColumn(st, "run_summaries", column, sqlType);
    }

    private static void ensureApiCallsColumn(Statement st, String column, String sqlType) throws SQLException {
        ensureTableColumn(st, "api_calls", column, sqlType);
    }

    private static void ensureTableColumn(Statement st, String table, String column, String sqlType) throws SQLException {
        try (ResultSet cols = st.getConnection().getMetaData().getColumns(null, null, table, column)) {
            if (!cols.next()) {
                st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + sqlType);
            }
        }
    }

    public void endRun(String runId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE runs SET ended_at = ? WHERE run_id = ?")) {
            ps.setString(1, Instant.now().toString());
            ps.setString(2, runId);
            ps.executeUpdate();
            connection.commit();
        }
    }

    public void insertApiCall(ApiCallRecord r) throws SQLException {
        synchronized (writeLock) {
            insertApiCallUnsafe(r);
        }
    }

    private void insertApiCallUnsafe(ApiCallRecord r) throws SQLException {
        NetworkTiming t = r.timing();
        double uploadMbps = 0;
        double throughputMs = t.throughputMs();
        if (throughputMs > 0 && t.requestBytes > 0) {
            uploadMbps = (t.requestBytes * 8.0) / (throughputMs * 1000.0);
        }
        insertApiCall.setString(1, r.runId());
        insertApiCall.setString(2, r.uploadGuid());
        insertApiCall.setString(3, r.boxFileId());
        if (r.uploadIndex() != null) {
            insertApiCall.setInt(4, r.uploadIndex());
        } else {
            insertApiCall.setNull(4, java.sql.Types.INTEGER);
        }
        insertApiCall.setString(5, r.phase().name());
        if (r.chunkIndex() != null) {
            insertApiCall.setInt(6, r.chunkIndex());
        } else {
            insertApiCall.setNull(6, java.sql.Types.INTEGER);
        }
        if (r.chunkOffset() != null) {
            insertApiCall.setLong(7, r.chunkOffset());
        } else {
            insertApiCall.setNull(7, java.sql.Types.BIGINT);
        }
        if (r.chunkLength() != null) {
            insertApiCall.setInt(8, r.chunkLength());
        } else {
            insertApiCall.setNull(8, java.sql.Types.INTEGER);
        }
        insertApiCall.setString(9, r.uploadStrategy());
        insertApiCall.setInt(10, r.ancillary() ? 1 : 0);
        insertApiCall.setInt(11, r.primaryUpload() ? 1 : 0);
        insertApiCall.setString(12, r.timestamp().toString());
        insertApiCall.setString(13, r.httpMethod());
        insertApiCall.setString(14, r.urlTemplate());
        insertApiCall.setInt(15, r.statusCode());
        insertApiCall.setDouble(16, t.durationMs);
        insertApiCall.setInt(17, t.requestBytes);
        insertApiCall.setInt(18, t.responseBytes);
        insertApiCall.setDouble(19, uploadMbps);
        insertApiCall.setString(20, r.threadMode());
        insertApiCall.setInt(21, r.attempt());
        insertApiCall.setInt(22, r.rateLimited() ? 1 : 0);
        if (r.retryAfterSeconds() != null) {
            insertApiCall.setInt(23, r.retryAfterSeconds());
        } else {
            insertApiCall.setNull(23, java.sql.Types.INTEGER);
        }
        insertApiCall.setString(24, r.errorMessage());
        if (r.retrySleepMs() != null) {
            insertApiCall.setDouble(25, r.retrySleepMs());
        } else {
            insertApiCall.setNull(25, java.sql.Types.REAL);
        }
        insertApiCall.setString(26, r.retryDelaySource());
        insertApiCall.setDouble(27, t.dnsLookupMs);
        insertApiCall.setDouble(28, t.tcpConnectMs);
        insertApiCall.setDouble(29, t.tlsHandshakeMs);
        insertApiCall.setDouble(30, t.timeToFirstByteMs);
        insertApiCall.setDouble(31, t.transferMs);
        insertApiCall.setInt(32, t.connectionReused ? 1 : 0);
        insertApiCall.setDouble(33, t.totalNetworkMs());
        insertApiCall.executeUpdate();
    }

    public void commit() throws SQLException {
        synchronized (writeLock) {
            connection.commit();
        }
    }

    public void rollback() throws SQLException {
        synchronized (writeLock) {
            connection.rollback();
        }
    }

    public void insertFileUpload(String runId, String uploadGuid, int uploadIndex, String boxFileId,
                                 String strategy, int chunkCount, boolean success,
                                 double uploadDurationMs, double e2eMs, int ancillaryCount, boolean had429) throws SQLException {
        synchronized (writeLock) {
            insertFileUploadUnsafe(runId, uploadGuid, uploadIndex, boxFileId, strategy, chunkCount,
                    success, uploadDurationMs, e2eMs, ancillaryCount, had429);
        }
    }

    private void insertFileUploadUnsafe(String runId, String uploadGuid, int uploadIndex, String boxFileId,
                                 String strategy, int chunkCount, boolean success,
                                 double uploadDurationMs, double e2eMs, int ancillaryCount, boolean had429) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO file_uploads (
                  run_id, upload_guid, upload_index, box_file_id, upload_strategy, chunk_count,
                  success, upload_duration_ms, end_to_end_duration_ms, ancillary_call_count, had_429
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            ps.setString(1, runId);
            ps.setString(2, uploadGuid);
            ps.setInt(3, uploadIndex);
            ps.setString(4, boxFileId);
            ps.setString(5, strategy);
            ps.setInt(6, chunkCount);
            ps.setInt(7, success ? 1 : 0);
            ps.setDouble(8, uploadDurationMs);
            ps.setDouble(9, e2eMs);
            ps.setInt(10, ancillaryCount);
            ps.setInt(11, had429 ? 1 : 0);
            ps.executeUpdate();
        }
    }

    public void insertResourceSample(String runId, double elapsedMs, double cpuProcess, Double cpuSystem,
                                     long nicIn, long nicOut, double nicRxMbps, double nicTxMbps,
                                     long appBytesDelta, double appMbps, int inFlight) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO resource_samples (
                  run_id, timestamp, elapsed_ms, cpu_process_pct, cpu_system_pct,
                  nic_bytes_in_delta, nic_bytes_out_delta, nic_rx_mbps, nic_tx_mbps,
                  app_bytes_uploaded_delta, app_upload_mbps, in_flight_uploads
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            ps.setString(1, runId);
            ps.setString(2, Instant.now().toString());
            ps.setDouble(3, elapsedMs);
            ps.setDouble(4, cpuProcess);
            if (cpuSystem != null) {
                ps.setDouble(5, cpuSystem);
            } else {
                ps.setNull(5, java.sql.Types.REAL);
            }
            ps.setLong(6, nicIn);
            ps.setLong(7, nicOut);
            ps.setDouble(8, nicRxMbps);
            ps.setDouble(9, nicTxMbps);
            ps.setLong(10, appBytesDelta);
            ps.setDouble(11, appMbps);
            ps.setInt(12, inFlight);
            ps.executeUpdate();
            connection.commit();
        }
    }

    public Connection connection() {
        return connection;
    }

    private String redactConfig(AppConfig config) {
        return new ProfileStore(config.profilesDirectory).redactedYaml(config);
    }

    @Override
    public void close() throws SQLException {
        insertApiCall.close();
        connection.close();
    }
}
