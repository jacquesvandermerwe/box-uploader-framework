package com.boxuploadperf.config;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class ProfileStore {

    private final Path profilesDir;

    public ProfileStore(Path profilesDir) {
        this.profilesDir = profilesDir;
    }

    public static ProfileStore defaults(AppConfig config) {
        return new ProfileStore(config.profilesDirectory);
    }

    public void save(AppConfig config) throws IOException {
        if (config.profileName == null || config.profileName.isBlank()) {
            throw new IllegalArgumentException("profile.name is required to save");
        }
        Files.createDirectories(profilesDir);
        setPrivateDirPermissions(profilesDir);
        Path file = profilePath(config.profileName);
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(opts);
        Map<String, Object> root = ConfigLoader.toMap(config);
        if (config.profileDescription != null) {
            ((Map<String, Object>) root.get("profile")).put("description", config.profileDescription);
        }
        ((Map<String, Object>) root.get("profile")).put("createdAt", Instant.now().toString());
        try (Writer w = Files.newBufferedWriter(file)) {
            yaml.dump(root, w);
        }
        setPrivateFilePermissions(file);
    }

    public AppConfig load(String profileName) throws IOException {
        Path file = profilePath(profileName);
        if (!Files.isRegularFile(file)) {
            throw new IOException("Profile not found: " + profileName + " (" + file + ")");
        }
        AppConfig config = ConfigLoader.load(file);
        config.profileName = profileName;
        return config;
    }

    public List<String> list() throws IOException {
        if (!Files.isDirectory(profilesDir)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        try (Stream<Path> stream = Files.list(profilesDir)) {
            stream.filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                    .map(p -> {
                        String fn = p.getFileName().toString();
                        return fn.replaceFirst("\\.(yaml|yml)$", "");
                    })
                    .sorted()
                    .forEach(names::add);
        }
        return names;
    }

    public void delete(String profileName) throws IOException {
        Files.deleteIfExists(profilePath(profileName));
    }

    public Path profilePath(String profileName) {
        return profilesDir.resolve(profileName + ".yaml");
    }

    public String redactedYaml(AppConfig config) {
        AppConfig copy = copyForDisplay(config);
        copy.boxClientSecret = "***REDACTED***";
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        return new Yaml(opts).dump(ConfigLoader.toMap(copy));
    }

    private static AppConfig copyForDisplay(AppConfig c) {
        AppConfig n = new AppConfig();
        n.profileName = c.profileName;
        n.profileDescription = c.profileDescription;
        n.boxClientId = c.boxClientId;
        n.boxClientSecret = c.boxClientSecret;
        n.boxEnterpriseId = c.boxEnterpriseId;
        n.boxUserId = c.boxUserId;
        n.boxParentFolderId = c.boxParentFolderId;
        n.uploadFileCount = c.uploadFileCount;
        n.uploadConcurrency = c.uploadConcurrency;
        n.uploadThreadMode = c.uploadThreadMode;
        n.pdfTargetSizeBytes = c.pdfTargetSizeBytes;
        n.uploadChunkedUploadThresholdBytes = c.uploadChunkedUploadThresholdBytes;
        n.uploadChunkSizeBytes = c.uploadChunkSizeBytes;
        return n;
    }

    private static void setPrivateDirPermissions(Path dir) {
        try {
            Files.setPosixFilePermissions(dir, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows
        }
    }

    private static void setPrivateFilePermissions(Path file) {
        try {
            Files.setPosixFilePermissions(file, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException | IOException ignored) {
        }
    }
}
