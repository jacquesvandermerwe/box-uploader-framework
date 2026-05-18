package com.boxuploadperf.upload;

/**
 * Client-side upload throttle (token spacing). One permit per file upload start.
 */
public final class UploadRateLimiter {

    private final double intervalNanos;
    private long nextPermitNanos;

    public UploadRateLimiter(double permitsPerSecond) {
        if (permitsPerSecond <= 0) {
            throw new IllegalArgumentException("permitsPerSecond must be > 0");
        }
        this.intervalNanos = (long) (1_000_000_000.0 / permitsPerSecond);
        this.nextPermitNanos = System.nanoTime();
    }

    public synchronized void acquire() throws InterruptedException {
        long now = System.nanoTime();
        if (now < nextPermitNanos) {
            long waitNanos = nextPermitNanos - now;
            long waitMs = waitNanos / 1_000_000;
            int waitNs = (int) (waitNanos % 1_000_000);
            Thread.sleep(waitMs, waitNs);
            now = System.nanoTime();
        }
        nextPermitNanos = Math.max(now, nextPermitNanos) + (long) intervalNanos;
    }
}
