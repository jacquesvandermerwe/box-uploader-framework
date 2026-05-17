package com.boxuploadperf.box;

/**
 * Regional upload host resolved once per run (SDK: preflight {@code UploadUrl}, cached as base only).
 */
public record UploadZoneContext(
        String uploadBaseUrl,
        String uploadZoneHost,
        String uploadToken,
        String preflightUploadUrl) {

    public static final String DEFAULT_UPLOAD_BASE = "https://upload.box.com/api/2.0";

    public static UploadZoneContext globalDefault() {
        return new UploadZoneContext(DEFAULT_UPLOAD_BASE, "upload.box.com", null, null);
    }
}
