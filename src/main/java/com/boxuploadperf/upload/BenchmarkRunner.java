package com.boxuploadperf.upload;

import com.boxuploadperf.box.BoxClient;
import com.boxuploadperf.config.AppConfig;
import com.boxuploadperf.metrics.MetricsDatabase;
import com.boxuploadperf.metrics.ResourceSampler;
import com.boxuploadperf.metrics.RunSummary;
import com.boxuploadperf.metrics.RunSummarizer;
import com.boxuploadperf.metrics.UploadRoutingReport;
import com.boxuploadperf.pdf.PdfPayloadGenerator;
import com.boxuploadperf.charts.HtmlChartReport;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class BenchmarkRunner {

    public void execute(AppConfig config, String configSource) throws Exception {
        RunProgress progress = new RunProgress(config.uploadFileCount);
        progress.phase("Run " + config.runId + " — validating configuration");
        config.validate();

        Files.createDirectories(config.runDirectory());
        Files.createDirectories(config.workParentDirectory);

        Path payload = config.payloadPath();
        PdfPayloadGenerator pdfGen = new PdfPayloadGenerator();
        long payloadBytes;
        if (config.workReusePayload && pdfGen.tryReuseExisting(payload, config.pdfTargetSizeBytes)) {
            payloadBytes = Files.size(payload);
            progress.phase(String.format(Locale.US,
                    "Reusing existing payload (%s, %,d bytes)", payload, payloadBytes));
        } else {
            progress.phase(String.format(Locale.US,
                    "Generating payload PDF (target %,d bytes)", config.pdfTargetSizeBytes));
            payloadBytes = pdfGen.generate(payload, config.pdfTargetSizeBytes, config.runId);
            progress.phase(String.format(Locale.US, "Payload ready (%s, %,d bytes)", payload, payloadBytes));
        }

        progress.phase("Computing payload SHA-256");
        String sha256 = sha256(payload);

        try (MetricsDatabase db = new MetricsDatabase(config.sqlitePath())) {
            db.insertRunStart(config, configSource, sha256, payloadBytes);

            ResourceSampler sampler = new ResourceSampler(config, db);
            Thread samplerThread = new Thread(sampler, "resource-sampler");
            samplerThread.setDaemon(true);
            samplerThread.start();

            String threadMode = config.uploadThreadMode.name();
            RunSummary summary = null;
            try (BoxClient box = new BoxClient(config)) {
                progress.phase("Authenticating (CCG)");
                box.authenticate(db, config.runId, threadMode);

                progress.phase("Creating run folder on Box");
                String runFolderId = box.createRunFolder(db, config.runId, threadMode);
                db.updateRunFolder(config.runId, runFolderId);

                progress.phase("Resolving upload zone (preflight)");
                var zone = box.resolveUploadZone(db, config.runId, threadMode, runFolderId, payloadBytes);
                db.updateRunUploadZone(config.runId, zone.uploadZoneHost(), zone.uploadBaseUrl(),
                        zone.preflightUploadUrl());
                progress.phase("Upload zone: " + zone.uploadZoneHost());

                UploadRateLimiter rateLimiter = null;
                if (config.shouldEnforceRateLimit()) {
                    double limit = config.effectiveUploadRateLimitPerSecond();
                    rateLimiter = new UploadRateLimiter(limit);
                    progress.phase(String.format(Locale.US,
                            "Rate limit enforcement enabled (%.3f uploads/s)", limit));
                }

                sampler.startUploadPhase();
                progress.startUploadPhase();
                long runStart = System.currentTimeMillis();
                AtomicInteger succeeded = new AtomicInteger();
                AtomicInteger failed = new AtomicInteger();
                AtomicInteger count429 = new AtomicInteger();
                AtomicLong totalBytes = new AtomicLong();
                Semaphore concurrency = new Semaphore(config.uploadConcurrency);
                boolean chunked = config.useChunkedUpload();
                final UploadRateLimiter limiter = rateLimiter;

                ExecutorService executor = config.uploadThreadMode == com.boxuploadperf.config.ThreadMode.VIRTUAL
                        ? Executors.newVirtualThreadPerTaskExecutor()
                        : Executors.newFixedThreadPool(config.platformPoolSize());

                try {
                    for (int i = 0; i < config.uploadFileCount; i++) {
                        final int uploadIndex = i;
                        executor.submit(() -> {
                            try {
                                concurrency.acquire();
                                sampler.setInFlight(config.uploadConcurrency - concurrency.availablePermits());
                                if (limiter != null) {
                                    limiter.acquire();
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                            String uploadGuid = UUID.randomUUID().toString();
                            try {
                                boolean had429;
                                synchronized (db) {
                                    db.connection().setAutoCommit(false);
                                    var result = box.uploadFile(db, config.runId, threadMode, uploadIndex,
                                            uploadGuid, payload, runFolderId, chunked);
                                    had429 = result.had429();
                                    db.insertFileUpload(config.runId, uploadGuid, uploadIndex, result.boxFileId(),
                                            chunked ? "CHUNKED" : "SINGLE_STREAM",
                                            chunked ? result.parts() : 0, true,
                                            result.uploadDurationMs(), result.endToEndMs(),
                                            result.ancillaryCalls(), had429);
                                    db.commit();
                                }
                                if (had429) {
                                    count429.incrementAndGet();
                                }
                                succeeded.incrementAndGet();
                                totalBytes.addAndGet(payloadBytes);
                                sampler.addAppBytes(payloadBytes);
                                progress.uploadSucceeded(succeeded, failed, count429);
                            } catch (Exception e) {
                                try {
                                    db.rollback();
                                } catch (Exception ignored) {
                                }
                                failed.incrementAndGet();
                                progress.uploadFailed(uploadIndex, succeeded, failed, count429, e);
                            } finally {
                                concurrency.release();
                            }
                        });
                    }
                    executor.shutdown();
                    executor.awaitTermination(7, TimeUnit.DAYS);
                } finally {
                    progress.uploadPhaseComplete(succeeded, failed, count429);
                    sampler.stop();
                    samplerThread.join(2000);
                }

                long runDuration = System.currentTimeMillis() - runStart;
                db.endRun(config.runId);

                progress.phase("Computing run summary");
                summary = RunSummarizer.compute(db.connection(), config, config.uploadFileCount,
                        succeeded.get(), failed.get(), totalBytes.get(), runDuration);

                if (config.cleanupDeleteBoxRunFolderAfterRun) {
                    progress.phase("Deleting Box run folder");
                    box.deleteFolder(runFolderId);
                }
            }

            if (config.cleanupDeleteLocalPayloadAfterRun) {
                Files.deleteIfExists(payload);
            }

            progress.phase("Writing routing report and HTML charts");
            Path routingJson = UploadRoutingReport.write(config);
            new HtmlChartReport().generate(config);
            printSummary(config, routingJson, summary);
        }
    }

    private static void printSummary(AppConfig config, Path routingJson, RunSummary summary) throws Exception {
        System.out.println();
        System.out.println("Run complete: " + config.runId);
        System.out.println("Results: " + config.runDirectory());
        System.out.println("Charts:  " + config.runDirectory().resolve("charts/index.html"));
        System.out.println("Routing: " + routingJson);
        System.out.println("SQLite:  " + config.sqlitePath());
        if (summary != null) {
            System.out.println();
            System.out.println("Throughput:");
            System.out.println("  " + summary.rateLimitDescription());
            System.out.printf("  CPU avg/max: %.1f%% / %.1f%%%n", summary.cpuProcessAvgPct(), summary.cpuProcessMaxPct());
            System.out.printf("  App upload avg/peak: %.2f / %.2f Mbps%n",
                    summary.appUploadMbpsAvg(), summary.appUploadMbpsPeak());
            if (summary.retryBackoff() != null) {
                System.out.println("  " + summary.retryBackoff().describe());
            }
        }
        try (var conn = java.sql.DriverManager.getConnection("jdbc:sqlite:" + config.sqlitePath().toAbsolutePath())) {
            UploadRoutingReport.printToConsole(UploadRoutingReport.load(conn, config.runId));
        }
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        try (InputStream in = Files.newInputStream(path);
             DigestInputStream din = new DigestInputStream(in, md)) {
            while (din.read(buffer) != -1) {
                // consume
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }
}
