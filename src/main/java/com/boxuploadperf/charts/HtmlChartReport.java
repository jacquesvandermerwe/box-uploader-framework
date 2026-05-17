package com.boxuploadperf.charts;

import com.boxuploadperf.config.AppConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public final class HtmlChartReport {

    public void generate(AppConfig config) throws Exception {
        Path chartsDir = config.runDirectory().resolve("charts");
        Files.createDirectories(chartsDir);

        List<String> labels = new ArrayList<>();
        List<Double> uploadTimes = new ArrayList<>();
        List<Double> cpu = new ArrayList<>();
        List<Double> appMbps = new ArrayList<>();
        String routingHtml = "";

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + config.sqlitePath().toAbsolutePath())) {
            routingHtml = buildRoutingHtml(com.boxuploadperf.metrics.UploadRoutingReport.load(conn, config.runId));
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT upload_index, upload_duration_ms FROM file_uploads WHERE run_id = ? AND success = 1 ORDER BY upload_index")) {
                ps.setString(1, config.runId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        labels.add(String.valueOf(rs.getInt(1)));
                        uploadTimes.add(rs.getDouble(2));
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT cpu_process_pct, app_upload_mbps FROM resource_samples WHERE run_id = ? ORDER BY elapsed_ms")) {
                ps.setString(1, config.runId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        cpu.add(rs.getDouble(1));
                        appMbps.add(rs.getDouble(2));
                    }
                }
            }
        }

        Files.writeString(chartsDir.resolve("index.html"),
                buildHtml(config.runId, labels, uploadTimes, cpu, appMbps, routingHtml));
    }

    private static String buildRoutingHtml(com.boxuploadperf.metrics.UploadRoutingReport.RoutingSummary r) {
        StringBuilder sb = new StringBuilder("<section class=\"routing\"><h2>Upload routing</h2><table>");
        row(sb, "Zone host (preflight)", r.uploadZoneHost());
        row(sb, "Cached upload base", r.uploadBaseUrl());
        row(sb, "Preflight upload_url", r.preflightUploadUrl());
        row(sb, "Hosts on wire", r.hostsObserved().isEmpty() ? null : String.join(", ", r.hostsObserved()));
        for (var e : r.urlsByPhase().entrySet()) {
            String urls = e.getValue().size() == 1 ? e.getValue().get(0)
                    : e.getValue().get(0) + " … (" + e.getValue().size() + " distinct)";
            row(sb, e.getKey(), urls);
        }
        for (String w : r.routingWarnings()) {
            sb.append("<tr><td colspan=\"2\" class=\"warn\">").append(escape(w)).append("</td></tr>");
        }
        sb.append("</table></section>");
        return sb.toString();
    }

    private static void row(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sb.append("<tr><th>").append(escape(label)).append("</th><td><code>")
                .append(escape(value)).append("</code></td></tr>");
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String buildHtml(String runId, List<String> labels, List<Double> uploadTimes,
                                    List<Double> cpu, List<Double> appMbps, String routingHtml) {
        return """
                <!DOCTYPE html>
                <html><head><meta charset="utf-8"><title>Run %s</title>
                <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js"></script>
                <style>body{font-family:system-ui,sans-serif;margin:2rem;background:#fafafa;}
                .chart,.routing{background:#fff;padding:1rem;border-radius:8px;max-width:960px;margin-bottom:1.5rem;}
                .routing table{border-collapse:collapse;width:100%%;} .routing th{text-align:left;padding:0.35rem 0.75rem 0.35rem 0;color:#555;width:12rem;}
                .routing td code{font-size:0.85rem;word-break:break-all;} .warn{color:#b45309;}</style>
                </head><body>
                <h1>Box Upload Performance</h1>
                <p>Run: <code>%s</code></p>
                %s
                <motion class="chart"><h2>Upload time (ms)</h2><canvas id="c1"></canvas></div>
                <motion class="chart"><h2>CPU (process)</h2><canvas id="c2"></canvas></motion>
                <motion class="chart"><h2>App upload Mbps</h2><canvas id="c3"></canvas></motion>
                <script>
                const opts={responsive:true};
                const cpuLabels=%s.map((_,i)=>String(i));
                new Chart(document.getElementById('c1'),{type:'line',data:{labels:%s,datasets:[{label:'ms',data:%s,borderColor:'#2563eb'}]},options:opts});
                new Chart(document.getElementById('c2'),{type:'line',data:{labels:cpuLabels,datasets:[{label:'CPU',data:%s,borderColor:'#16a34a'}]},options:opts});
                new Chart(document.getElementById('c3'),{type:'line',data:{labels:cpuLabels,datasets:[{label:'Mbps',data:%s,borderColor:'#dc2626'}]},options:opts});
                </script></body></html>
                """.formatted(runId, runId, routingHtml, toJson(cpu), toJson(labels), toJson(uploadTimes), toJson(cpu), toJson(appMbps));
    }

    private static String toJson(List<?> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            Object o = list.get(i);
            if (o instanceof String s) {
                sb.append('"').append(s.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            } else {
                sb.append(o);
            }
        }
        return sb.append(']').toString();
    }
}
