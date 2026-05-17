package com.boxuploadperf.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void requiresThreadMode() {
        AppConfig c = new AppConfig();
        c.boxClientId = "a";
        c.boxClientSecret = "b";
        c.boxEnterpriseId = "c";
        c.boxParentFolderId = "d";
        assertThrows(IllegalArgumentException.class, c::validate);
    }
}
