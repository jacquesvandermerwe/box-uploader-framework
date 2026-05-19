package com.boxuploadperf.wizard;

import com.boxuploadperf.config.AppConfig;
import com.boxuploadperf.config.ProfileStore;
import com.boxuploadperf.config.ThreadMode;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.util.Locale;
import java.util.Scanner;
public final class SetupWizard {

    private final ProfileStore profiles;
    private final boolean saveOnly;
    private final Scanner scanner = new Scanner(System.in);

    public SetupWizard(ProfileStore profiles, boolean saveOnly) {
        this.profiles = profiles;
        this.saveOnly = saveOnly;
    }

    public WizardResult run() throws Exception {
        if (System.console() == null && System.getenv("CI") != null) {
            throw new IllegalStateException("No TTY: use --profile or --config in CI (wizard requires interactive terminal)");
        }

        System.out.println("=== Box Upload Performance — Setup Wizard ===\n");
        AppConfig config = new AppConfig();

        String start = ask("New setup (n) or load profile (l)? [n/l]: ", "n");
        if (start.toLowerCase(Locale.ROOT).startsWith("l")) {
            var names = profiles.list();
            if (names.isEmpty()) {
                System.out.println("No profiles found.");
            } else {
                System.out.println("Profiles: " + String.join(", ", names));
                String name = ask("Profile name: ", "");
                config = profiles.load(name.trim());
                System.out.println("Loaded profile: " + name);
            }
        }

        System.out.println("--- Box credentials & target ---");
        config.boxClientId = ask("Box Client ID: ", config.boxClientId);
        config.boxClientSecret = askSecret("Box Client Secret: ", config.boxClientSecret);
        config.boxEnterpriseId = ask("Box Enterprise ID (required unless impersonating a user): ", config.boxEnterpriseId);
        config.boxParentFolderId = ask("Box Parent Folder ID (upload destination): ", config.boxParentFolderId);
        config.boxUserId = ask(
                "Impersonation user ID(s) (optional; comma-separated for round-robin via As-User header): ",
                config.boxUserId);
        String rate = ask(
                "Upload rate limit baseline (uploads/s; 0 = Box 240/min, <0 = no baseline in reports): ",
                rateLimitDefault(config));
        config.uploadRateLimitPerSecond = Double.parseDouble(rate);
        if (config.uploadRateLimitDisabled()) {
            if (config.uploadEnforceRateLimit) {
                System.out.println("Note: rate-limit enforcement disabled when baseline is < 0.");
            }
            config.uploadEnforceRateLimit = false;
        } else {
            double effective = config.effectiveUploadRateLimitPerSecond();
            System.out.printf(Locale.US,
                    "Effective limit for reports/enforcement: %.3f uploads/s%n", effective);
            config.uploadEnforceRateLimit = askYesNo(
                    "Enforce rate limit during uploads? (y = throttle starts, n = reports only): ",
                    config.uploadEnforceRateLimit);
        }

        config.uploadFileCount = Integer.parseInt(ask("Number of uploads: ", String.valueOf(config.uploadFileCount)));
        config.pdfTargetSizeBytes = Long.parseLong(ask("PDF size (bytes): ", String.valueOf(config.pdfTargetSizeBytes)));
        config.uploadThreadMode = ThreadMode.parse(ask("Thread mode VIRTUAL or PLATFORM: ", 
                config.uploadThreadMode != null ? config.uploadThreadMode.name() : "VIRTUAL"));
        config.uploadConcurrency = Integer.parseInt(ask("Concurrency: ", String.valueOf(config.uploadConcurrency)));
        config.uploadChunkedUploadThresholdBytes = Long.parseLong(ask("Chunked threshold (bytes): ",
                String.valueOf(config.uploadChunkedUploadThresholdBytes)));
        config.uploadChunkSizeBytes = Long.parseLong(ask("Chunk size (bytes): ",
                String.valueOf(config.uploadChunkSizeBytes)));

        config.assignFreshRunIdentity();

        System.out.println("\n--- Review ---");
        System.out.println(profiles.redactedYaml(config));

        String action = ask("Run (r), Save profile (s), Both (b), Cancel (c)? [b]: ", "b");
        if (action.toLowerCase(Locale.ROOT).startsWith("c")) {
            System.out.println("Cancelled.");
            return null;
        }

        if (action.toLowerCase(Locale.ROOT).startsWith("s") || action.toLowerCase(Locale.ROOT).startsWith("b")) {
            String profileName = ask("Profile name to save: ", config.profileName != null ? config.profileName : "my-run");
            config.profileName = profileName.trim();
            String desc = ask("Description: ", config.profileDescription != null ? config.profileDescription : "");
            config.profileDescription = desc;
            profiles.save(config);
            System.out.println("Saved profile: " + profiles.profilePath(config.profileName));
        }

        boolean runBenchmark = action.toLowerCase(Locale.ROOT).startsWith("r")
                || action.toLowerCase(Locale.ROOT).startsWith("b");
        return new WizardResult(config, runBenchmark && !saveOnly);
    }

    private static String rateLimitDefault(AppConfig config) {
        if (config.uploadRateLimitDisabled()) {
            return String.valueOf(config.uploadRateLimitPerSecond);
        }
        if (config.uploadRateLimitPerSecond > 0) {
            return String.valueOf(config.uploadRateLimitPerSecond);
        }
        return "0";
    }

    private boolean askYesNo(String prompt, boolean defaultValue) {
        String def = defaultValue ? "y" : "n";
        String line = ask(prompt, def);
        return line.toLowerCase(Locale.ROOT).startsWith("y");
    }

    private String ask(String prompt, String defaultValue) {
        if (defaultValue != null && !defaultValue.isBlank()) {
            System.out.print(prompt + "[" + defaultValue + "] ");
        } else {
            System.out.print(prompt);
        }
        String line = scanner.nextLine().trim();
        return line.isEmpty() ? defaultValue : line;
    }

    private String askSecret(String prompt, String defaultPresent) {
        try {
            Terminal terminal = TerminalBuilder.builder().system(true).build();
            LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();
            String hint = defaultPresent != null && !defaultPresent.isBlank() ? " (press Enter to keep existing)" : "";
            String value = reader.readLine(prompt + hint + " ");
            terminal.close();
            if (value == null || value.isBlank()) {
                return defaultPresent;
            }
            return value;
        } catch (Exception e) {
            System.out.print(prompt);
            String line = scanner.nextLine().trim();
            return line.isEmpty() ? defaultPresent : line;
        }
    }
}
