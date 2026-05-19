package com.boxuploadperf.box;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Parses comma-separated Box user IDs for {@code As-User} impersonation. */
public final class ImpersonationUsers {

    static final int MAX_USERS = 100;

    private ImpersonationUsers() {}

    public static List<String> parse(String boxUserId) {
        if (boxUserId == null || boxUserId.isBlank()) {
            return List.of();
        }
        String[] parts = boxUserId.split(",");
        List<String> ids = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String part : parts) {
            String id = part.trim();
            if (id.isEmpty()) {
                continue;
            }
            if (!id.matches("\\d+")) {
                throw new IllegalArgumentException(
                        "box.userId entries must be numeric Box user IDs (invalid: \"" + id + "\")");
            }
            if (seen.add(id)) {
                ids.add(id);
            }
        }
        if (ids.size() > MAX_USERS) {
            throw new IllegalArgumentException(
                    "box.userId supports at most " + MAX_USERS + " user IDs (got " + ids.size() + ")");
        }
        return List.copyOf(ids);
    }
}
