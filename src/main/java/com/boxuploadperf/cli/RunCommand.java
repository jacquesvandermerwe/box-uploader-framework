package com.boxuploadperf.cli;

import com.boxuploadperf.config.AppConfig;
import com.boxuploadperf.config.ConfigLoader;
import com.boxuploadperf.config.ProfileStore;
import com.boxuploadperf.upload.BenchmarkRunner;
import com.boxuploadperf.wizard.SetupWizard;
import com.boxuploadperf.wizard.WizardResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;

@Command(name = "run", description = "Execute a benchmark run")
public class RunCommand implements Runnable {

    @Option(names = "--profile", description = "Saved profile name")
    String profile;

    @Option(names = "--config", description = "Path to YAML config file")
    Path configPath;

    @Option(names = "--file-count", description = "Override upload.fileCount")
    Integer fileCount;

    @Option(names = "--concurrency", description = "Override upload.concurrency")
    Integer concurrency;

    @Option(names = "--thread-mode", description = "Override upload.threadMode (VIRTUAL or PLATFORM)")
    String threadMode;

    @Option(names = "--rate-limit", description = "Override upload.rateLimitPerSecond")
    Double rateLimit;

    @Option(names = "--enforce-rate-limit", description = "Throttle uploads to effective rate limit")
    Boolean enforceRateLimit;

    @Option(names = "--payload-bytes", description = "Override pdf.targetSizeBytes")
    Long payloadBytes;

    @Option(names = "--run-id", description = "Override run.runId (and Box run folder name)")
    String runId;

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
                WizardResult wizardResult = wizard.run();
                if (wizardResult == null) {
                    return;
                }
                config = wizardResult.config();
                source = "WIZARD";
                if (!wizardResult.runBenchmark()) {
                    return;
                }
            } else {
                System.err.println("Provide --profile <name> or --config <path>, or run interactively.");
                System.exit(1);
                return;
            }
            ConfigOverrides.apply(config, fileCount, concurrency, threadMode, rateLimit,
                    enforceRateLimit, payloadBytes, runId);
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
