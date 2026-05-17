package com.boxuploadperf.metrics;

import com.boxuploadperf.config.AppConfig;
import com.sun.management.OperatingSystemMXBean;
import oshi.SystemInfo;
import oshi.hardware.NetworkIF;

import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class ResourceSampler implements Runnable {

    private final AppConfig config;
    private final MetricsDatabase db;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicLong appBytesCounter = new AtomicLong();
    private final AtomicLong inFlight = new AtomicLong();

    private long uploadPhaseStartMs;
    private NetworkIF networkIf;
    private long lastBytesIn;
    private long lastBytesOut;

    public ResourceSampler(AppConfig config, MetricsDatabase db) {
        this.config = config;
        this.db = db;
    }

    public void startUploadPhase() {
        uploadPhaseStartMs = System.currentTimeMillis();
        try {
            SystemInfo si = new SystemInfo();
            var nets = si.getHardware().getNetworkIFs();
            if (config.metricsNetworkInterfaceName != null && !config.metricsNetworkInterfaceName.isBlank()) {
                networkIf = nets.stream()
                        .filter(n -> config.metricsNetworkInterfaceName.equals(n.getName()))
                        .findFirst()
                        .orElse(nets.getFirst());
            } else {
                networkIf = nets.stream()
                        .filter(n -> !n.getName().toLowerCase().startsWith("lo"))
                        .findFirst()
                        .orElse(nets.getFirst());
            }
            networkIf.updateAttributes();
            lastBytesIn = networkIf.getBytesRecv();
            lastBytesOut = networkIf.getBytesSent();
        } catch (Exception ignored) {
            networkIf = null;
        }
    }

    public void addAppBytes(long bytes) {
        appBytesCounter.addAndGet(bytes);
    }

    public void setInFlight(long count) {
        inFlight.set(count);
    }

    public void stop() {
        running.set(false);
    }

    @Override
    public void run() {
        OperatingSystemMXBean os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        while (running.get()) {
            try {
                Thread.sleep(config.metricsSampleIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (uploadPhaseStartMs == 0) {
                continue;
            }
            double elapsed = System.currentTimeMillis() - uploadPhaseStartMs;
            double cpuProcess = os.getProcessCpuLoad() >= 0 ? os.getProcessCpuLoad() * 100 : 0;
            Double cpuSystem = os.getCpuLoad() >= 0 ? os.getCpuLoad() * 100 : null;

            long nicIn = 0, nicOut = 0;
            double nicRx = 0, nicTx = 0;
            if (networkIf != null) {
                networkIf.updateAttributes();
                long in = networkIf.getBytesRecv();
                long out = networkIf.getBytesSent();
                nicIn = in - lastBytesIn;
                nicOut = out - lastBytesOut;
                lastBytesIn = in;
                lastBytesOut = out;
                double sec = config.metricsSampleIntervalMs / 1000.0;
                nicRx = (nicIn * 8.0) / (sec * 1_000_000.0);
                nicTx = (nicOut * 8.0) / (sec * 1_000_000.0);
            }

            long appDelta = appBytesCounter.getAndSet(0);
            double appMbps = (appDelta * 8.0) / ((config.metricsSampleIntervalMs / 1000.0) * 1_000_000.0);

            try {
                db.insertResourceSample(config.runId, elapsed, cpuProcess, cpuSystem,
                        nicIn, nicOut, nicRx, nicTx, appDelta, appMbps, (int) inFlight.get());
            } catch (Exception ignored) {
            }
        }
    }
}
