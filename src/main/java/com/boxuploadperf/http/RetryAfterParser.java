package com.boxuploadperf.http;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.OptionalInt;

/**
 * Parses Box {@code Retry-After} on 429 responses (seconds per
 * <a href="https://developer.box.com/guides/api-calls/permissions-and-errors/rate-limits">Box rate limits</a>).
 */
public final class RetryAfterParser {

    private static final DateTimeFormatter RFC_1123 = DateTimeFormatter.RFC_1123_DATE_TIME;

    private RetryAfterParser() {}

    /** Seconds to wait before retry; empty if header absent or unparseable. */
    public static OptionalInt parseSeconds(HttpResponse<?> response) {
        return response.headers().firstValue("retry-after")
                .map(RetryAfterParser::parseHeaderValue)
                .orElse(OptionalInt.empty());
    }

    static OptionalInt parseHeaderValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return OptionalInt.empty();
        }
        String trimmed = raw.trim();
        try {
            int seconds = Integer.parseInt(trimmed);
            return seconds > 0 ? OptionalInt.of(seconds) : OptionalInt.empty();
        } catch (NumberFormatException ignored) {
            return parseHttpDate(trimmed);
        }
    }

    private static OptionalInt parseHttpDate(String value) {
        try {
            ZonedDateTime when = ZonedDateTime.parse(value, RFC_1123.withLocale(Locale.US));
            long sec = Duration.between(Instant.now(), when.toInstant()).toSeconds();
            if (sec < 1) {
                sec = 1;
            }
            return OptionalInt.of((int) Math.min(sec, Integer.MAX_VALUE));
        } catch (DateTimeParseException e) {
            return OptionalInt.empty();
        }
    }
}
