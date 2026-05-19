package com.boxuploadperf.charts;

import com.boxuploadperf.config.AppConfig;
import com.boxuploadperf.metrics.RunReportData;
import com.boxuploadperf.metrics.UploadFailureReport;
import com.boxuploadperf.metrics.UploadRoutingReport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HtmlChartReport {

    public void generate(AppConfig config) throws Exception {
        Path chartsDir = config.runDirectory().resolve("charts");
        Files.createDirectories(chartsDir);

        RunChartData data = RunChartData.load(config);
        String configHtml = "";
        String metricsHtml = "";
        if (data.report() != null) {
            configHtml = buildConfigHtml(data.report());
            metricsHtml = buildMetricsHtml(data.report());
        }
        String routingHtml = buildRoutingHtml(data.routing());
        String failuresHtml = buildFailuresHtml(data.failures());

        List<String> uploadLabels = new ArrayList<>();
        for (Integer idx : data.uploadIndices()) {
            uploadLabels.add(String.valueOf(idx));
        }
        List<String> timeLabels = new ArrayList<>();
        for (Double sec : data.resourceElapsedSec()) {
            timeLabels.add(String.format(Locale.US, "%.1f", sec));
        }

        Files.writeString(chartsDir.resolve("index.html"),
                buildHtml(config.runId, configHtml, metricsHtml, routingHtml, failuresHtml,
                        uploadLabels, data.uploadDurationMs(), timeLabels, data.cpuProcessPct(),
                        data.appUploadMbps()));
    }

    private static String buildConfigHtml(RunReportData report) {
        RunReportData.Config c = report.config();
        if (c == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder("<section class=\"panel\"><h2>Configuration</h2><table>");
        row(sb, "Run ID", c.runId());
        row(sb, "Profile", c.profileName());
        row(sb, "Config source", c.configSource());
        row(sb, "Started / ended", fmtTime(c.startedAt()) + " → " + fmtTime(c.endedAt()));
        row(sb, "Thread mode", c.threadMode());
        row(sb, "Concurrency", String.valueOf(c.concurrency()));
        row(sb, "File count", String.valueOf(c.fileCount()));
        row(sb, "Payload size", formatBytes(c.payloadBytes()));
        row(sb, "Upload strategy", c.uploadStrategy());
        row(sb, "Chunked threshold", formatBytes(c.chunkedThresholdBytes()));
        row(sb, "Chunk size", formatBytes(c.chunkSizeBytes()));
        if (c.rateLimitDisabled()) {
            row(sb, "Rate limit", "none (comparison disabled for this run)");
        } else if (c.rateLimitFromProfile()) {
            row(sb, "Rate limit (profile)", String.format(Locale.US, "%.3f uploads/s", c.effectiveRateLimitPerSec()));
        } else {
            row(sb, "Rate limit (Box default)",
                    String.format(Locale.US, "%.0f uploads/min (%.3f uploads/s)",
                            AppConfig.BOX_UPLOAD_RATE_LIMIT_PER_MINUTE, c.effectiveRateLimitPerSec()));
        }
        row(sb, "Enterprise ID", c.enterpriseId());
        row(sb, "Impersonation user ID(s)", ReportFormat.formatImpersonationUsers(c.impersonationUserId()));
        row(sb, "Parent folder ID", c.boxParentFolderId());
        row(sb, "Run folder ID", c.boxRunFolderId());
        row(sb, "Upload zone host", c.uploadZoneHost());
        row(sb, "Upload base URL", c.uploadBaseUrl());
        row(sb, "JVM", c.jvmVersion());
        sb.append("</table></section>");
        return sb.toString();
    }

    private static String buildMetricsHtml(RunReportData report) {
        RunReportData.Metrics m = report.metrics();
        if (m == null) {
            return "<section class=\"panel\"><h2>Aggregate metrics</h2><p>No summary row yet.</p></section>";
        }
        StringBuilder sb = new StringBuilder("<section class=\"panel\"><h2>Aggregate metrics</h2><table>");
        row(sb, "Files attempted", String.valueOf(m.filesAttempted()));
        row(sb, "Files succeeded", String.valueOf(m.filesSucceeded()));
        row(sb, "Files failed", String.valueOf(m.filesFailed()));
        if (m.filesNotStarted() > 0) {
            row(sb, "Files not started", String.valueOf(m.filesNotStarted()));
        }
        row(sb, "HTTP 429 responses", String.valueOf(m.count429()));
        if (m.retrySleepCount() > 0) {
            row(sb, "Retry wait (count / total / avg)",
                    String.format(Locale.US, "%d / %.0f ms / %.0f ms",
                            m.retrySleepCount(), m.retrySleepTotalMs(), m.retrySleepAvgMs()));
        }
        if (m.retryAfterAvgSec() != null) {
            row(sb, "Retry-After header (avg / max)",
                    String.format(Locale.US, "%.1f s / %d s", m.retryAfterAvgSec(), m.retryAfterMaxSec()));
        }
        if (m.count429WithoutRetryAfter() > 0) {
            row(sb, "429 without Retry-After", String.valueOf(m.count429WithoutRetryAfter()));
        }
        row(sb, "Ancillary API calls", String.valueOf(m.ancillaryRequests()));
        row(sb, "Upload time (min)", formatMs(m.uploadTimeMinMs()));
        row(sb, "Upload time (avg)", formatMs(m.uploadTimeAvgMs()));
        row(sb, "Upload time (max)", formatMs(m.uploadTimeMaxMs()));
        row(sb, "Upload time (P95)", formatMs(m.uploadTimeP95Ms()));
        row(sb, "Upload time (P99)", formatMs(m.uploadTimeP99Ms()));
        row(sb, "Run duration", formatMs(m.runDurationMs()));
        row(sb, "Total bytes uploaded", formatBytes(m.totalBytesUploaded()));
        row(sb, "Throughput (bytes/s)", formatBytes((long) m.throughputBytesPerSec()) + "/s");
        row(sb, "Throughput (files/s)", String.format(Locale.US, "%.3f", m.throughputFilesPerSec()));
        row(sb, "Rate limit", report.rateLimitDescription());
        row(sb, "CPU process avg / max", String.format(Locale.US, "%.1f%% / %.1f%%",
                m.cpuProcessAvgPct(), m.cpuProcessMaxPct()));
        if (m.cpuSystemAvgPct() != null) {
            row(sb, "CPU system avg", String.format(Locale.US, "%.1f%%", m.cpuSystemAvgPct()));
        }
        row(sb, "App upload Mbps avg / peak", String.format(Locale.US, "%.2f / %.2f",
                m.appUploadMbpsAvg(), m.appUploadMbpsPeak()));
        row(sb, "NIC TX Mbps avg / peak", String.format(Locale.US, "%.2f / %.2f",
                m.nicTxMbpsAvg(), m.nicTxMbpsPeak()));
        row(sb, "NIC RX Mbps avg / peak", String.format(Locale.US, "%.2f / %.2f",
                m.nicRxMbpsAvg(), m.nicRxMbpsPeak()));
        sb.append("</table></section>");
        return sb.toString();
    }

    private static String buildFailuresHtml(UploadFailureReport.Summary failures) {
        if (failures == null || failures.failures().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("<section class=\"panel\"><h2>Failed uploads</h2><table>");
        row(sb, "Failed count", String.valueOf(failures.filesFailed()));
        for (var e : failures.failuresByReason().entrySet()) {
            row(sb, e.getKey().name(), String.valueOf(e.getValue()));
        }
        sb.append("</table><h3>Sample failures</h3><table>");
        sb.append("<tr><th>Index</th><th>Reason</th><th>Status</th><th>Attempts</th><th>Message</th></tr>");
        int max = Math.min(40, failures.failures().size());
        for (int i = 0; i < max; i++) {
            var f = failures.failures().get(i);
            sb.append("<tr><td>").append(f.uploadIndex()).append("</td><td>")
                    .append(escape(f.failureReason().name())).append("</td><td>")
                    .append(f.lastStatusCode()).append("</td><td>")
                    .append(f.httpAttempts()).append("</td><td>")
                    .append(escape(f.errorMessage() == null ? "" : f.errorMessage()))
                    .append("</td></tr>");
        }
        if (failures.failures().size() > max) {
            sb.append("<tr><td colspan=\"5\">… ")
                    .append(failures.failures().size() - max)
                    .append(" more (box-upload-perf failures --run-id …)</td></tr>");
        }
        sb.append("</table></section>");
        return sb.toString();
    }

    private static String buildRoutingHtml(UploadRoutingReport.RoutingSummary r) {
        StringBuilder sb = new StringBuilder("<section class=\"panel\"><h2>Upload routing</h2><table>");
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
        sb.append("<tr><th>").append(escape(label)).append("</th><td>")
                .append(escape(value)).append("</td></tr>");
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String fmtTime(String iso) {
        if (iso == null || iso.isBlank()) {
            return "—";
        }
        return iso.length() > 19 ? iso.substring(0, 19).replace('T', ' ') : iso;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double mib = bytes / (1024.0 * 1024.0);
        if (mib < 1024) {
            return String.format(Locale.US, "%.2f MiB", mib);
        }
        return String.format(Locale.US, "%.2f GiB", mib / 1024.0);
    }

    private static String formatMs(double ms) {
        return String.format(Locale.US, "%.1f ms", ms);
    }

    private static String buildHtml(String runId, String configHtml, String metricsHtml, String routingHtml,
                                    String failuresHtml,
                                    List<String> uploadLabels, List<Double> uploadTimes,
                                    List<String> timeLabels, List<Double> cpu, List<Double> appMbps) {
        return """
                <!DOCTYPE html>
                <html><head><meta charset="utf-8"><title>Run %s</title>
                <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js"></script>
                <style>
                body{font-family:system-ui,sans-serif;margin:2rem;background:#fafafa;color:#111;}
                h1{margin-bottom:0.25rem;} .run-id{color:#555;font-size:0.95rem;margin-bottom:1.5rem;}
                .panel,.chart{background:#fff;padding:1rem 1.25rem;border-radius:8px;max-width:960px;margin-bottom:1.5rem;box-shadow:0 1px 2px rgba(0,0,0,.06);}
                table{border-collapse:collapse;width:100%%;}
                th{text-align:left;padding:0.4rem 1rem 0.4rem 0;color:#555;width:14rem;font-weight:600;vertical-align:top;}
                td{padding:0.4rem 0;word-break:break-word;}
                td code,th+td{font-family:ui-monospace,monospace;font-size:0.88rem;}
                .warn{color:#b45309;font-weight:500;}
                </style>
                </head><body>
                <h1>Box Upload Performance</h1>
                <p class="run-id">Run <code>%s</code></p>
                %s
                %s
                %s
                %s
                <div class="chart"><h2>Upload time</h2><canvas id="c1"></canvas></div>
                <div class="chart"><h2>CPU (process)</h2><canvas id="c2"></canvas></div>
                <div class="chart"><h2>App upload throughput</h2><canvas id="c3"></canvas></div>
                <script>
                function chartOpts(xTitle,yTitle){
                  return {
                    responsive:true,
                    plugins:{legend:{display:true}},
                    scales:{
                      x:{title:{display:true,text:xTitle}},
                      y:{title:{display:true,text:yTitle},beginAtZero:true}
                    }
                  };
                }
                new Chart(document.getElementById('c1'),{
                  type:'line',
                  data:{labels:%s,datasets:[{label:'Upload duration',data:%s,borderColor:'#2563eb'}]},
                  options:chartOpts('Upload index','Duration (ms)')
                });
                new Chart(document.getElementById('c2'),{
                  type:'line',
                  data:{labels:%s,datasets:[{label:'CPU %%',data:%s,borderColor:'#16a34a'}]},
                  options:chartOpts('Elapsed time (s)','CPU (%%)')
                });
                new Chart(document.getElementById('c3'),{
                  type:'line',
                  data:{labels:%s,datasets:[{label:'Mbps',data:%s,borderColor:'#dc2626'}]},
                  options:chartOpts('Elapsed time (s)','Throughput (Mbps)')
                });
                </script></body></html>
                """.formatted(
                runId, runId, configHtml, metricsHtml, routingHtml, failuresHtml,
                toJson(uploadLabels), toJson(uploadTimes),
                toJson(timeLabels), toJson(cpu),
                toJson(timeLabels), toJson(appMbps));
    }

    private static String toJson(List<?> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
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
