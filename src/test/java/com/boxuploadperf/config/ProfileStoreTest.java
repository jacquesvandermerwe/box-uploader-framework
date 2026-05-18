package com.boxuploadperf.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileStoreTest {

    @TempDir
    Path profilesDir;

    @Test
    void saveWritesMutableYamlProfile() throws Exception {
        AppConfig c = new AppConfig();
        c.profileName = "run1";
        c.profileDescription = "run1";
        c.boxClientId = "client-id";
        c.boxClientSecret = "secret";
        c.boxUserId = "user-1";
        c.boxParentFolderId = "folder-1";
        c.uploadThreadMode = ThreadMode.VIRTUAL;
        c.uploadFileCount = 50;
        c.uploadRateLimitPerSecond = 4.0;

        ProfileStore store = new ProfileStore(profilesDir);
        store.save(c);

        Path file = profilesDir.resolve("run1.yaml");
        assertTrue(Files.isRegularFile(file));
        String yaml = Files.readString(file);
        assertTrue(yaml.contains("createdAt"));
        assertTrue(yaml.contains("client-id"));

        AppConfig loaded = store.load("run1");
        assertEquals("run1", loaded.profileName);
        assertEquals("run1", loaded.profileDescription);
        assertEquals("client-id", loaded.boxClientId);
        assertEquals("secret", loaded.boxClientSecret);
        assertEquals(50, loaded.uploadFileCount);
        assertEquals(4.0, loaded.uploadRateLimitPerSecond, 0.001);
    }
}
