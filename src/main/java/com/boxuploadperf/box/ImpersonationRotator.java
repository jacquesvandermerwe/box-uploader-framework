package com.boxuploadperf.box;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Round-robin selector for {@code As-User} impersonation.
 * A single user ID is returned for every call (no rotation).
 */
final class ImpersonationRotator {

    private final List<String> userIds;
    private final AtomicInteger uploadIndex = new AtomicInteger();

    ImpersonationRotator(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            this.userIds = List.of();
        } else {
            this.userIds = List.copyOf(userIds);
        }
    }

    static ImpersonationRotator from(List<String> userIds) {
        return new ImpersonationRotator(userIds);
    }

    /** First configured user — used for setup calls (folder create, preflight, cleanup). */
    String setupUserId() {
        if (userIds.isEmpty()) {
            return null;
        }
        return userIds.get(0);
    }

    /** Next user for a benchmark file upload (round-robin when multiple IDs are configured). */
    String nextForUpload() {
        if (userIds.isEmpty()) {
            return null;
        }
        if (userIds.size() == 1) {
            return userIds.get(0);
        }
        int i = uploadIndex.getAndIncrement();
        return userIds.get(Math.floorMod(i, userIds.size()));
    }
}
