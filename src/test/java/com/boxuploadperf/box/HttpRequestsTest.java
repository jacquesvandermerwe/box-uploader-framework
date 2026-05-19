package com.boxuploadperf.box;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpRequestsTest {

    @Test
    void appliesBearerTokenAndReplacesExistingAuthorization() {
        HttpRequest template = HttpRequest.newBuilder(URI.create("https://api.box.com/2.0/files"))
                .header("Authorization", "Bearer stale")
                .header("Content-Type", "application/json")
                .GET()
                .build();

        HttpRequest updated = HttpRequests.withBearerToken(template, "fresh");

        assertEquals("Bearer fresh", updated.headers().firstValue("Authorization").orElseThrow());
        assertEquals("application/json", updated.headers().firstValue("Content-Type").orElseThrow());
        assertEquals(template.method(), updated.method());
        assertEquals(template.uri(), updated.uri());
    }

    @Test
    void preservesHttpVersionAndExpectContinue() {
        HttpRequest template = HttpRequest.newBuilder(URI.create("https://api.box.com/2.0/files"))
                .version(HttpClient.Version.HTTP_1_1)
                .expectContinue(true)
                .GET()
                .build();

        HttpRequest updated = HttpRequests.withBearerToken(template, "fresh");

        assertTrue(updated.version().isPresent());
        assertEquals(HttpClient.Version.HTTP_1_1, updated.version().get());
        assertTrue(updated.expectContinue());
    }
}
