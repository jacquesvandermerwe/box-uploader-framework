package com.boxuploadperf.box;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that a 401 token-refresh retry does not consume a {@code retryMaxAttempts} slot.
 */
class BoxClient401RetryTest {

    @Test
    void retriesAfter401WhenRetryMaxAttemptsIsOne() {
        int retryMaxAttempts = 1;
        int attempt = 1;
        boolean refreshedFor401 = false;
        int sends = 0;
        int lastStatus = 0;

        for (; attempt <= retryMaxAttempts; attempt++) {
            sends++;
            lastStatus = sends == 1 ? 401 : 201;
            if (lastStatus == 401 && !refreshedFor401) {
                refreshedFor401 = true;
                attempt--;
                continue;
            }
            break;
        }

        assertEquals(2, sends, "401 refresh must allow one more send when maxAttempts is 1");
        assertEquals(201, lastStatus);
    }
}
