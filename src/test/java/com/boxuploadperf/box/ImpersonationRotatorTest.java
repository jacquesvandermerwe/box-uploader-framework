package com.boxuploadperf.box;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ImpersonationRotatorTest {

    @Test
    void singleUserAlwaysSame() {
        ImpersonationRotator r = ImpersonationRotator.from(List.of("100"));
        assertEquals("100", r.nextForUpload());
        assertEquals("100", r.nextForUpload());
        assertEquals("100", r.setupUserId());
    }

    @Test
    void roundRobinsAcrossUploads() {
        ImpersonationRotator r = ImpersonationRotator.from(List.of("1", "2", "3"));
        assertEquals("1", r.nextForUpload());
        assertEquals("2", r.nextForUpload());
        assertEquals("3", r.nextForUpload());
        assertEquals("1", r.nextForUpload());
    }

    @Test
    void disabledWhenEmpty() {
        ImpersonationRotator r = ImpersonationRotator.from(List.of());
        assertNull(r.nextForUpload());
        assertNull(r.setupUserId());
    }
}
