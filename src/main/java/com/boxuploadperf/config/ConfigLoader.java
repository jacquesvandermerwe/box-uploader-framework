package com.boxuploadperf.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings("unchecked")
public final class ConfigLoader {

    private ConfigLoader() {}

    public static AppConfig load(Path yamlPath) throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream in = Files.newInputStream(yamlPath)) {
            Map<String, Object> root = yaml.load(in);
            return fromMap(root);
        }
    }

    public static AppConfig fromMap(Map<String, Object> root) {
        AppConfig c = new AppConfig();
        if (root == null) {
            return c;
        }
        Map<String, Object> profile = map(root.get("profile"));
        if (profile != null) {
            c.profileName = str(profile.get("name"));
            c.profileDescription = str(profile.get("description"));
        }
        Map<String, Object> box = map(root.get("box"));
        if (box != null) {
            c.boxClientId = str(box.get("clientId"));
            c.boxClientSecret = str(box.get("clientSecret"));
            c.boxEnterpriseId = str(box.get("enterpriseId"));
            c.boxUserId = str(box.get("userId"));
            c.boxParentFolderId = str(box.get("parentFolderId"));
            // runFolderName in YAML is ignored; each run uses run.runId (see AppConfig.validate).
        }
        Map<String, Object> upload = map(root.get("upload"));
        if (upload != null) {
            c.uploadFileCount = intVal(upload.get("fileCount"), c.uploadFileCount);
            c.uploadConcurrency = intVal(upload.get("concurrency"), c.uploadConcurrency);
            if (upload.get("threadMode") != null) {
                c.uploadThreadMode = ThreadMode.parse(str(upload.get("threadMode")));
            }
            if (upload.get("platformThreadPoolSize") != null) {
                c.uploadPlatformThreadPoolSize = intVal(upload.get("platformThreadPoolSize"), c.uploadConcurrency);
            }
            c.uploadRateLimitPerSecond = doubleVal(upload.get("rateLimitPerSecond"), c.uploadRateLimitPerSecond);
            c.uploadEnforceRateLimit = bool(upload.get("enforceRateLimit"));
            c.uploadChunkedUploadThresholdBytes = longVal(upload.get("chunkedUploadThresholdBytes"), c.uploadChunkedUploadThresholdBytes);
            c.uploadChunkSizeBytes = longVal(upload.get("chunkSizeBytes"), c.uploadChunkSizeBytes);
        }
        Map<String, Object> retry = map(root.get("retry"));
        if (retry != null) {
            c.retryMaxAttempts = intVal(retry.get("maxAttempts"), c.retryMaxAttempts);
            c.retryBackoffMs = longVal(retry.get("backoffMs"), c.retryBackoffMs);
        }
        Map<String, Object> pdf = map(root.get("pdf"));
        if (pdf != null) {
            c.pdfTargetSizeBytes = longVal(pdf.get("targetSizeBytes"), c.pdfTargetSizeBytes);
        }
        Map<String, Object> work = map(root.get("work"));
        if (work != null) {
            if (work.get("parentDirectory") != null) {
                c.workParentDirectory = Path.of(str(work.get("parentDirectory")));
            }
            c.workPayloadFileName = strOr(work.get("payloadFileName"), c.workPayloadFileName);
            c.workReusePayload = bool(work.get("reusePayload"));
        }
        Map<String, Object> cleanup = map(root.get("cleanup"));
        if (cleanup != null) {
            c.cleanupDeleteBoxRunFolderAfterRun = bool(cleanup.get("deleteBoxRunFolderAfterRun"));
            c.cleanupDeleteLocalPayloadAfterRun = bool(cleanup.get("deleteLocalPayloadAfterRun"));
        }
        Map<String, Object> report = map(root.get("report"));
        if (report != null) {
            if (report.get("generatePdf") != null) {
                c.reportGeneratePdf = bool(report.get("generatePdf"));
            }
            if (report.get("uploadPdfToBox") != null) {
                c.reportUploadPdfToBox = bool(report.get("uploadPdfToBox"));
            }
            c.reportPdfFileName = strOr(report.get("pdfFileName"), c.reportPdfFileName);
        }
        Map<String, Object> run = map(root.get("run"));
        if (run != null) {
            c.runId = strOr(run.get("runId"), c.runId);
            if (run.get("outputDirectory") != null) {
                c.runOutputDirectory = Path.of(str(run.get("outputDirectory")));
            }
        }
        Map<String, Object> metrics = map(root.get("metrics"));
        if (metrics != null) {
            c.metricsSqliteFileName = strOr(metrics.get("sqliteFileName"), c.metricsSqliteFileName);
            c.metricsSampleIntervalMs = longVal(metrics.get("sampleIntervalMs"), c.metricsSampleIntervalMs);
            c.metricsNetworkInterfaceName = str(metrics.get("networkInterfaceName"));
        }
        Map<String, Object> profiles = map(root.get("profiles"));
        if (profiles != null && profiles.get("directory") != null) {
            c.profilesDirectory = Path.of(str(profiles.get("directory")));
        }
        return c;
    }

    /** Mutable map tree for YAML dump and profile save (ProfileStore may add fields). */
    public static Map<String, Object> toMap(AppConfig c) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("name", nullToEmpty(c.profileName));
        profile.put("description", nullToEmpty(c.profileDescription));

        Map<String, Object> box = new LinkedHashMap<>();
        box.put("clientId", nullToEmpty(c.boxClientId));
        box.put("clientSecret", nullToEmpty(c.boxClientSecret));
        box.put("enterpriseId", nullToEmpty(c.boxEnterpriseId));
        box.put("userId", nullToEmpty(c.boxUserId));
        box.put("parentFolderId", nullToEmpty(c.boxParentFolderId));

        Map<String, Object> upload = new LinkedHashMap<>();
        upload.put("fileCount", c.uploadFileCount);
        upload.put("concurrency", c.uploadConcurrency);
        upload.put("threadMode", c.uploadThreadMode != null ? c.uploadThreadMode.name() : "");
        upload.put("rateLimitPerSecond", c.uploadRateLimitPerSecond);
        upload.put("enforceRateLimit", c.uploadEnforceRateLimit);
        upload.put("chunkedUploadThresholdBytes", c.uploadChunkedUploadThresholdBytes);
        upload.put("chunkSizeBytes", c.uploadChunkSizeBytes);

        Map<String, Object> work = new LinkedHashMap<>();
        work.put("parentDirectory", c.workParentDirectory.toString());
        work.put("payloadFileName", c.workPayloadFileName);
        work.put("reusePayload", c.workReusePayload);

        Map<String, Object> run = new LinkedHashMap<>();
        run.put("outputDirectory", c.runOutputDirectory.toString());

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("sampleIntervalMs", c.metricsSampleIntervalMs);

        Map<String, Object> cleanup = new LinkedHashMap<>();
        cleanup.put("deleteBoxRunFolderAfterRun", c.cleanupDeleteBoxRunFolderAfterRun);
        cleanup.put("deleteLocalPayloadAfterRun", c.cleanupDeleteLocalPayloadAfterRun);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generatePdf", c.reportGeneratePdf);
        report.put("uploadPdfToBox", c.reportUploadPdfToBox);
        report.put("pdfFileName", c.reportPdfFileName);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("profile", profile);
        root.put("box", box);
        root.put("upload", upload);
        root.put("pdf", Map.of("targetSizeBytes", c.pdfTargetSizeBytes));
        root.put("work", work);
        root.put("run", run);
        root.put("metrics", metrics);
        root.put("cleanup", cleanup);
        root.put("report", report);

        if (c.retryMaxAttempts != 3 || c.retryBackoffMs != 500L) {
            root.put("retry", Map.of(
                    "maxAttempts", c.retryMaxAttempts,
                    "backoffMs", c.retryBackoffMs));
        }
        return root;
    }

    private static Map<String, Object> map(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String strOr(Object o, String def) {
        String s = str(o);
        return s == null || s.isBlank() ? def : s;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static int intVal(Object o, int def) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        return def;
    }

    private static long longVal(Object o, long def) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        return def;
    }

    private static double doubleVal(Object o, double def) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        return def;
    }

    private static boolean bool(Object o) {
        return o instanceof Boolean b && b;
    }
}
