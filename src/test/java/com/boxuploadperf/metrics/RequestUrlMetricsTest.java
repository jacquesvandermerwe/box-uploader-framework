package com.boxuploadperf.metrics;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestUrlMetricsTest {

    @Test
    void stripsQueryString() {
        assertEquals(
                "https://upload-las.app.box.com/api/2.0/files/upload_sessions/abc/commit",
                RequestUrlMetrics.fromUri(URI.create(
                        "https://upload-las.app.box.com/api/2.0/files/upload_sessions/abc/commit?sig=xyz")));
    }
}
