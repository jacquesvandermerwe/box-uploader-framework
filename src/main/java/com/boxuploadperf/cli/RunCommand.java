package com.boxuploadperf.cli;

import com.boxuploadperf.config.AppConfig;
import com.boxuploadperf.config.ConfigLoader;
import com.boxuploadperf.config.ProfileStore;
import com.boxuploadperf.upload.BenchmarkRunner;
import com.boxuploadperf.wizard.SetupWizard;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;

@Command(name = "run", description = "Execute a benchmark run")
public class RunCommand implements Runnable {

    @Option(names = "--profile", description = "Saved profile name")
    String profile;

    @Option(names = "--config", description = "Path to YAML config file")
    Path configPath;

    @Override
    public void run() {
        try {
            AppConfig config;
            String source;
            if (profile != null && !profile.isBlank()) {
                ProfileStore store = new ProfileStore(defaultProfilesDir());
                config = store.load(profile.trim());
                source = "PROFILE";
            } else if (configPath != null) {
                config = ConfigLoader.load(configPath);
                source = "FILE";
            } else if (System.console() != null) {
                SetupWizard wizard = new SetupWizard(new ProfileStore(defaultProfilesDir()), false);
                config = wizard.run();
                if (config == null) {
                    return;
                }
                source = "WIZARD";
                return;
            } else {
                System.err.println("Provide --profile <name> or --config <path>, or run interactively.");
                System.exit(1);
                return;
            }
            config.validate();
            new BenchmarkRunner().execute(config, source);
        } catch (Exception e) {
            System.err.println("Run failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    static Path defaultProfilesDir() {
        return Path.of(System.getProperty("user.home"), ".box-upload-perf", "profiles");
    }
}
