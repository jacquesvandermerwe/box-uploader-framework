package com.boxuploadperf.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryDelayPolicyTest {

    @Test
    void honorsRetryAfterOn429() {
        RetryDelayPolicy.Delay d = RetryDelayPolicy.compute(429, 100, 1, 500);
        assertEquals(100_000L, d.sleepMs());
        assertEquals(RetryDelayPolicy.DelaySource.RETRY_AFTER, d.source());
    }

    @Test
    void exponentialFallbackWhen429WithoutHeader() {
        RetryDelayPolicy.Delay d = RetryDelayPolicy.compute(429, null, 2, 500);
        assertEquals(1000L, d.sleepMs());
        assertEquals(RetryDelayPolicy.DelaySource.EXPONENTIAL_429, d.source());
    }

    @Test
    void exponentialOn5xx() {
        RetryDelayPolicy.Delay d = RetryDelayPolicy.compute(503, null, 3, 500);
        assertEquals(2000L, d.sleepMs());
        assertEquals(RetryDelayPolicy.DelaySource.EXPONENTIAL_5XX, d.source());
    }

    @Test
    void capsRetryAfterAtMax() {
        RetryDelayPolicy.Delay d = RetryDelayPolicy.compute(429, 999_999, 1, 500);
        assertTrue(d.sleepMs() <= RetryDelayPolicy.MAX_SLEEP_MS);
    }
}
