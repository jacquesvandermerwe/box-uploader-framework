package com.boxuploadperf.box;

import com.boxuploadperf.metrics.RequestUrlMetrics;

import java.net.URI;

/**
 * Parses Box preflight {@code upload_url} into a reusable API base (host + /api/2.0), not the full session URL.
 */
public final class UploadZoneResolver {

    static final String PREFLIGHT_PROBE_NAME = ".box-perf-preflight-probe.pdf";

    private UploadZoneResolver() {
    }

    public static UploadZoneContext fromPreflightResponse(String uploadUrl, String uploadToken) {
        if (uploadUrl == null || uploadUrl.isBlank()) {
            throw new IllegalArgumentException("Preflight response missing upload_url");
        }
        URI uri = URI.create(uploadUrl.trim());
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Invalid upload_url host: " + uploadUrl);
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        int apiIdx = path.indexOf("/api/");
        String basePath = apiIdx >= 0 ? path.substring(0, apiIdx + "/api/2.0".length()) : "/api/2.0";
        if (!basePath.endsWith("/2.0")) {
            basePath = "/api/2.0";
        }
        String base = uri.getScheme() + "://" + host + basePath;
        String redactedPreflight = RequestUrlMetrics.fromUri(uri);
        return new UploadZoneContext(base, host, blankToNull(uploadToken), redactedPreflight);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
