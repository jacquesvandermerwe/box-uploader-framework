package com.boxuploadperf.box;

import java.net.http.HttpRequest;

/** Helpers for rebuilding {@link HttpRequest} with an updated Bearer token. */
final class HttpRequests {

    private HttpRequests() {}

    static HttpRequest withBearerToken(HttpRequest template, String accessToken) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(template.uri())
                .method(template.method(), template.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()));
        template.version().ifPresent(builder::version);
        builder.expectContinue(template.expectContinue());
        template.headers().map().forEach((name, values) -> {
            if (!"Authorization".equalsIgnoreCase(name)) {
                for (String value : values) {
                    builder.header(name, value);
                }
            }
        });
        builder.header("Authorization", "Bearer " + accessToken);
        if (template.timeout().isPresent()) {
            builder.timeout(template.timeout().get());
        }
        return builder.build();
    }
}
