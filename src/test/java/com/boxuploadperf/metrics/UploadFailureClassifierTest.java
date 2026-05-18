package com.boxuploadperf.metrics;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystemException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UploadFailureClassifierTest {

    @Test
    void classifies429() {
        assertEquals(UploadFailureReason.HTTP_429,
                UploadFailureClassifier.classify(new IOException("Upload failed: 429"), 429, true));
    }

    @Test
    void classifiesNetwork() {
        assertEquals(UploadFailureReason.NETWORK,
                UploadFailureClassifier.classify(new IOException("Connection reset"), 0, false));
    }

    @Test
    void classifiesInterrupted() {
        assertEquals(UploadFailureReason.INTERRUPTED,
                UploadFailureClassifier.classify(new InterruptedException("sleep"), 0, false));
    }

    @Test
    void classifiesLocalIo() {
        assertEquals(UploadFailureReason.LOCAL_IO,
                UploadFailureClassifier.classify(new FileSystemException("Too many open files"), 0, false));
    }
}
