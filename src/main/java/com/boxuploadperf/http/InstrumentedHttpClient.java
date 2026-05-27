package com.boxuploadperf.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Wraps {@link HttpClient#send} with per-call timing stored in {@link NetworkTiming}.
 *
 * <p><b>v1 measurement model</b> (see docs/PRD.md §4.3):
 * <ul>
 *   <li>{@code duration_ms} — wall time around {@code send()}.</li>
 *   <li>{@code dns_lookup_ms} — timed {@link java.net.InetAddress#getAllByName} on the first
 *       request to each host in this client instance; {@code 0} when the connection is reused.</li>
 *   <li>{@code tcp_connect_ms} / {@code tls_handshake_ms} — <em>estimated</em> as fractions of
 *       {@code duration_ms} on cold connections only; {@code 0} when reused. The JDK client does
 *       not expose true connect/handshake hooks without a custom socket stack (deferred).</li>
 *   <li>{@code time_to_first_byte_ms} — approximated as {@code duration_ms} until response headers
 *       are available (no body subscriber split in v1).</li>
 * </ul>
 */
public final class InstrumentedHttpClient implements AutoCloseable {

    /** Estimated TCP share of cold-connection RTT when connect timing is unavailable. */
    static final double COLD_CONNECTION_TCP_FRACTION = 0.2;
    /** Estimated TLS share of cold-connection RTT when handshake timing is unavailable. */
    static final double COLD_CONNECTION_TLS_FRACTION = 0.35;

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
            timing.tcpConnectMs = elapsedMs * COLD_CONNECTION_TCP_FRACTION;
            timing.tlsHandshakeMs = elapsedMs * COLD_CONNECTION_TLS_FRACTION;
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
