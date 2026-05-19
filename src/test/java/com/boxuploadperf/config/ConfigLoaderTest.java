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
}
