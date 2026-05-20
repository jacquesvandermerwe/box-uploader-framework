package com.boxuploadperf.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {

    @Test
    void loadsNestedYaml() {
        AppConfig c = ConfigLoader.fromMap(Map.of(
                "box", Map.of(
                        "clientId", "id",
                        "clientSecret", "secret",
                        "enterpriseId", "ent",
                        "parentFolderId", "folder"),
                "upload", Map.of(
                        "fileCount", 5,
                        "concurrency", 2,
                        "threadMode", "VIRTUAL"),
                "pdf", Map.of("targetSizeBytes", 1024)));
        c.validate();
        assertEquals("id", c.boxClientId);
        assertEquals(5, c.uploadFileCount);
        assertEquals(ThreadMode.VIRTUAL, c.uploadThreadMode);
    }

    @Test
    void loadsRetryAndEnforceRateLimit() {
        AppConfig c = ConfigLoader.fromMap(Map.of(
                "box", Map.of(
                        "clientId", "id",
                        "clientSecret", "secret",
                        "enterpriseId", "ent",
                        "parentFolderId", "folder"),
                "upload", Map.of(
                        "fileCount", 5,
                        "concurrency", 2,
                        "threadMode", "VIRTUAL",
                        "enforceRateLimit", true,
                        "rateLimitPerSecond", 2.0),
                "retry", Map.of("maxAttempts", 5, "backoffMs", 1000),
                "work", Map.of("reusePayload", true),
                "pdf", Map.of("targetSizeBytes", 1024)));
        assertEquals(5, c.retryMaxAttempts);
        assertEquals(1000L, c.retryBackoffMs);
        assertTrue(c.uploadEnforceRateLimit);
        assertTrue(c.workReusePayload);
    }

    @Test
    void requiresThreadMode() {
        AppConfig c = new AppConfig();
        c.boxClientId = "a";
        c.boxClientSecret = "b";
        c.boxEnterpriseId = "c";
        c.boxParentFolderId = "d";
        assertThrows(IllegalArgumentException.class, c::validate);
    }

    @Test
    void validateDefaultsRunFolderNameToRunId() {
        AppConfig c = new AppConfig();
        c.boxClientId = "id";
        c.boxClientSecret = "secret";
        c.boxEnterpriseId = "ent";
        c.boxUserId = "3312464263";
        c.boxParentFolderId = "folder";
        c.runId = "run-abc";
        c.boxRunFolderName = null;
        c.uploadThreadMode = ThreadMode.VIRTUAL;
        c.validate();
        assertEquals("run-abc", c.boxRunFolderName);
    }

    @Test
    void validateOverwritesStaleProfileRunFolderName() {
        AppConfig c = new AppConfig();
        c.boxClientId = "id";
        c.boxClientSecret = "secret";
        c.boxEnterpriseId = "ent";
        c.boxUserId = "3312464263";
        c.boxParentFolderId = "folder";
        c.runId = "new-run-id";
        c.boxRunFolderName = "old-saved-folder-id";
        c.uploadThreadMode = ThreadMode.VIRTUAL;
        c.validate();
        assertEquals("new-run-id", c.boxRunFolderName);
    }

    @Test
    void parsesCommaSeparatedUserIds() {
        AppConfig c = ConfigLoader.fromMap(Map.of(
                "box", Map.of(
                        "clientId", "id",
                        "clientSecret", "secret",
                        "enterpriseId", "ent",
                        "userId", "111, 222, 333",
                        "parentFolderId", "folder"),
                "upload", Map.of("threadMode", "VIRTUAL"),
                "pdf", Map.of("targetSizeBytes", 1024)));
        c.validate();
        assertEquals(java.util.List.of("111", "222", "333"), c.impersonationUserIds());
        assertTrue(c.usesImpersonation());
    }

    @Test
    void impersonationRequiresEnterpriseId() {
        AppConfig c = new AppConfig();
        c.boxClientId = "id";
        c.boxClientSecret = "secret";
        c.boxUserId = "123";
        c.boxParentFolderId = "folder";
        c.uploadThreadMode = ThreadMode.VIRTUAL;
        assertThrows(IllegalArgumentException.class, c::validate);
    }

    @Test
    void toMapOmitsRunFolderName() {
        AppConfig c = new AppConfig();
        c.boxRunFolderName = "should-not-be-saved";
        Map<String, Object> box = (Map<String, Object>) ConfigLoader.toMap(c).get("box");
        assertTrue(!box.containsKey("runFolderName"));
    }

    @Test
    void copyCopiesAllFields() throws Exception {
        AppConfig c1 = new AppConfig();
        for (java.lang.reflect.Field field : AppConfig.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            Class<?> type = field.getType();
            if (type == String.class) {
                field.set(c1, "test-" + field.getName());
            } else if (type == int.class) {
                field.set(c1, 42);
            } else if (type == long.class) {
                field.set(c1, 100L);
            } else if (type == double.class) {
                field.set(c1, 3.14);
            } else if (type == boolean.class) {
                field.set(c1, true);
            } else if (type == Integer.class) {
                field.set(c1, 24);
            } else if (type == java.nio.file.Path.class) {
                field.set(c1, java.nio.file.Path.of("test-path"));
            } else if (type == ThreadMode.class) {
                field.set(c1, ThreadMode.PLATFORM);
            } else if (type == java.util.List.class) {
                field.set(c1, java.util.List.of("user-1"));
            } else {
                throw new IllegalStateException("Unhandled field type: " + type + " for field: " + field.getName());
            }
        }

        AppConfig c2 = c1.copy();

        for (java.lang.reflect.Field field : AppConfig.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            Object v1 = field.get(c1);
            Object v2 = field.get(c2);
            assertEquals(v1, v2, "Field " + field.getName() + " was not correctly copied!");
        }
    }
}
