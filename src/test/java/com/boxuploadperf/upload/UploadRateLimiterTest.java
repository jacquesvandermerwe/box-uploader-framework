package com.boxuploadperf.upload;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadRateLimiterTest {

    @Test
    void spacesPermitsOverTime() throws Exception {
        UploadRateLimiter limiter = new UploadRateLimiter(10.0);
        long start = System.nanoTime();
        for (int i = 0; i < 5; i++) {
            limiter.acquire();
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMs >= 350, "expected ~400ms spacing for 5 at 10/s, got " + elapsedMs);
    }
}
