package com.boxuploadperf.metrics;

import java.net.URI;

/** Redacts query strings (tokens) while keeping host + path for routing diagnostics. */
public final class RequestUrlMetrics {

    private RequestUrlMetrics() {
    }

    public static String fromUri(URI uri) {
        if (uri == null) {
            return "";
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null) {
            return uri.toString();
        }
        int port = uri.getPort();
        String authority = port > 0 ? host + ":" + port : host;
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        return scheme + "://" + authority + path;
    }
}
