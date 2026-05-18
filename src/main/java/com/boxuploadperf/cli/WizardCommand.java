package com.boxuploadperf.cli;

import com.boxuploadperf.config.ProfileStore;
import com.boxuploadperf.upload.BenchmarkRunner;
import com.boxuploadperf.wizard.SetupWizard;
import com.boxuploadperf.wizard.WizardResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "wizard", description = "Interactive setup wizard")
public class WizardCommand implements Runnable {

    @Option(names = "--save-only", description = "Save profile without running")
    boolean saveOnly;

    @Override
    public void run() {
        try {
            WizardResult result = new SetupWizard(new ProfileStore(RunCommand.defaultProfilesDir()), saveOnly).run();
            if (result != null && result.runBenchmark()) {
                result.config().validate();
                new BenchmarkRunner().execute(result.config(), "WIZARD");
            }
        } catch (Exception e) {
            System.err.println("Wizard failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
