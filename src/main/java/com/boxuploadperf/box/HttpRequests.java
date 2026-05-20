package com.boxuploadperf.box;

import java.net.http.HttpRequest;

/** Helpers for rebuilding {@link HttpRequest} with Bearer token and optional {@code As-User} header. */
final class HttpRequests {

    private HttpRequests() {}

    static HttpRequest withAuth(HttpRequest template, String accessToken, String asUserId) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(template.uri())
                .method(template.method(), template.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()));
        template.version().ifPresent(builder::version);
        builder.expectContinue(template.expectContinue());
        template.headers().map().forEach((name, values) -> {
            if (shouldCopyHeader(name)) {
                for (String value : values) {
                    builder.header(name, value);
                }
            }
        });
        builder.header("Authorization", "Bearer " + accessToken);
        if (asUserId != null && !asUserId.isBlank()) {
            builder.header("As-User", asUserId);
        }
        if (template.timeout().isPresent()) {
            builder.timeout(template.timeout().get());
        }
        return builder.build();
    }

    private static boolean shouldCopyHeader(String name) {
        return !"Authorization".equalsIgnoreCase(name) && !"As-User".equalsIgnoreCase(name);
    }
}
