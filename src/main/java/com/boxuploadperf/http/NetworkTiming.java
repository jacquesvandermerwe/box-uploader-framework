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

    public double totalNetworkMs() {
        return dnsLookupMs + tcpConnectMs + tlsHandshakeMs + timeToFirstByteMs + transferMs;
    }
}
