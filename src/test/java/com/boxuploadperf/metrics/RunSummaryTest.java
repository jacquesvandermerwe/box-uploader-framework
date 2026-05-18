package com.boxuploadperf.metrics;

import com.boxuploadperf.config.AppConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunSummaryTest {

    @Test
    void boxDefaultIsFourPerSecond() {
        assertEquals(4.0, AppConfig.BOX_DEFAULT_UPLOAD_RATE_LIMIT_PER_SECOND, 0.001);
        assertEquals(240.0, AppConfig.BOX_UPLOAD_RATE_LIMIT_PER_MINUTE, 0.001);
    }

    @Test
    void describesProfileRateLimit() {
        RunSummary s = new RunSummary(10, 0, 2.5, 5.0, true, false, 4, 0, 0, null, 0, 0);
        assertTrue(s.rateLimitDescription().contains("configured"));
        assertTrue(s.rateLimitDescription().contains("50.0%"));
    }

    @Test
    void describesBoxDefaultRateLimit() {
        RunSummary s = new RunSummary(10, 0, 1.2, 4.0, false, false, 8, 0, 0, null, 0, 0);
        assertTrue(s.rateLimitDescription().contains("Box upload limit"));
        assertTrue(s.rateLimitDescription().contains("240/min"));
    }

    @Test
    void describesDisabledRateLimit() {
        RunSummary s = new RunSummary(10, 0, 3.0, 0, false, true, 30, 0, 0, null, 0, 0);
        assertTrue(s.rateLimitDescription().contains("no rate limit baseline"));
        assertTrue(s.rateLimitDescription().contains("concurrency=30"));
    }

    @Test
    void resolveEffectiveLimitUsesDefaultWhenUnset() {
        assertEquals(4.0, RunSummary.resolveEffectiveLimit(null, 0), 0.001);
        assertEquals(5.0, RunSummary.resolveEffectiveLimit(5.0, 1), 0.001);
        assertEquals(0, RunSummary.resolveEffectiveLimit(null, -1), 0.001);
    }
}
