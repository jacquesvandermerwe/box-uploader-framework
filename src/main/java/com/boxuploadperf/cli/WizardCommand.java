package com.boxuploadperf.cli;

import com.boxuploadperf.config.ProfileStore;
import com.boxuploadperf.wizard.SetupWizard;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "wizard", description = "Interactive setup wizard")
public class WizardCommand implements Runnable {

    @Option(names = "--save-only", description = "Save profile without running")
    boolean saveOnly;

    @Override
    public void run() {
        try {
            new SetupWizard(new ProfileStore(RunCommand.defaultProfilesDir()), saveOnly).run();
        } catch (Exception e) {
            System.err.println("Wizard failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
