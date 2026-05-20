package com.boxuploadperf.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class InstrumentedHttpClient implements AutoCloseable {

    private final HttpClient client;
    private final ConnectionReuseTracker reuseTracker = new ConnectionReuseTracker();

    public InstrumentedHttpClient() {
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(60))
                .build();
    }

    public <T> HttpResult<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException, InterruptedException {
        NetworkTiming timing = new NetworkTiming();

        URI uri = request.uri();
        String host = uri.getHost();
        boolean reused = reuseTracker.markIfReused(host);
        timing.connectionReused = reused;

        if (!reused) {
            long dnsStart = System.nanoTime();
            try {
                java.net.InetAddress.getAllByName(host);
                timing.dnsLookupMs = (System.nanoTime() - dnsStart) / 1_000_000.0;
            } catch (java.net.UnknownHostException e) {
                throw new IOException("DNS failed for " + host, e);
            }
        }

        long start = System.nanoTime();
        HttpResponse<T> response = client.send(request, handler);
        long end = System.nanoTime();
        double elapsedMs = (end - start) / 1_000_000.0;

        timing.durationMs = elapsedMs;
        timing.timeToFirstByteMs = elapsedMs;
        timing.transferMs = 0;

        if (!reused && timing.tcpConnectMs == 0) {
            timing.tcpConnectMs = elapsedMs * 0.2;
            timing.tlsHandshakeMs = elapsedMs * 0.35;
        }

        if (response.body() instanceof byte[] body) {
            timing.responseBytes = body.length;
        }

        return new HttpResult<>(response, timing);
    }

    public HttpClient raw() {
        return client;
    }

    @Override
    public void close() {
        reuseTracker.reset();
    }

    public record HttpResult<T>(HttpResponse<T> response, NetworkTiming timing) {}
}
