package com.boxuploadperf.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetworkTimingTest {

    @Test
    void totalNetworkMsUsesDurationWithoutDoubleCountingTransfer() {
        NetworkTiming t = new NetworkTiming();
        t.durationMs = 100;
        t.timeToFirstByteMs = 100;
        t.transferMs = 0;
        t.dnsLookupMs = 5;
        t.tcpConnectMs = 20;
        t.tlsHandshakeMs = 35;
        assertEquals(105, t.totalNetworkMs(), 0.001);
    }

    @Test
    void totalNetworkMsFallsBackToPhaseSumWhenDurationUnset() {
        NetworkTiming t = new NetworkTiming();
        t.dnsLookupMs = 1;
        t.tcpConnectMs = 2;
        t.tlsHandshakeMs = 3;
        t.timeToFirstByteMs = 4;
        t.transferMs = 5;
        assertEquals(15, t.totalNetworkMs(), 0.001);
    }
}
