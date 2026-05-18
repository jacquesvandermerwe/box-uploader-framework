package com.boxuploadperf.pdf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfPayloadGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesPayloadNearTargetSize() throws Exception {
        Path out = tempDir.resolve("payload.pdf");
        long target = 1_048_576L;
        PdfPayloadGenerator gen = new PdfPayloadGenerator();
        long actual = gen.generate(out, target, "test-run");
        assertTrue(actual >= target * 0.98 && actual <= target * 1.02,
                "size " + actual + " not within 2% of " + target);
        assertTrue(Files.size(out) > 0);
    }

    @Test
    void reusesExistingWhenSizeMatches() throws Exception {
        Path out = tempDir.resolve("reuse.pdf");
        PdfPayloadGenerator gen = new PdfPayloadGenerator();
        long target = 50_000L;
        gen.generate(out, target, "run-1");
        assertTrue(gen.tryReuseExisting(out, target));
    }
}
