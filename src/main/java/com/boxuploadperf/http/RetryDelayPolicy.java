package com.boxuploadperf.http;

/**
 * Retry wait duration: honor {@code Retry-After} on 429; exponential fallback otherwise
 * (<a href="https://developer.box.com/guides/api-calls/permissions-and-errors/rate-limits">Box rate limits</a>).
 */
public final class RetryDelayPolicy {

    public static final long MAX_SLEEP_MS = 300_000L;

    public enum DelaySource {
        /** Wait derived from {@code Retry-After} header (seconds). */
        RETRY_AFTER,
        /** 429 without header — exponential backoff from configured base. */
        EXPONENTIAL_429,
        /** 5xx — exponential backoff from configured base. */
        EXPONENTIAL_5XX
    }

    public record Delay(long sleepMs, DelaySource source) {}

    private RetryDelayPolicy() {}

    /**
     * @param retryAfterSeconds parsed {@code Retry-After} (seconds), if present
     * @param attempt           1-based attempt that received the error
     * @param fallbackBackoffMs profile {@code retry.backoffMs} — used only when header absent (429) or on 5xx
     */
    public static Delay compute(int status, Integer retryAfterSeconds, int attempt, long fallbackBackoffMs) {
        if (status == 429) {
            if (retryAfterSeconds != null && retryAfterSeconds > 0) {
                long ms = Math.min(retryAfterSeconds * 1000L, MAX_SLEEP_MS);
                return new Delay(ms, DelaySource.RETRY_AFTER);
            }
            return new Delay(exponentialMs(attempt, fallbackBackoffMs), DelaySource.EXPONENTIAL_429);
        }
        if (status >= 500) {
            return new Delay(exponentialMs(attempt, fallbackBackoffMs), DelaySource.EXPONENTIAL_5XX);
        }
        return new Delay(0, DelaySource.EXPONENTIAL_5XX);
    }

    private static long exponentialMs(int attempt, long baseMs) {
        int exp = Math.min(Math.max(attempt - 1, 0), 10);
        long scaled = baseMs * (1L << exp);
        return Math.min(Math.max(scaled, baseMs), MAX_SLEEP_MS);
    }
}
