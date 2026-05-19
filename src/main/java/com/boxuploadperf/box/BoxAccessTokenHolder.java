package com.boxuploadperf.box;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thread-safe Box CCG access token with proactive refresh before expiry.
 * Box tokens are typically valid for 3600 seconds; refresh is scheduled at 90% of lifetime.
 */
final class BoxAccessTokenHolder {

    /** Refresh when this fraction of the token lifetime has elapsed (0.9 = 10% remaining). */
    static final double REFRESH_AT_LIFETIME_FRACTION = 0.90;

    /** Used when {@code expires_in} is missing from the token response. */
    static final long DEFAULT_EXPIRES_IN_SECONDS = 3600L;

    /** Minimum seconds before expiry to refresh (avoids last-second races). */
    static final long MIN_REFRESH_BEFORE_EXPIRY_SECONDS = 120L;

    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern EXPIRES_IN = Pattern.compile("\"expires_in\"\\s*:\\s*(\\d+)");

    private final Object lock = new Object();
    private String accessToken;
    private Instant issuedAt;
    private long expiresInSeconds;
    private Instant expiresAt;

    void applyTokenResponse(String jsonBody) {
        String token = extractRequired(ACCESS_TOKEN, jsonBody, "access_token");
        long expiresIn = extractExpiresIn(jsonBody);
        Instant now = Instant.now();
        synchronized (lock) {
            this.accessToken = token;
            this.expiresInSeconds = expiresIn;
            this.issuedAt = now;
            this.expiresAt = now.plusSeconds(expiresIn);
        }
    }

    String accessToken() {
        synchronized (lock) {
            return accessToken;
        }
    }

    boolean hasToken() {
        synchronized (lock) {
            return accessToken != null && !accessToken.isBlank();
        }
    }

    /**
     * True when the token should be replaced before the next API call.
     */
    boolean needsProactiveRefresh() {
        synchronized (lock) {
            if (accessToken == null) {
                return true;
            }
            Instant now = Instant.now();
            if (!now.isBefore(expiresAt)) {
                return true;
            }
            long refreshAfterSeconds = (long) (expiresInSeconds * REFRESH_AT_LIFETIME_FRACTION);
            Instant proactiveRefreshAt = issuedAt.plusSeconds(refreshAfterSeconds);
            Instant latestSafeRefresh = expiresAt.minusSeconds(MIN_REFRESH_BEFORE_EXPIRY_SECONDS);
            if (proactiveRefreshAt.isAfter(latestSafeRefresh)) {
                proactiveRefreshAt = latestSafeRefresh;
            }
            return !now.isBefore(proactiveRefreshAt);
        }
    }

    Instant expiresAt() {
        synchronized (lock) {
            return expiresAt;
        }
    }

    private static long extractExpiresIn(String jsonBody) {
        Matcher m = EXPIRES_IN.matcher(jsonBody);
        if (m.find()) {
            return Long.parseLong(m.group(1));
        }
        return DEFAULT_EXPIRES_IN_SECONDS;
    }

    private static String extractRequired(Pattern pattern, String json, String fieldName) {
        Matcher m = pattern.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        throw new IllegalStateException("Missing field: " + fieldName);
    }
}
