package com.boxuploadperf.metrics;

public enum ApiPhase {
    AUTH_TOKEN,
    FOLDER_CREATE,
    PREFLIGHT_CHECK,
    UPLOAD_SIMPLE,
    UPLOAD_SESSION_CREATE,
    UPLOAD_PART,
    UPLOAD_COMMIT,
    FOLDER_DELETE,
    REPORT_UPLOAD
}
