package com.boxuploadperf.charts;

import com.boxuploadperf.config.AppConfig;
import com.boxuploadperf.metrics.RunReportData;
import com.boxuploadperf.metrics.UploadFailureReport;
import com.boxuploadperf.metrics.UploadRoutingReport;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** PDF benchmark report styled to mirror the HTML report (panels, tables, chart images). */
public final class PdfChartReport {

    private static final float PAGE_W = PDRectangle.LETTER.getWidth();
    private static final float PAGE_H = PDRectangle.LETTER.getHeight();
    private static final float MARGIN = 44f;
    private static final float CONTENT_W = PAGE_W - 2 * MARGIN;
    private static final float PANEL_PAD = 14f;
    private static final float ROW_V_PAD = 5f;
    private static final float WRAP_LINE_GAP = 2f;

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font FONT_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDType1Font FONT_MONO = new PDType1Font(Standard14Fonts.FontName.COURIER);

    private static final Color CHART_UPLOAD = new Color(37, 99, 235);
    private static final Color CHART_CPU = new Color(22, 163, 74);
    private static final Color CHART_MBPS = new Color(220, 38, 38);

    public Path generate(AppConfig config) throws Exception {
        RunChartData data = RunChartData.load(config);
        Path out = config.reportPdfPath();
        Files.createDirectories(out.getParent());

        try (PDDocument doc = new PDDocument()) {
            PageLayout layout = new PageLayout(doc);
            writeCover(layout, config.runId, data);
            writeSectionTable(layout, "Configuration", configRows(data.report()), false);
            writeSectionTable(layout, "Aggregate metrics", metricsRows(data.report()), false);
            writeSectionTable(layout, "Upload routing", routingRows(data.routing()), true);
            if (data.failures() != null && !data.failures().failures().isEmpty()) {
                writeSectionTable(layout, "Failed uploads", failureRows(data.failures()), true);
            }
            writeChartImage(layout, doc, "Upload time", "Upload index", "Duration (ms)",
                    "Upload duration", data.uploadDurationMs(), CHART_UPLOAD);
            writeChartImage(layout, doc, "CPU (process)", "Elapsed time (s)", "CPU (%)",
                    "CPU %", data.cpuProcessPct(), CHART_CPU);
            writeChartImage(layout, doc, "App upload throughput", "Elapsed time (s)", "Mbps",
                    "Mbps", data.appUploadMbps(), CHART_MBPS);
            layout.closeStream();
            doc.save(out.toFile());
        }
        return out;
    }

    private static void writeCover(PageLayout layout, String runId, RunChartData data) throws IOException {
        layout.ensureSpace(120);
        PDPageContentStream cs = layout.stream();
        float y = layout.y;

        cs.setNonStrokingColor(0.15f, 0.39f, 0.92f);
        cs.addRect(0, PAGE_H - 6, PAGE_W, 6);
        cs.fill();

        y = drawText(cs, FONT_BOLD, 22, MARGIN, y, "Box Upload Performance");
        y -= 4;
        y = drawText(cs, FONT, 12, MARGIN, y, "Benchmark report");
        y -= 18;
        y = drawText(cs, FONT_BOLD, 11, MARGIN, y, "Run ID");
        y = drawMono(cs, 10, MARGIN + 72, y, runId);
        y -= 14;

        if (data.report() != null && data.report().config() != null) {
            var c = data.report().config();
            y = drawKeyValue(cs, y, "Profile", nullToDash(c.profileName()));
            y = drawKeyValue(cs, y, "Config source", nullToDash(c.configSource()));
            y = drawKeyValue(cs, y, "Workload",
                    c.fileCount() + " files, concurrency " + c.concurrency() + ", " + c.uploadStrategy());
        }
        layout.y = y - 20;
    }

    private static void writeSectionTable(PageLayout layout, String title, List<String[]> rows,
                                          boolean warnStyle) throws IOException {
        float labelSize = 9f;
        float valueSize = 8f;
        float valueMaxW = CONTENT_W - 2 * PANEL_PAD - 152f;
        float bodyH = 20 + 8;
        List<List<String>> wrapped = new ArrayList<>();
        List<Float> rowHeights = new ArrayList<>();
        for (String[] row : rows) {
            List<String> lines = wrapText(row[1], FONT_MONO, valueSize, valueMaxW);
            wrapped.add(lines);
            float rowH = tableRowHeight(lines.size(), labelSize, valueSize);
            rowHeights.add(rowH);
            bodyH += rowH;
        }
        float panelH = PANEL_PAD * 2 + bodyH;
        layout.ensureSpace(panelH + 24);
        float panelTop = layout.y;
        float panelBottom = panelTop - panelH;

        PDPageContentStream cs = layout.stream();
        drawPanel(cs, MARGIN, panelBottom, CONTENT_W, panelH);

        float titleBaseline = panelTop - PANEL_PAD - 16;
        drawText(cs, FONT_BOLD, 13, MARGIN + PANEL_PAD, titleBaseline, title);
        float rowTop = titleBaseline - fontDescent(FONT_BOLD, 13f) - 8f;

        float labelX = MARGIN + PANEL_PAD;
        float valueX = MARGIN + PANEL_PAD + 148f;
        float stripeW = CONTENT_W - 2 * PANEL_PAD + 8;
        boolean stripe = false;
        for (int r = 0; r < rows.size(); r++) {
            String[] row = rows.get(r);
            List<String> valueLines = wrapped.get(r);
            float rowHeight = rowHeights.get(r);
            float thisRowTop = rowTop;
            if (stripe) {
                cs.setNonStrokingColor(0.98f, 0.98f, 0.99f);
                cs.addRect(labelX - 4, thisRowTop - rowHeight, stripeW, rowHeight);
                cs.fill();
            }
            stripe = !stripe;
            boolean warning = warnStyle && "Warning".equals(row[0]);
            if (warning) {
                cs.setNonStrokingColor(0.71f, 0.33f, 0.04f);
            } else {
                cs.setNonStrokingColor(0.42f, 0.45f, 0.49f);
            }
            float baseline = thisRowTop - ROW_V_PAD - Math.max(fontAscent(FONT_BOLD, labelSize), fontAscent(FONT_MONO, valueSize));
            drawText(cs, FONT_BOLD, labelSize, labelX, baseline, row[0]);
            cs.setNonStrokingColor(0.1f, 0.1f, 0.1f);
            float valueBaseline = baseline;
            float valueStep = lineStep(FONT_MONO, valueSize, WRAP_LINE_GAP);
            for (String line : valueLines) {
                drawMono(cs, valueSize, valueX, valueBaseline, line);
                valueBaseline -= valueStep;
            }
            rowTop = thisRowTop - rowHeight;
        }
        layout.y = panelBottom - 20;
    }

    private static float tableRowHeight(int valueLineCount, float labelSize, float valueSize) {
        float labelBlock = lineStep(FONT_BOLD, labelSize, 0);
        float valueBlock = valueLineCount <= 0
                ? lineStep(FONT_MONO, valueSize, 0)
                : lineStep(FONT_MONO, valueSize, 0) + (valueLineCount - 1) * lineStep(FONT_MONO, valueSize, WRAP_LINE_GAP);
        return ROW_V_PAD * 2 + Math.max(labelBlock, valueBlock);
    }

    private static float fontAscent(PDType1Font font, float size) {
        return font.getFontDescriptor().getAscent() / 1000f * size;
    }

    private static float fontDescent(PDType1Font font, float size) {
        return Math.abs(font.getFontDescriptor().getDescent() / 1000f * size);
    }

    private static float lineStep(PDType1Font font, float size, float gapAfterLine) {
        return fontAscent(font, size) + fontDescent(font, size) + gapAfterLine;
    }

    private static void writeChartImage(PageLayout layout, PDDocument doc, String panelTitle,
                                        String xLabel, String yLabel, String seriesLabel,
                                        List<Double> yValues, Color color) throws IOException {
        int imgW = 920;
        int imgH = 300;
        BufferedImage chart = PdfLineChartImage.render(panelTitle, xLabel, yLabel, seriesLabel, yValues, color, imgW, imgH);

        float displayW = CONTENT_W;
        float displayH = displayW * imgH / imgW;
        float panelH = PANEL_PAD * 2 + displayH + 8;
        layout.ensureSpace(panelH + 24);

        float panelTop = layout.y;
        float panelBottom = panelTop - panelH;
        PDPageContentStream cs = layout.stream();
        drawPanel(cs, MARGIN, panelBottom, CONTENT_W, panelH);

        PDImageXObject pdImage = LosslessFactory.createFromImage(doc, chart);
        float imgX = MARGIN + PANEL_PAD;
        float imgY = panelBottom + PANEL_PAD;
        cs.drawImage(pdImage, imgX, imgY, displayW - 2 * PANEL_PAD, displayH);

        layout.y = panelBottom - 20;
    }

    private static void drawPanel(PDPageContentStream cs, float x, float y, float w, float h) throws IOException {
        cs.setNonStrokingColor(1f, 1f, 1f);
        cs.addRect(x, y, w, h);
        cs.fill();
        cs.setStrokingColor(0.9f, 0.91f, 0.92f);
        cs.setLineWidth(0.75f);
        cs.addRect(x, y, w, h);
        cs.stroke();
    }

    private static float drawKeyValue(PDPageContentStream cs, float y, String key, String value) throws IOException {
        drawText(cs, FONT_BOLD, 10, MARGIN, y, key);
        return drawText(cs, FONT, 10, MARGIN + 72, y, sanitize(value));
    }

    private static float drawText(PDPageContentStream cs, PDType1Font font, float size,
                                  float tx, float ty, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(tx, ty);
        cs.showText(sanitize(text));
        cs.endText();
        return ty - size - 2;
    }

    private static float drawMono(PDPageContentStream cs, float size, float tx, float ty, String text)
            throws IOException {
        cs.beginText();
        cs.setFont(FONT_MONO, size);
        cs.newLineAtOffset(tx, ty);
        cs.showText(sanitize(text));
        cs.endText();
        return ty - size - 2;
    }

    private static List<String> wrapText(String text, PDType1Font font, float size, float maxWidth)
            throws IOException {
        if (text == null || text.isBlank()) {
            return List.of("-");
        }
        String s = sanitize(text);
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : s.split(" ")) {
            String trial = current.isEmpty() ? word : current + " " + word;
            float w = font.getStringWidth(trial) / 1000f * size;
            if (w > maxWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(trial);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        if (lines.isEmpty()) {
            lines.add(s);
        }
        return lines;
    }

    private static String sanitize(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c == '\n' || c == '\r') {
                out.append(' ');
            } else if (c < 127 && c >= 32) {
                out.append(c);
            } else if (c == '\u2014' || c == '\u2013') {
                out.append('-');
            }
        }
        return out.toString();
    }

    private static String nullToDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    private static List<String[]> configRows(RunReportData report) {
        if (report == null || report.config() == null) {
            return Collections.singletonList(new String[] {"Status", "No run metadata"});
        }
        var c = report.config();
        return List.<String[]>of(
                row("Run ID", c.runId()),
                row("Profile", c.profileName()),
                row("Config source", c.configSource()),
                row("Started / ended", fmtTime(c.startedAt()) + " -> " + fmtTime(c.endedAt())),
                row("Thread mode", c.threadMode()),
                row("Concurrency", String.valueOf(c.concurrency())),
                row("File count", String.valueOf(c.fileCount())),
                row("Payload size", formatBytes(c.payloadBytes())),
                row("Upload strategy", c.uploadStrategy()),
                row("Rate limit", report.rateLimitDescription()),
                row("Enterprise ID", c.enterpriseId()),
                row("Impersonation user ID", c.impersonationUserId()),
                row("Parent folder ID", c.boxParentFolderId()),
                row("Run folder ID", c.boxRunFolderId()),
                row("Upload zone host", c.uploadZoneHost()),
                row("Upload base URL", c.uploadBaseUrl()),
                row("JVM", c.jvmVersion()));
    }

    private static List<String[]> metricsRows(RunReportData report) {
        if (report == null || report.metrics() == null) {
            return Collections.singletonList(new String[] {"Status", "No summary row"});
        }
        var m = report.metrics();
        List<String[]> rows = new ArrayList<>();
        rows.add(row("Files attempted", String.valueOf(m.filesAttempted())));
        rows.add(row("Files succeeded", String.valueOf(m.filesSucceeded())));
        rows.add(row("Files failed", String.valueOf(m.filesFailed())));
        if (m.filesNotStarted() > 0) {
            rows.add(row("Files not started", String.valueOf(m.filesNotStarted())));
        }
        rows.add(row("HTTP 429 responses", String.valueOf(m.count429())));
        if (m.retrySleepCount() > 0) {
            rows.add(row("Retry wait (count / total / avg)",
                    String.format(Locale.US, "%d / %.0f ms / %.0f ms",
                            m.retrySleepCount(), m.retrySleepTotalMs(), m.retrySleepAvgMs())));
        }
        rows.add(row("Ancillary API calls", String.valueOf(m.ancillaryRequests())));
        rows.add(row("Upload time (min / avg / max)",
                formatMs(m.uploadTimeMinMs()) + " / " + formatMs(m.uploadTimeAvgMs()) + " / "
                        + formatMs(m.uploadTimeMaxMs())));
        rows.add(row("Upload time (P95 / P99)", formatMs(m.uploadTimeP95Ms()) + " / " + formatMs(m.uploadTimeP99Ms())));
        rows.add(row("Run duration", formatMs(m.runDurationMs())));
        rows.add(row("Total bytes uploaded", formatBytes(m.totalBytesUploaded())));
        rows.add(row("Throughput (bytes/s)", formatBytes((long) m.throughputBytesPerSec()) + "/s"));
        rows.add(row("Throughput (files/s)", String.format(Locale.US, "%.3f", m.throughputFilesPerSec())));
        rows.add(row("Rate limit", report.rateLimitDescription()));
        rows.add(row("CPU process avg / max", String.format(Locale.US, "%.1f%% / %.1f%%",
                m.cpuProcessAvgPct(), m.cpuProcessMaxPct())));
        rows.add(row("App upload Mbps avg / peak", String.format(Locale.US, "%.2f / %.2f",
                m.appUploadMbpsAvg(), m.appUploadMbpsPeak())));
        rows.add(row("NIC TX Mbps avg / peak", String.format(Locale.US, "%.2f / %.2f",
                m.nicTxMbpsAvg(), m.nicTxMbpsPeak())));
        rows.add(row("NIC RX Mbps avg / peak", String.format(Locale.US, "%.2f / %.2f",
                m.nicRxMbpsAvg(), m.nicRxMbpsPeak())));
        return rows;
    }

    private static List<String[]> failureRows(UploadFailureReport.Summary failures) {
        List<String[]> rows = new ArrayList<>();
        rows.add(row("Failed count", String.valueOf(failures.filesFailed())));
        for (var e : failures.failuresByReason().entrySet()) {
            rows.add(row(e.getKey().name(), String.valueOf(e.getValue())));
        }
        int max = Math.min(35, failures.failures().size());
        for (int i = 0; i < max; i++) {
            var f = failures.failures().get(i);
            rows.add(row(
                    "#" + f.uploadIndex() + " " + f.failureReason(),
                    String.format(Locale.US, "status=%d attempts=%d %s",
                            f.lastStatusCode(), f.httpAttempts(),
                            f.errorMessage() == null ? "" : f.errorMessage())));
        }
        if (failures.failures().size() > max) {
            rows.add(row("More failures", (failures.failures().size() - max)
                    + " not shown (box-upload-perf failures --run-id ...)"));
        }
        return rows;
    }

    private static List<String[]> routingRows(UploadRoutingReport.RoutingSummary r) {
        if (r == null) {
            return Collections.singletonList(new String[] {"Status", "No routing data"});
        }
        var rows = new ArrayList<String[]>();
        rows.add(row("Zone host (preflight)", r.uploadZoneHost()));
        rows.add(row("Cached upload base", r.uploadBaseUrl()));
        rows.add(row("Preflight upload_url", r.preflightUploadUrl()));
        rows.add(row("Hosts on wire", r.hostsObserved().isEmpty() ? null : String.join(", ", r.hostsObserved())));
        for (var e : r.urlsByPhase().entrySet()) {
            String urls = e.getValue().size() == 1 ? e.getValue().get(0)
                    : e.getValue().get(0) + " ... (" + e.getValue().size() + " distinct)";
            rows.add(row(e.getKey(), urls));
        }
        for (String w : r.routingWarnings()) {
            rows.add(row("Warning", w));
        }
        return rows;
    }

    private static String[] row(String k, String v) {
        return new String[] {k, v == null || v.isBlank() ? "-" : v};
    }

    private static String fmtTime(String iso) {
        if (iso == null || iso.isBlank()) {
            return "-";
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

    /** Tracks vertical cursor and creates new pages when content overflows. */
    private static final class PageLayout {
        private final PDDocument doc;
        private PDPage page;
        private PDPageContentStream stream;
        private float y = PAGE_H - MARGIN;

        PageLayout(PDDocument doc) throws IOException {
            this.doc = doc;
            newPage();
        }

        void ensureSpace(float needed) throws IOException {
            if (y - needed < MARGIN + 36) {
                closeStream();
                newPage();
            }
        }

        PDPageContentStream stream() {
            return stream;
        }

        private void newPage() throws IOException {
            page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            stream = new PDPageContentStream(doc, page);
            stream.setNonStrokingColor(0.98f, 0.98f, 0.99f);
            stream.addRect(0, 0, PAGE_W, PAGE_H);
            stream.fill();
            y = PAGE_H - MARGIN;
        }

        void closeStream() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }
    }
}
