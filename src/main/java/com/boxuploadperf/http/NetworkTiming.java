package com.boxuploadperf.http;

public final class NetworkTiming {

    public double dnsLookupMs;
    public double tcpConnectMs;
    public double tlsHandshakeMs;
    public double timeToFirstByteMs;
    public double transferMs;
    public double durationMs;
    public boolean connectionReused;
    public int requestBytes;
    public int responseBytes;

    /**
     * End-to-end network time for this call. Uses {@link #durationMs} when set so phases are not
     * double-counted (e.g. {@code timeToFirstByteMs} and {@code transferMs} both equalling wall time).
     */
    public double totalNetworkMs() {
        if (durationMs > 0) {
            return durationMs + dnsLookupMs;
        }
        return dnsLookupMs + tcpConnectMs + tlsHandshakeMs + timeToFirstByteMs + transferMs;
    }

    /** Wall time for upload throughput (Mbps); prefers {@link #transferMs} when set. */
    public double throughputMs() {
        return transferMs > 0 ? transferMs : durationMs;
    }
}
