package com.boxuploadperf.config;

public enum ThreadMode {
    VIRTUAL,
    PLATFORM;

    public static ThreadMode parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("upload.threadMode is required (VIRTUAL or PLATFORM)");
        }
        return ThreadMode.valueOf(value.trim().toUpperCase());
    }
}
