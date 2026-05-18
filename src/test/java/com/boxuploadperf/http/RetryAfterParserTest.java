package com.boxuploadperf.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryAfterParserTest {

    @Test
    void parsesIntegerSeconds() {
        var opt = RetryAfterParser.parseHeaderValue("100");
        assertTrue(opt.isPresent());
        assertEquals(100, opt.getAsInt());
    }

    @Test
    void rejectsZeroOrNegative() {
        assertFalse(RetryAfterParser.parseHeaderValue("0").isPresent());
        assertFalse(RetryAfterParser.parseHeaderValue("-1").isPresent());
    }

    @Test
    void rejectsGarbage() {
        assertFalse(RetryAfterParser.parseHeaderValue("not-a-number").isPresent());
    }
}
