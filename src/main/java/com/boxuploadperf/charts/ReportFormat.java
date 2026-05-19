package com.boxuploadperf.charts;

/** Shared formatting for HTML/PDF run reports. */
final class ReportFormat {

    private ReportFormat() {}

    static String formatImpersonationUsers(String userIdConfig) {
        if (userIdConfig == null || userIdConfig.isBlank()) {
            return "";
        }
        if (!userIdConfig.contains(",")) {
            return userIdConfig.trim();
        }
        return userIdConfig.trim() + " (round-robin per upload, As-User header)";
    }
}
