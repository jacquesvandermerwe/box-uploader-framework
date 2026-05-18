package com.boxuploadperf.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
            c.boxRunFolderName = str(box.get("runFolderName"));
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
            c.uploadChunkedUploadThresholdBytes = longVal(upload.get("chunkedUploadThresholdBytes"), c.uploadChunkedUploadThresholdBytes);
            c.uploadChunkSizeBytes = longVal(upload.get("chunkSizeBytes"), c.uploadChunkSizeBytes);
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
        }
        Map<String, Object> cleanup = map(root.get("cleanup"));
        if (cleanup != null) {
            c.cleanupDeleteBoxRunFolderAfterRun = bool(cleanup.get("deleteBoxRunFolderAfterRun"));
            c.cleanupDeleteLocalPayloadAfterRun = bool(cleanup.get("deleteLocalPayloadAfterRun"));
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
        if (c.boxRunFolderName == null || c.boxRunFolderName.isBlank()) {
            c.boxRunFolderName = c.runId;
        }
        return c;
    }

    public static Map<String, Object> toMap(AppConfig c) {
        return Map.of(
                "profile", Map.of(
                        "name", nullToEmpty(c.profileName),
                        "description", nullToEmpty(c.profileDescription)),
                "box", Map.of(
                        "clientId", nullToEmpty(c.boxClientId),
                        "clientSecret", nullToEmpty(c.boxClientSecret),
                        "enterpriseId", nullToEmpty(c.boxEnterpriseId),
                        "userId", nullToEmpty(c.boxUserId),
                        "parentFolderId", nullToEmpty(c.boxParentFolderId),
                        "runFolderName", nullToEmpty(c.boxRunFolderName)),
                "upload", Map.of(
                        "fileCount", c.uploadFileCount,
                        "concurrency", c.uploadConcurrency,
                        "threadMode", c.uploadThreadMode != null ? c.uploadThreadMode.name() : "",
                        "chunkedUploadThresholdBytes", c.uploadChunkedUploadThresholdBytes,
                        "chunkSizeBytes", c.uploadChunkSizeBytes),
                "pdf", Map.of("targetSizeBytes", c.pdfTargetSizeBytes),
                "work", Map.of(
                        "parentDirectory", c.workParentDirectory.toString(),
                        "payloadFileName", c.workPayloadFileName),
                "run", Map.of(
                        "runId", c.runId,
                        "outputDirectory", c.runOutputDirectory.toString()),
                "metrics", Map.of("sampleIntervalMs", c.metricsSampleIntervalMs),
                "cleanup", Map.of(
                        "deleteBoxRunFolderAfterRun", c.cleanupDeleteBoxRunFolderAfterRun,
                        "deleteLocalPayloadAfterRun", c.cleanupDeleteLocalPayloadAfterRun));
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
