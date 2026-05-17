package com.boxuploadperf.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "box-upload-perf", mixinStandardHelpOptions = true, version = "1.0.0",
        description = "Box Upload Performance Framework",
        subcommands = {RunCommand.class, WizardCommand.class, ProfileCommand.class})
public class BoxUploadPerfCommand implements Runnable {

    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }
}
