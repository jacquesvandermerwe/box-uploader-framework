package com.boxuploadperf.metrics;

import com.boxuploadperf.config.AppConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Summarizes which upload hosts/URLs were used so routing issues (wrong zone, mixed hosts) are visible.
 */
public final class UploadRoutingReport {

    public record RoutingSummary(
            String uploadZoneHost,
            String uploadBaseUrl,
            String preflightUploadUrl,
            List<String> hostsObserved,
            Map<String, List<String>> urlsByPhase,
            List<String> routingWarnings
    ) {}

    public static RoutingSummary load(Connection conn, String runId) throws Exception {
        String zoneHost = null;
        String baseUrl = null;
        String preflightUrl = null;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT upload_zone_host, upload_base_url, preflight_upload_url FROM runs WHERE run_id = ?")) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    zoneHost = rs.getString(1);
                    baseUrl = rs.getString(2);
                    preflightUrl = rs.getString(3);
                }
            }
        }

        Map<String, List<String>> urlsByPhase = new LinkedHashMap<>();
        Set<String> hosts = new LinkedHashSet<>();
        String uploadPhases = "'PREFLIGHT_CHECK','UPLOAD_SIMPLE','UPLOAD_SESSION_CREATE','UPLOAD_PART','UPLOAD_COMMIT'";
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT phase, url_template FROM api_calls
                WHERE run_id = ? AND phase IN (%s)
                ORDER BY id
                """.formatted(uploadPhases))) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String phase = rs.getString(1);
                    String url = rs.getString(2);
                    if (url == null || url.isBlank()) {
                        continue;
                    }
                    urlsByPhase.computeIfAbsent(phase, k -> new ArrayList<>());
                    List<String> list = urlsByPhase.get(phase);
                    if (list.isEmpty() || !list.get(list.size() - 1).equals(url)) {
                        list.add(url);
                    }
                    try {
                        hosts.add(java.net.URI.create(url).getHost());
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        List<String> warnings = new ArrayList<>();
        if (zoneHost != null && hosts.size() > 1) {
            warnings.add("Multiple upload hosts observed: " + hosts);
        }
        if (zoneHost != null) {
            for (String host : hosts) {
                if (host != null && !host.equals(zoneHost) && !host.equals("api.box.com")) {
                    warnings.add("Host " + host + " differs from preflight zone host " + zoneHost);
                }
            }
        }

        return new RoutingSummary(zoneHost, baseUrl, preflightUrl, List.copyOf(hosts), urlsByPhase, warnings);
    }

    public static Path write(AppConfig config) throws Exception {
        Path out = config.runDirectory().resolve("routing.json");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + config.sqlitePath().toAbsolutePath())) {
            RoutingSummary summary = load(conn, config.runId);
            Files.writeString(out, toJson(summary));
        }
        return out;
    }

    static String toJson(RoutingSummary s) {
        StringBuilder sb = new StringBuilder("{\n");
        jsonField(sb, "uploadZoneHost", s.uploadZoneHost());
        jsonField(sb, "uploadBaseUrl", s.uploadBaseUrl());
        jsonField(sb, "preflightUploadUrl", s.preflightUploadUrl());
        sb.append("  \"hostsObserved\": ").append(jsonStringList(s.hostsObserved())).append(",\n");
        sb.append("  \"urlsByPhase\": {\n");
        int pi = 0;
        for (var e : s.urlsByPhase().entrySet()) {
            if (pi++ > 0) sb.append(",\n");
            sb.append("    ").append(jsonString(e.getKey())).append(": ")
                    .append(jsonStringList(e.getValue()));
        }
        sb.append("\n  },\n");
        sb.append("  \"routingWarnings\": ").append(jsonStringList(s.routingWarnings())).append("\n}");
        return sb.toString();
    }

    private static void jsonField(StringBuilder sb, String key, String value) {
        sb.append("  ").append(jsonString(key)).append(": ");
        if (value == null) {
            sb.append("null,\n");
        } else {
            sb.append(jsonString(value)).append(",\n");
        }
    }

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String jsonStringList(List<String> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(jsonString(list.get(i)));
        }
        return sb.append(']').toString();
    }

    public static void printToConsole(RoutingSummary summary) {
        System.out.println();
        System.out.println("Upload routing:");
        if (summary.uploadZoneHost() != null) {
            System.out.println("  Zone host (preflight):  " + summary.uploadZoneHost());
        }
        if (summary.uploadBaseUrl() != null) {
            System.out.println("  Cached upload base:     " + summary.uploadBaseUrl());
        }
        if (summary.preflightUploadUrl() != null) {
            System.out.println("  Preflight upload_url:   " + summary.preflightUploadUrl());
        }
        if (!summary.hostsObserved().isEmpty()) {
            System.out.println("  Hosts on wire:          " + summary.hostsObserved());
        }
        for (var e : summary.urlsByPhase().entrySet()) {
            List<String> urls = e.getValue();
            if (urls.size() == 1) {
                System.out.println("  " + e.getKey() + ": " + urls.get(0));
            } else {
                System.out.println("  " + e.getKey() + ": " + urls.size() + " distinct URL(s), first=" + urls.get(0));
            }
        }
        for (String w : summary.routingWarnings()) {
            System.out.println("  WARNING: " + w);
        }
    }
}
