package com.boxuploadperf.http;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstrumentedHttpClientTest {

    @Test
    void coldConnectionMeasuresDnsAndEstimatesTcpTls() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            try (InstrumentedHttpClient client = new InstrumentedHttpClient()) {
                var first = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                NetworkTiming cold = first.timing();
                assertFalse(cold.connectionReused);
                assertTrue(cold.dnsLookupMs >= 0);
                assertEquals(cold.durationMs * InstrumentedHttpClient.COLD_CONNECTION_TCP_FRACTION,
                        cold.tcpConnectMs, 0.001);
                assertEquals(cold.durationMs * InstrumentedHttpClient.COLD_CONNECTION_TLS_FRACTION,
                        cold.tlsHandshakeMs, 0.001);

                var second = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                NetworkTiming warm = second.timing();
                assertTrue(warm.connectionReused);
                assertEquals(0, warm.dnsLookupMs, 0.001);
                assertEquals(0, warm.tcpConnectMs, 0.001);
                assertEquals(0, warm.tlsHandshakeMs, 0.001);
            }
            assertEquals(2, hits.get());
        } finally {
            server.stop(0);
        }
    }
}
