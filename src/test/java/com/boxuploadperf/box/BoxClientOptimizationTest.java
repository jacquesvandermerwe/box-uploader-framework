package com.boxuploadperf.box;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoxClientOptimizationTest {

    @Test
    void sha1HexWithOffsetAndLenMatchesWholeArrayHash() throws Exception {
        String input = "The quick brown fox jumps over the lazy dog";
        byte[] fullBytes = input.getBytes(StandardCharsets.UTF_8);

        // Test offset = 10, len = 15
        int offset = 10;
        int len = 15;
        byte[] subArray = Arrays.copyOfRange(fullBytes, offset, offset + len);

        String expectedHash = BoxClient.sha1Hex(subArray);
        String optimizedHash = BoxClient.sha1Hex(fullBytes, offset, len);

        assertEquals(expectedHash, optimizedHash);
    }
}
