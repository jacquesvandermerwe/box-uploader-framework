package com.boxuploadperf.upload;

import com.boxuploadperf.box.BoxClient;
import com.boxuploadperf.config.AppConfig;
import com.boxuploadperf.metrics.MetricsDatabase;
import com.boxuploadperf.metrics.ResourceSampler;
import com.boxuploadperf.metrics.RunSummarizer;
import com.boxuploadperf.metrics.UploadRoutingReport;
import com.boxuploadperf.pdf.PdfPayloadGenerator;
import com.boxuploadperf.charts.HtmlChartReport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class BenchmarkRunner {

    public void execute(AppConfig config, String configSource) throws Exception {
        config.validate();
        Files.createDirectories(config.runDirectory());
        Files.createDirectories(config.workParentDirectory);

        Path payload = config.payloadPath();
        PdfPayloadGenerator pdfGen = new PdfPayloadGenerator();
        long payloadBytes = pdfGen.generate(payload, config.pdfTargetSizeBytes, config.runId);
        String sha256 = sha256(payload);

        try (MetricsDatabase db = new MetricsDatabase(config.sqlitePath())) {
            db.insertRunStart(config, configSource, sha256, payloadBytes);

            ResourceSampler sampler = new ResourceSampler(config, db);
            Thread samplerThread = new Thread(sampler, "resource-sampler");
            samplerThread.setDaemon(true);
            samplerThread.start();

            String threadMode = config.uploadThreadMode.name();
            try (BoxClient box = new BoxClient(config)) {
                box.authenticate(db, config.runId, threadMode);
                String runFolderId = box.createRunFolder(db, config.runId, threadMode);
                db.updateRunFolder(config.runId, runFolderId);
                var zone = box.resolveUploadZone(db, config.runId, threadMode, runFolderId, payloadBytes);
                db.updateRunUploadZone(config.runId, zone.uploadZoneHost(), zone.uploadBaseUrl(),
                        zone.preflightUploadUrl());

                sampler.startUploadPhase();
                long runStart = System.currentTimeMillis();
                AtomicInteger succeeded = new AtomicInteger();
                AtomicInteger failed = new AtomicInteger();
                AtomicLong totalBytes = new AtomicLong();
                Semaphore concurrency = new Semaphore(config.uploadConcurrency);
                boolean chunked = config.useChunkedUpload();

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
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                            String uploadGuid = UUID.randomUUID().toString();
                            try {
                                synchronized (db) {
                                    db.connection().setAutoCommit(false);
                                    var result = box.uploadFile(db, config.runId, threadMode, uploadIndex,
                                            uploadGuid, payload, runFolderId, chunked);
                                    db.insertFileUpload(config.runId, uploadGuid, uploadIndex, result.boxFileId(),
                                            chunked ? "CHUNKED" : "SINGLE_STREAM",
                                            chunked ? result.parts() : 0, true,
                                            result.uploadDurationMs(), result.endToEndMs(),
                                            result.ancillaryCalls(), result.had429());
                                    db.commit();
                                }
                                succeeded.incrementAndGet();
                                totalBytes.addAndGet(payloadBytes);
                                sampler.addAppBytes(payloadBytes);
                            } catch (Exception e) {
                                try {
                                    db.rollback();
                                } catch (Exception ignored) {
                                }
                                failed.incrementAndGet();
                            } finally {
                                concurrency.release();
                            }
                        });
                    }
                    executor.shutdown();
                    executor.awaitTermination(7, TimeUnit.DAYS);
                } finally {
                    sampler.stop();
                    samplerThread.join(2000);
                }

                long runDuration = System.currentTimeMillis() - runStart;
                db.endRun(config.runId);

                RunSummarizer.compute(db.connection(), config.runId, config.uploadFileCount,
                        succeeded.get(), failed.get(), totalBytes.get(), runDuration,
                        0, 0, null, 0, 0, 0, 0, 0, 0);

                if (config.cleanupDeleteBoxRunFolderAfterRun) {
                    box.deleteFolder(runFolderId);
                }
            }

            if (config.cleanupDeleteLocalPayloadAfterRun) {
                Files.deleteIfExists(payload);
            }

            Path routingJson = UploadRoutingReport.write(config);
            new HtmlChartReport().generate(config);
            printSummary(config, routingJson);
        }
    }

    private static void printSummary(AppConfig config, Path routingJson) throws Exception {
        System.out.println();
        System.out.println("Run complete: " + config.runId);
        System.out.println("Results: " + config.runDirectory());
        System.out.println("Charts:  " + config.runDirectory().resolve("charts/index.html"));
        System.out.println("Routing: " + routingJson);
        System.out.println("SQLite:  " + config.sqlitePath());
        try (var conn = java.sql.DriverManager.getConnection("jdbc:sqlite:" + config.sqlitePath().toAbsolutePath())) {
            UploadRoutingReport.printToConsole(UploadRoutingReport.load(conn, config.runId));
        }
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(Files.readAllBytes(path));
        return HexFormat.of().formatHex(md.digest());
    }
}
