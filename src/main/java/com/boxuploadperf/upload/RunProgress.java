package com.boxuploadperf.upload;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/** Console progress for benchmark phases and upload completion. */
public final class RunProgress {

    private static final long MIN_REPORT_INTERVAL_MS = 2_000;

    private final int totalUploads;
    private final Object reportLock = new Object();
    private long uploadPhaseStartMs;
    private long lastReportMs;

    public RunProgress(int totalUploads) {
        this.totalUploads = totalUploads;
    }

    public void phase(String message) {
        System.out.println("[box-upload-perf] " + message);
    }

    public void startUploadPhase() {
        uploadPhaseStartMs = System.currentTimeMillis();
        phase(String.format(Locale.US, "Upload phase started (%d files, reporting every ~2s)", totalUploads));
    }

    public void uploadSucceeded(AtomicInteger succeeded, AtomicInteger failed, AtomicInteger count429,
                                int inFlight) {
        reportIfDue(succeeded, failed, count429, inFlight, false);
    }

    public void uploadFailed(int uploadIndex, AtomicInteger succeeded, AtomicInteger failed,
                             AtomicInteger count429, int inFlight, Exception error) {
        System.err.printf("[box-upload-perf] Upload %d failed: %s%n", uploadIndex, error.getMessage());
        reportIfDue(succeeded, failed, count429, inFlight, false);
    }

    public void uploadPhaseComplete(AtomicInteger succeeded, AtomicInteger failed, AtomicInteger count429,
                                    int inFlight) {
        reportIfDue(succeeded, failed, count429, inFlight, true);
    }

    private void reportIfDue(AtomicInteger succeeded, AtomicInteger failed, AtomicInteger count429,
                             int inFlight, boolean force) {
        int ok = succeeded.get();
        int bad = failed.get();
        int done = ok + bad;
        long now = System.currentTimeMillis();
        synchronized (reportLock) {
            if (!force && done < totalUploads && now - lastReportMs < MIN_REPORT_INTERVAL_MS) {
                return;
            }
            lastReportMs = now;
        }
        double elapsedSec = uploadPhaseStartMs > 0 ? (now - uploadPhaseStartMs) / 1000.0 : 0;
        double rate = elapsedSec > 0 ? ok / elapsedSec : 0;
        String eta = "";
        if (!force && done > 0 && done < totalUploads && uploadPhaseStartMs > 0) {
            double secPerFile = elapsedSec / done;
            double remainingSec = secPerFile * (totalUploads - done);
            eta = String.format(Locale.US, ", ETA ~%.0fs", remainingSec);
        }
        System.out.printf(Locale.US,
                "[box-upload-perf] Progress: %d/%d succeeded, %d failed, %d×429, %d in-flight, %.2f files/s%s%n",
                ok, totalUploads, bad, count429.get(), inFlight, rate, eta);
    }
}
