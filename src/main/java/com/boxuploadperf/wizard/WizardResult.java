package com.boxuploadperf.wizard;

import com.boxuploadperf.config.AppConfig;

/** Outcome of {@link SetupWizard}: config plus whether a benchmark run was requested. */
public record WizardResult(AppConfig config, boolean runBenchmark) {}
