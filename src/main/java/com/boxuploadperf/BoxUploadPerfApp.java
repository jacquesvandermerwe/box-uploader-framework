package com.boxuploadperf;

import com.boxuploadperf.cli.BoxUploadPerfCommand;
import com.boxuploadperf.config.AppConfig;
import picocli.CommandLine;

public final class BoxUploadPerfApp {

    public static void main(String[] args) {
        AppConfig.requireJava21();
        int code = new CommandLine(new BoxUploadPerfCommand()).execute(args);
        System.exit(code);
    }
}
